package com.faruque.player.model

import android.net.Uri

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class MediaFilter {
    ALL,
    VIDEO,
    AUDIO
}

data class MediaItemModel(
    val id: String,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderName: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val lastPositionMs: Long = 0L
)

data class MediaFolder(
    val name: String,
    val treeUri: Uri?,
    val items: List<MediaItemModel>
) {
    val videoCount: Int get() = items.count { it.mediaType == MediaType.VIDEO }
    val audioCount: Int get() = items.count { it.mediaType == MediaType.AUDIO }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Content(
        val folders: List<MediaFolder>,
        val allItems: List<MediaItemModel>
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}
