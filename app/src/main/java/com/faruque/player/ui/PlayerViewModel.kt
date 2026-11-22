package com.faruque.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faruque.player.data.VideoRepository
import com.faruque.player.model.VideoItem
import com.faruque.player.model.VideoLibraryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<VideoLibraryUiState>(VideoLibraryUiState.Loading)
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    private val _selectedVideo = MutableStateFlow<VideoItem?>(null)
    val selectedVideo: StateFlow<VideoItem?> = _selectedVideo.asStateFlow()

    private var cachedVideos: List<VideoItem> = emptyList()

    init {
        loadInitialVideos()
    }

    fun loadInitialVideos() {
        viewModelScope.launch {
            _uiState.value = VideoLibraryUiState.Loading
            runCatching { repository.loadInitialVideos() }
                .onSuccess { videos ->
                    cachedVideos = videos
                    _uiState.value = if (videos.isEmpty()) {
                        VideoLibraryUiState.Empty
                    } else {
                        VideoLibraryUiState.Content(videos)
                    }
                }
                .onFailure { err ->
                    _uiState.value = VideoLibraryUiState.Error(
                        err.localizedMessage ?: "Failed to load videos"
                    )
                }
        }
    }

    fun onVideosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importPickedVideos(uris)
            if (imported.isNotEmpty()) {
                val merged = LinkedHashMap<String, VideoItem>(cachedVideos.size + imported.size)
                for (item in imported) {
                    merged[item.id] = item
                }
                for (item in cachedVideos) {
                    merged.putIfAbsent(item.id, item)
                }
                cachedVideos = merged.values.toList()
                _uiState.value = VideoLibraryUiState.Content(cachedVideos)
            }
        }
    }

    fun onExternalVideoOpened(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importPickedVideos(listOf(uri))
            val target = imported.firstOrNull() ?: VideoItem(
                id = uri.toString(),
                title = uri.lastPathSegment?.substringAfterLast('/') ?: "Video",
                uri = uri,
                durationMs = 0L
            )
            val merged = LinkedHashMap<String, VideoItem>()
            merged[target.id] = target
            for (item in cachedVideos) {
                merged.putIfAbsent(item.id, item)
            }
            cachedVideos = merged.values.toList()
            _uiState.value = VideoLibraryUiState.Content(cachedVideos)
            _selectedVideo.value = target
        }
    }

    fun onSelectVideo(video: VideoItem) {
        _selectedVideo.value = video
    }

    fun onClosePlayer() {
        _selectedVideo.value = null
    }

    fun onRemoveVideo(video: VideoItem) {
        repository.removePersistedUri(video.uri)
        cachedVideos = cachedVideos.filterNot { it.id == video.id }
        _uiState.value = if (cachedVideos.isEmpty()) {
            VideoLibraryUiState.Empty
        } else {
            VideoLibraryUiState.Content(cachedVideos)
        }
    }

    fun onClearAll() {
        repository.clearPersistedUris()
        cachedVideos = emptyList()
        _uiState.value = VideoLibraryUiState.Empty
    }
}
