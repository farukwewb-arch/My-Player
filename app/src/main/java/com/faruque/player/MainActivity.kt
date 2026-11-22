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
import com.faruque.player.ui.PlayerScreen
import com.faruque.player.ui.PlayerViewModel
import com.faruque.player.ui.VideoListScreen
import com.faruque.player.ui.theme.MyPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Stats.log(this, "app_launch")

        val incomingVideoUri = extractVideoUri(intent)

        setContent {
            MyPlayerTheme {
                MyPlayerApp(initialExternalUri = incomingVideoUri)
            }
        }
    }

    private fun extractVideoUri(intent: Intent?): Uri? {
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
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()

    LaunchedEffect(initialExternalUri) {
        if (initialExternalUri != null) {
            viewModel.onExternalVideoOpened(initialExternalUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.onVideosPicked(uris)
    }

    val openDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.onVideosPicked(uris)
    }

    val activeVideo = selectedVideo
    if (activeVideo != null) {
        PlayerScreen(
            video = activeVideo,
            onBack = { viewModel.onClosePlayer() }
        )
    } else {
        VideoListScreen(
            uiState = uiState,
            onSelectVideo = { viewModel.onSelectVideo(it) },
            onRemoveVideo = { viewModel.onRemoveVideo(it) },
            onClearAll = { viewModel.onClearAll() },
            onPickFromGallery = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickFromFiles = {
                openDocumentsLauncher.launch(arrayOf("video/*"))
            }
        )
    }
}
