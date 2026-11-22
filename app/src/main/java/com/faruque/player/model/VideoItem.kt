package com.faruque.player.model

import android.net.Uri

data class VideoItem(
    val id: String,
    val title: String,
    val uri: Uri,
    val durationMs: Long
)

sealed interface VideoLibraryUiState {
    data object Loading : VideoLibraryUiState
    data object Empty : VideoLibraryUiState
    data class Content(val videos: List<VideoItem>) : VideoLibraryUiState
    data class Error(val message: String) : VideoLibraryUiState
}
