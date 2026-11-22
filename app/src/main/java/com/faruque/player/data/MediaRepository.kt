package com.faruque.player.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.faruque.player.R
import com.faruque.player.model.MediaFolder
import com.faruque.player.model.MediaItemModel
import com.faruque.player.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object Stats {
    fun log(context: Context, event: String) {
        try {
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstance.invoke(null, context.applicationContext)
            val logEvent = clazz.getMethod("logEvent", String::class.java, android.os.Bundle::class.java)
            logEvent.invoke(instance, event, null)
        } catch (_: Throwable) {
            // Optional analytics hook when Firebase is configured.
        }
    }
}

class MediaRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val progressPrefs by lazy {
        context.getSharedPreferences(PROGRESS_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val durationCachePrefs by lazy {
        context.getSharedPreferences(DURATION_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    suspend fun loadLibrary(): Pair<List<MediaFolder>, List<MediaItemModel>> = withContext(Dispatchers.IO) {
        val itemMap = LinkedHashMap<String, MediaItemModel>()
        val treeUriByFolder = mutableMapOf<String, Uri>()

        // 1. Scan persisted folder trees added via OpenDocumentTree (SAF).
        val savedTrees = prefs.getStringSet(KEY_PERSISTED_TREES, emptySet()).orEmpty()
        val validTrees = mutableSetOf<String>()
        for (treeStr in savedTrees) {
            val treeUri = runCatching { Uri.parse(treeStr) }.getOrNull() ?: continue
            val scanned = scanDocumentTree(treeUri)
            if (scanned != null) {
                validTrees.add(treeStr)
                for (item in scanned) {
                    itemMap[item.id] = item
                    treeUriByFolder.putIfAbsent(item.folderName, treeUri)
                }
            }
        }
        if (validTrees.size != savedTrees.size) {
            prefs.edit().putStringSet(KEY_PERSISTED_TREES, validTrees).apply()
        }

        // 2. Load individually persisted document/file URIs.
        val savedFiles = prefs.getStringSet(KEY_PERSISTED_FILES, emptySet()).orEmpty()
        val validFiles = mutableSetOf<String>()
        for (uriStr in savedFiles) {
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
            val item = resolveSingleUri(uri)
            if (item != null) {
                validFiles.add(uriStr)
                itemMap.putIfAbsent(item.id, item)
            }
        }
        if (validFiles.size != savedFiles.size) {
            prefs.edit().putStringSet(KEY_PERSISTED_FILES, validFiles).apply()
        }

        // 3. Query app-accessible MediaStore Video & Audio entries without broad permissions.
        for (item in queryAccessibleMediaStore(MediaType.VIDEO)) {
            itemMap.putIfAbsent(item.id, item)
        }
        for (item in queryAccessibleMediaStore(MediaType.AUDIO)) {
            itemMap.putIfAbsent(item.id, item)
        }

        val allItems = itemMap.values.map { item ->
            item.copy(lastPositionMs = getSavedPosition(item.id))
        }
        val folders = groupIntoFolders(allItems, treeUriByFolder)
        folders to allItems
    }

    suspend fun importFolderTree(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        val current = prefs.getStringSet(KEY_PERSISTED_TREES, emptySet()).orEmpty().toMutableSet()
        val added = current.add(treeUri.toString())
        prefs.edit().putStringSet(KEY_PERSISTED_TREES, current).apply()
        added || true
    }

    suspend fun importFiles(uris: List<Uri>): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val current = prefs.getStringSet(KEY_PERSISTED_FILES, emptySet()).orEmpty().toMutableSet()
        val imported = mutableListOf<MediaItemModel>()
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                current.add(uri.toString())
            } catch (_: SecurityException) {
            }
            resolveSingleUri(uri)?.let { imported.add(it) }
        }
        prefs.edit().putStringSet(KEY_PERSISTED_FILES, current).apply()
        imported
    }

    fun removeItem(item: MediaItemModel) {
        val currentFiles = prefs.getStringSet(KEY_PERSISTED_FILES, emptySet()).orEmpty().toMutableSet()
        if (currentFiles.remove(item.uri.toString())) {
            prefs.edit().putStringSet(KEY_PERSISTED_FILES, currentFiles).apply()
        }
        progressPrefs.edit().remove(item.id).apply()
    }

    fun removeFolder(folder: MediaFolder) {
        folder.treeUri?.let { treeUri ->
            val currentTrees = prefs.getStringSet(KEY_PERSISTED_TREES, emptySet()).orEmpty().toMutableSet()
            if (currentTrees.remove(treeUri.toString())) {
                prefs.edit().putStringSet(KEY_PERSISTED_TREES, currentTrees).apply()
            }
            try {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
        val currentFiles = prefs.getStringSet(KEY_PERSISTED_FILES, emptySet()).orEmpty().toMutableSet()
        var changed = false
        for (item in folder.items) {
            if (currentFiles.remove(item.uri.toString())) {
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putStringSet(KEY_PERSISTED_FILES, currentFiles).apply()
        }
    }

    fun clearAll() {
        val trees = prefs.getStringSet(KEY_PERSISTED_TREES, emptySet()).orEmpty()
        for (uriStr in trees) {
            runCatching { Uri.parse(uriStr) }.getOrNull()?.let { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
        val files = prefs.getStringSet(KEY_PERSISTED_FILES, emptySet()).orEmpty()
        for (uriStr in files) {
            runCatching { Uri.parse(uriStr) }.getOrNull()?.let { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
        prefs.edit().clear().apply()
        progressPrefs.edit().clear().apply()
    }

    fun savePlaybackPosition(id: String, positionMs: Long, durationMs: Long) {
        val normalized = if (durationMs > 0L && positionMs >= durationMs - 2500L) {
            0L
        } else {
            positionMs.coerceAtLeast(0L)
        }
        progressPrefs.edit().putLong(id, normalized).apply()
    }

    fun getSavedPosition(id: String): Long = progressPrefs.getLong(id, 0L)

    fun resolveSingleUri(uri: Uri): MediaItemModel? {
        val resolver = context.contentResolver
        var displayName: String? = null
        var sizeBytes = 0L

        try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Throwable) {
            return null
        }

        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val title = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: context.getString(R.string.default_video_title)

        val mediaType = classifyMediaType(mimeType, title) ?: MediaType.VIDEO
        val durationMs = extractDurationCached(uri)
        val folderName = extractFolderFromUri(uri) ?: context.getString(R.string.unknown_folder)

        return MediaItemModel(
            id = uri.toString(),
            title = title,
            uri = uri,
            durationMs = durationMs,
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            folderName = folderName,
            mediaType = mediaType,
            mimeType = mimeType,
            lastPositionMs = getSavedPosition(uri.toString())
        )
    }

    private fun scanDocumentTree(treeUri: Uri): List<MediaItemModel>? {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        var rootFolderName = context.getString(R.string.unknown_folder)

        try {
            context.contentResolver.query(
                rootDocUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                            rootFolderName = it
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return null
        }

        val collected = mutableListOf<MediaItemModel>()
        traverseDirectory(
            treeUri = treeUri,
            parentDocId = rootDocId,
            folderName = rootFolderName,
            depth = 0,
            out = collected
        )
        return collected
    }

    private fun traverseDirectory(
        treeUri: Uri,
        parentDocId: String,
        folderName: String,
        depth: Int,
        out: MutableList<MediaItemModel>
    ) {
        if (depth > MAX_SCAN_DEPTH || out.size >= MAX_SCAN_ITEMS) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext() && out.size < MAX_SCAN_ITEMS) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = (if (nameCol >= 0) cursor.getString(nameCol) else null) ?: continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) {
                            traverseDirectory(
                                treeUri = treeUri,
                                parentDocId = docId,
                                folderName = name,
                                depth = depth + 1,
                                out = out
                            )
                        }
                    } else {
                        val mediaType = classifyMediaType(mime, name) ?: continue
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val duration = extractDurationCached(docUri)
                        out.add(
                            MediaItemModel(
                                id = docUri.toString(),
                                title = name,
                                uri = docUri,
                                durationMs = duration,
                                sizeBytes = size.coerceAtLeast(0L),
                                folderName = folderName,
                                mediaType = mediaType,
                                mimeType = mime,
                                lastPositionMs = getSavedPosition(docUri.toString())
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Skip unreadable subdirectories gracefully.
        }
    }

    private fun queryAccessibleMediaStore(type: MediaType): List<MediaItemModel> {
        val list = mutableListOf<MediaItemModel>()
        val collection = if (type == MediaType.VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val bucketColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        } else {
            MediaStore.MediaColumns.DISPLAY_NAME
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            bucketColumn
        )

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val bucketCol = cursor.getColumnIndex(bucketColumn)

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val title = if (nameCol >= 0) {
                        cursor.getString(nameCol) ?: context.getString(R.string.default_video_title)
                    } else {
                        context.getString(R.string.default_video_title)
                    }
                    val folder = if (bucketCol >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(bucketCol)?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.unknown_folder)
                    } else {
                        context.getString(R.string.unknown_folder)
                    }
                    list.add(
                        MediaItemModel(
                            id = uri.toString(),
                            title = title,
                            uri = uri,
                            durationMs = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L,
                            sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                            folderName = folder,
                            mediaType = type,
                            mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null,
                            lastPositionMs = getSavedPosition(uri.toString())
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Safely ignore if scoped storage blocks external queries without permissions.
        }
        return list
    }

    private fun extractDurationCached(uri: Uri): Long {
        val key = uri.toString()
        if (durationCachePrefs.contains(key)) {
            return durationCachePrefs.getLong(key, 0L)
        }
        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val str = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = str?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            durationCachePrefs.edit().putLong(key, durationMs).apply()
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        return durationMs
    }

    private fun groupIntoFolders(
        items: List<MediaItemModel>,
        treeUriByFolder: Map<String, Uri>
    ): List<MediaFolder> {
        return items
            .groupBy { it.folderName }
            .map { (name, folderItems) ->
                MediaFolder(
                    name = name,
                    treeUri = treeUriByFolder[name],
                    items = folderItems.sortedBy { it.title.lowercase(Locale.getDefault()) }
                )
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun extractFolderFromUri(uri: Uri): String? {
        val segment = uri.lastPathSegment ?: return null
        val cleaned = segment.substringAfter(':', segment)
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        return if (parts.size >= 2) parts[parts.size - 2] else null
    }

    private fun classifyMediaType(mimeType: String?, fileName: String): MediaType? {
        if (mimeType != null) {
            if (mimeType.startsWith("video/")) return MediaType.VIDEO
            if (mimeType.startsWith("audio/")) return MediaType.AUDIO
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v", "ts" -> MediaType.VIDEO
            "mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "wma", "amr" -> MediaType.AUDIO
            else -> null
        }
    }

    companion object {
        private const val PREFS_NAME = "my_player_sources"
        private const val PROGRESS_PREFS_NAME = "my_player_progress"
        private const val DURATION_CACHE_PREFS = "my_player_durations"
        private const val KEY_PERSISTED_TREES = "persisted_tree_uris"
        private const val KEY_PERSISTED_FILES = "persisted_file_uris"
        private const val MAX_SCAN_DEPTH = 4
        private const val MAX_SCAN_ITEMS = 300
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
