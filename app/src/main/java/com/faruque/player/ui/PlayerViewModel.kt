package com.faruque.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faruque.player.data.MediaRepository
import com.faruque.player.model.LibraryUiState
import com.faruque.player.model.MediaFilter
import com.faruque.player.model.MediaFolder
import com.faruque.player.model.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackSession(
    val playlist: List<MediaItemModel>,
    val currentIndex: Int
) {
    val currentItem: MediaItemModel?
        get() = playlist.getOrNull(currentIndex)
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _selectedFolderName = MutableStateFlow<String?>(null)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _mediaFilter = MutableStateFlow(MediaFilter.ALL)
    val mediaFilter: StateFlow<MediaFilter> = _mediaFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _playbackSession = MutableStateFlow<PlaybackSession?>(null)
    val playbackSession: StateFlow<PlaybackSession?> = _playbackSession.asStateFlow()

    init {
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            runCatching { repository.loadLibrary() }
                .onSuccess { (folders, allItems) ->
                    _uiState.value = if (allItems.isEmpty()) {
                        LibraryUiState.Empty
                    } else {
                        LibraryUiState.Content(folders = folders, allItems = allItems)
                    }
                    // Keep selected folder open only if it still exists
                    val openFolder = _selectedFolderName.value
                    if (openFolder != null && folders.none { it.name == openFolder }) {
                        _selectedFolderName.value = null
                    }
                }
                .onFailure { err ->
                    _uiState.value = LibraryUiState.Error(
                        err.localizedMessage ?: "Failed to load media"
                    )
                }
        }
    }

    fun onFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            repository.importFolderTree(treeUri)
            refreshLibrary()
        }
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importFiles(uris)
            val state = _uiState.value
            if (imported.isNotEmpty() && state is LibraryUiState.Content) {
                // Reload full grouped folder library
                refreshLibrary()
            } else if (imported.isNotEmpty()) {
                refreshLibrary()
            }
        }
    }

    fun onExternalMediaOpened(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importFiles(listOf(uri))
            val target = imported.firstOrNull() ?: repository.resolveSingleUri(uri)
            if (target != null) {
                refreshLibrary()
                _playbackSession.value = PlaybackSession(
                    playlist = listOf(target),
                    currentIndex = 0
                )
            }
        }
    }

    fun openFolder(folderName: String) {
        _selectedFolderName.value = folderName
    }

    fun closeFolder() {
        _selectedFolderName.value = null
    }

    fun setMediaFilter(filter: MediaFilter) {
        _mediaFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playItem(item: MediaItemModel, playlist: List<MediaItemModel>) {
        val list = if (playlist.isEmpty()) listOf(item) else playlist
        val idx = list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _playbackSession.value = PlaybackSession(playlist = list, currentIndex = idx)
    }

    fun playNext() {
        val session = _playbackSession.value ?: return
        if (session.currentIndex + 1 < session.playlist.size) {
            _playbackSession.value = session.copy(currentIndex = session.currentIndex + 1)
        }
    }

    fun playPrevious() {
        val session = _playbackSession.value ?: return
        if (session.currentIndex - 1 >= 0) {
            _playbackSession.value = session.copy(currentIndex = session.currentIndex - 1)
        }
    }

    fun closePlayer() {
        _playbackSession.value = null
        refreshLibrary()
    }

    fun savePlaybackProgress(id: String, positionMs: Long, durationMs: Long) {
        repository.savePlaybackPosition(id, positionMs, durationMs)
    }

    fun removeItem(item: MediaItemModel) {
        viewModelScope.launch {
            repository.removeItem(item)
            refreshLibrary()
        }
    }

    fun removeFolder(folder: MediaFolder) {
        viewModelScope.launch {
            repository.removeFolder(folder)
            if (_selectedFolderName.value == folder.name) {
                _selectedFolderName.value = null
            }
            refreshLibrary()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedFolderName.value = null
            _uiState.value = LibraryUiState.Empty
        }
    }
}
