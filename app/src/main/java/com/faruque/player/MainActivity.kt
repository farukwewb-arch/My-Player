package com.faruque.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faruque.player.data.Stats
import com.faruque.player.ui.FolderBrowserScreen
import com.faruque.player.ui.PlayerScreen
import com.faruque.player.ui.PlayerViewModel
import com.faruque.player.ui.theme.MyPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Stats.log(this, "app_launch")

        val incomingUri = extractMediaUri(intent)

        setContent {
            MyPlayerTheme(darkTheme = true) {
                MyPlayerApp(initialExternalUri = incomingUri)
            }
        }
    }

    private fun extractMediaUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

@Composable
fun MyPlayerApp(
    initialExternalUri: Uri? = null,
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFolderName by viewModel.selectedFolderName.collectAsStateWithLifecycle()
    val mediaFilter by viewModel.mediaFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playbackSession by viewModel.playbackSession.collectAsStateWithLifecycle()

    LaunchedEffect(initialExternalUri) {
        if (initialExternalUri != null) {
            viewModel.onExternalMediaOpened(initialExternalUri)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        viewModel.onFolderPicked(treeUri)
    }

    val openDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.onFilesPicked(uris)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.onFilesPicked(uris)
    }

    val session = playbackSession
    val activeItem = session?.currentItem
    if (session != null && activeItem != null) {
        PlayerScreen(
            mediaItem = activeItem,
            hasPrevious = session.currentIndex > 0,
            hasNext = session.currentIndex + 1 < session.playlist.size,
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            onSaveProgress = { id, pos, dur -> viewModel.savePlaybackProgress(id, pos, dur) },
            onBack = { viewModel.closePlayer() }
        )
    } else {
        FolderBrowserScreen(
            uiState = uiState,
            selectedFolderName = selectedFolderName,
            mediaFilter = mediaFilter,
            searchQuery = searchQuery,
            onOpenFolder = { viewModel.openFolder(it) },
            onCloseFolder = { viewModel.closeFolder() },
            onSetFilter = { viewModel.setMediaFilter(it) },
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onPlayItem = { item, playlist -> viewModel.playItem(item, playlist) },
            onRemoveItem = { viewModel.removeItem(it) },
            onRemoveFolder = { viewModel.removeFolder(it) },
            onClearAll = { viewModel.clearAll() },
            onPickFolder = { folderPickerLauncher.launch(null) },
            onPickFiles = { openDocumentsLauncher.launch(arrayOf("video/*", "audio/*")) },
            onPickGalleryVideos = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        )
    }
}
