package com.faruque.player.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.faruque.player.R
import com.faruque.player.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Optional analytics helper matching the original MyPlayer Stats contract.
 * Safely no-ops when Firebase Analytics is not configured.
 */
object Stats {
    fun log(context: Context, event: String) {
        try {
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstance.invoke(null, context.applicationContext)
            val logEvent = clazz.getMethod("logEvent", String::class.java, android.os.Bundle::class.java)
            logEvent.invoke(instance, event, null)
        } catch (_: Throwable) {
            // Firebase Analytics not bundled or google-services.json not present.
        }
    }
}

class VideoRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun loadInitialVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, VideoItem>()

        // 1. Load any persisted user-selected video URIs (SAF / Photo Picker persistable grants).
        val savedUris = prefs.getStringSet(KEY_PERSISTED_URIS, emptySet()).orEmpty()
        val validUris = mutableSetOf<String>()
        for (uriStr in savedUris) {
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
            val item = resolveVideoMetadata(uri)
            if (item != null) {
                result[item.id] = item
                validUris.add(uriStr)
            }
        }
        if (validUris.size != savedUris.size) {
            prefs.edit().putStringSet(KEY_PERSISTED_URIS, validUris).apply()
        }

        // 2. Query app-accessible MediaStore videos without requiring broad storage permissions.
        for (item in queryAccessibleMediaStoreVideos()) {
            result.putIfAbsent(item.id, item)
        }

        result.values.toList()
    }

    suspend fun importPickedVideos(uris: List<Uri>): List<VideoItem> = withContext(Dispatchers.IO) {
        val currentSaved = prefs.getStringSet(KEY_PERSISTED_URIS, emptySet()).orEmpty().toMutableSet()
        val imported = mutableListOf<VideoItem>()

        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                currentSaved.add(uri.toString())
            } catch (_: SecurityException) {
                // Photo Picker URIs on older Android versions may only grant temporary session access.
            }
            resolveVideoMetadata(uri)?.let { imported.add(it) }
        }

        prefs.edit().putStringSet(KEY_PERSISTED_URIS, currentSaved).apply()
        imported
    }

    fun removePersistedUri(uri: Uri) {
        val updated = prefs.getStringSet(KEY_PERSISTED_URIS, emptySet()).orEmpty().toMutableSet()
        if (updated.remove(uri.toString())) {
            prefs.edit().putStringSet(KEY_PERSISTED_URIS, updated).apply()
        }
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
    }

    fun clearPersistedUris() {
        val current = prefs.getStringSet(KEY_PERSISTED_URIS, emptySet()).orEmpty()
        for (uriStr in current) {
            runCatching { Uri.parse(uriStr) }.getOrNull()?.let { uri ->
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
        }
        prefs.edit().remove(KEY_PERSISTED_URIS).apply()
    }

    private fun resolveVideoMetadata(uri: Uri): VideoItem? {
        val resolver = context.contentResolver
        var displayName: String? = null

        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Throwable) {
            return null
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            // Keep 0ms if metadata extraction is unavailable for this provider.
        } finally {
            runCatching { retriever.release() }
        }

        val fallbackTitle = uri.lastPathSegment?.substringAfterLast('/')
            ?: context.getString(R.string.default_video_title)

        return VideoItem(
            id = uri.toString(),
            title = displayName?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            uri = uri,
            durationMs = durationMs
        )
    }

    private fun queryAccessibleMediaStoreVideos(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    list.add(
                        VideoItem(
                            id = uri.toString(),
                            title = cursor.getString(nameCol)
                                ?: context.getString(R.string.default_video_title),
                            uri = uri,
                            durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Safely ignore if scoped storage blocks external queries without permissions.
        }
        return list
    }

    companion object {
        private const val PREFS_NAME = "my_player_library"
        private const val KEY_PERSISTED_URIS = "persisted_video_uris"
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
