package com.faruque.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.faruque.player.R
import com.faruque.player.data.Stats
import com.faruque.player.data.formatDuration
import com.faruque.player.model.MediaItemModel
import com.faruque.player.model.MediaType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

private data class ResizeOption(
    val label: String,
    val resizeMode: Int
)

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
private val RESIZE_OPTIONS = listOf(
    ResizeOption("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ResizeOption("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ResizeOption("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL)
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    mediaItem: MediaItemModel,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSaveProgress: (id: String, positionMs: Long, durationMs: Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var playbackError by remember(mediaItem.uri) { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember(mediaItem.uri) {
        mutableLongStateOf(mediaItem.lastPositionMs.coerceAtLeast(0L))
    }
    var durationMs by remember(mediaItem.uri) {
        mutableLongStateOf(mediaItem.durationMs.coerceAtLeast(0L))
    }
    var isScrubbingSlider by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }

    var speedIndex by remember { mutableIntStateOf(2) } // 1.0x
    var resizeIndex by remember { mutableIntStateOf(0) } // Fit
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isLandscape by remember { mutableStateOf(false) }

    // Volume & Brightness states
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var currentVolumeRatio by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
        )
    }
    var isMuted by remember { mutableStateOf(currentVolumeRatio == 0f) }
    var preMuteVolume by remember { mutableFloatStateOf(0.5f) }

    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0.01f..1f } ?: 0.5f
        )
    }

    // Gesture HUD feedback states
    var activeDragMode by remember { mutableStateOf(DragMode.NONE) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var doubleTapText by remember { mutableStateOf<String?>(null) }
    var statusBannerText by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(mediaItem.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaItem.uri))
            prepare()
            if (mediaItem.lastPositionMs > 1500L) {
                seekTo(mediaItem.lastPositionMs)
            }
            playWhenReady = true
        }
    }

    LaunchedEffect(speedIndex, exoPlayer) {
        exoPlayer.setPlaybackSpeed(SPEED_OPTIONS[speedIndex])
    }

    LaunchedEffect(repeatMode, exoPlayer) {
        exoPlayer.repeatMode = repeatMode
    }

    // Poll playback position smoothly
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: mediaItem.durationMs
            if (dur > 0L) durationMs = dur
            if (!isScrubbingSlider) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(400L)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(controlsVisible, isPlaying, isLocked, activeDragMode) {
        if (controlsVisible && isPlaying && !isLocked && activeDragMode == DragMode.NONE) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(showUnlockButton) {
        if (showUnlockButton) {
            delay(2500L)
            showUnlockButton = false
        }
    }

    LaunchedEffect(doubleTapText) {
        if (doubleTapText != null) {
            delay(750L)
            doubleTapText = null
        }
    }

    LaunchedEffect(statusBannerText) {
        if (statusBannerText != null) {
            delay(1400L)
            statusBannerText = null
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        Stats.log(context, "video_play")

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    onSaveProgress(
                        mediaItem.id,
                        exoPlayer.currentPosition.coerceAtLeast(0L),
                        exoPlayer.duration.coerceAtLeast(0L)
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onSaveProgress(mediaItem.id, 0L, exoPlayer.duration.coerceAtLeast(0L))
                    if (hasNext && repeatMode == Player.REPEAT_MODE_OFF) {
                        onNext()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                onSaveProgress(
                    mediaItem.id,
                    exoPlayer.currentPosition.coerceAtLeast(0L),
                    exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            onSaveProgress(
                mediaItem.id,
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L)
            )
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Restore orientation and system brightness when leaving PlayerScreen completely
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
        }
    }

    BackHandler {
        if (isLocked) {
            showUnlockButton = true
        } else {
            onBack()
        }
    }

    fun applyVolume(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1f)
        currentVolumeRatio = clamped
        isMuted = clamped == 0f
        if (clamped > 0f) preMuteVolume = clamped
        val targetStep = (clamped * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
    }

    fun applyBrightness(ratio: Float) {
        val clamped = ratio.coerceIn(0.02f, 1f)
        currentBrightness = clamped
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = clamped
            win.attributes = lp
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Video surface or Audio visualizer backdrop
        if (mediaItem.mediaType == MediaType.VIDEO) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = RESIZE_OPTIONS[resizeIndex].resizeMode
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = RESIZE_OPTIONS[resizeIndex].resizeMode
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exo_player_view")
            )
        } else {
            AudioPlaybackBackdrop(
                mediaItem = mediaItem,
                isPlaying = isPlaying
            )
        }

        // MX Player Gesture Layer (Tap, Double-Tap -10s/+10s, Vertical Brightness/Volume, Horizontal Seek)
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var accumulatedX by remember { mutableFloatStateOf(0f) }
        var accumulatedY by remember { mutableFloatStateOf(0f) }
        var dragStartPosition by remember { mutableLongStateOf(0L) }
        var dragStartVolume by remember { mutableFloatStateOf(currentVolumeRatio) }
        var dragStartBrightness by remember { mutableFloatStateOf(currentBrightness) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, exoPlayer) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) {
                                showUnlockButton = !showUnlockButton
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked) {
                                showUnlockButton = true
                                return@detectTapGestures
                            }
                            val dur = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
                            val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                            if (offset.x < containerWidthPx * 0.45f) {
                                val target = (cur - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                doubleTapText = "-10s"
                            } else if (offset.x > containerWidthPx * 0.55f) {
                                val target = if (dur > 0L) (cur + 10_000L).coerceAtMost(dur) else cur + 10_000L
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                doubleTapText = "+10s"
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    )
                }
                .pointerInput(isLocked, exoPlayer) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (isLocked) return@detectDragGestures
                            dragStartX = startOffset.x
                            accumulatedX = 0f
                            accumulatedY = 0f
                            activeDragMode = DragMode.NONE
                            dragStartPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                            dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                .toFloat() / maxVolume.toFloat()
                            currentVolumeRatio = dragStartVolume
                            dragStartBrightness = currentBrightness
                        },
                        onDragEnd = {
                            if (activeDragMode == DragMode.SEEK) {
                                exoPlayer.seekTo(seekPreviewMs)
                                currentPositionMs = seekPreviewMs
                            }
                            activeDragMode = DragMode.NONE
                        },
                        onDragCancel = {
                            activeDragMode = DragMode.NONE
                        },
                        onDrag = { change, dragAmount ->
                            if (isLocked) return@detectDragGestures
                            change.consume()
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y

                            if (activeDragMode == DragMode.NONE) {
                                if (abs(accumulatedX) > 24f && abs(accumulatedX) > abs(accumulatedY)) {
                                    activeDragMode = DragMode.SEEK
                                } else if (abs(accumulatedY) > 24f && abs(accumulatedY) > abs(accumulatedX)) {
                                    activeDragMode = if (dragStartX < containerWidthPx / 2f) {
                                        DragMode.BRIGHTNESS
                                    } else {
                                        DragMode.VOLUME
                                    }
                                }
                            }

                            when (activeDragMode) {
                                DragMode.SEEK -> {
                                    val dur = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
                                    val maxSpanMs = dur.coerceAtMost(180_000L).coerceAtLeast(60_000L)
                                    val deltaMs = ((accumulatedX / containerWidthPx) * maxSpanMs).toLong()
                                    val target = if (dur > 0L) {
                                        (dragStartPosition + deltaMs).coerceIn(0L, dur)
                                    } else {
                                        (dragStartPosition + deltaMs).coerceAtLeast(0L)
                                    }
                                    seekPreviewMs = target
                                    seekDeltaMs = target - dragStartPosition
                                }

                                DragMode.VOLUME -> {
                                    val deltaRatio = -accumulatedY / (containerHeightPx * 0.65f)
                                    applyVolume(dragStartVolume + deltaRatio)
                                }

                                DragMode.BRIGHTNESS -> {
                                    val deltaRatio = -accumulatedY / (containerHeightPx * 0.65f)
                                    applyBrightness(dragStartBrightness + deltaRatio)
                                }

                                DragMode.NONE -> Unit
                            }
                        }
                    )
                }
        )

        // Gesture HUD Overlays (Brightness / Volume / Seek / DoubleTap / Status)
        when (activeDragMode) {
            DragMode.BRIGHTNESS -> {
                VerticalGestureHud(
                    icon = Icons.Filled.BrightnessHigh,
                    label = stringResource(R.string.brightness_label),
                    value = currentBrightness,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp)
                )
            }
            DragMode.VOLUME -> {
                VerticalGestureHud(
                    icon = if (currentVolumeRatio <= 0.01f) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    label = stringResource(R.string.volume_label),
                    value = currentVolumeRatio,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp)
                )
            }
            DragMode.SEEK -> {
                val sign = if (seekDeltaMs >= 0L) "+" else "-"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatDuration(seekPreviewMs),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "($sign${formatDuration(abs(seekDeltaMs))})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            DragMode.NONE -> Unit
        }

        doubleTapText?.let { text ->
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        statusBannerText?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp)
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Locked Screen Unlock Pill
        AnimatedVisibility(
            visible = isLocked && showUnlockButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        showUnlockButton = false
                        controlsVisible = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = stringResource(R.string.unlock_screen_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Main Top & Bottom Player Controls
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_desc),
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mediaItem.folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Playback Speed button
                    TextButton(
                        onClick = {
                            speedIndex = (speedIndex + 1) % SPEED_OPTIONS.size
                            statusBannerText = "গতি ${SPEED_OPTIONS[speedIndex]}x"
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("speed_button")
                    ) {
                        Text(
                            text = "${SPEED_OPTIONS[speedIndex]}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Repeat mode toggle
                    IconButton(
                        onClick = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> {
                                    statusBannerText = "রিপিট: একটি"
                                    Player.REPEAT_MODE_ONE
                                }
                                Player.REPEAT_MODE_ONE -> {
                                    statusBannerText = "রিপিট: সব"
                                    Player.REPEAT_MODE_ALL
                                }
                                else -> {
                                    statusBannerText = "রিপিট: বন্ধ"
                                    Player.REPEAT_MODE_OFF
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("repeat_button")
                    ) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            contentDescription = stringResource(R.string.repeat_mode_desc),
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                Color.White.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // Lock screen button
                    IconButton(
                        onClick = {
                            isLocked = true
                            controlsVisible = false
                            showUnlockButton = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.lock_screen_desc),
                            tint = Color.White
                        )
                    }
                }

                // Center Transport Deck (Prev / -10s / Play-Pause / +10s / Next)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = hasPrevious,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("prev_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_track_desc),
                            tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = stringResource(R.string.rewind_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play_pause_desc),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val dur = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
                            val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                            val target = if (dur > 0L) (cur + 10_000L).coerceAtMost(dur) else cur + 10_000L
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = stringResource(R.string.forward_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        enabled = hasNext,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("next_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_track_desc),
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Bottom Seekbar & Quick MX Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                            )
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
                    val displayPosition = if (isScrubbingSlider) {
                        sliderPositionMs.toLong()
                    } else {
                        currentPositionMs
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(displayPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )

                        Slider(
                            value = displayPosition.toFloat().coerceIn(0f, safeDuration),
                            onValueChange = { newVal ->
                                isScrubbingSlider = true
                                sliderPositionMs = newVal
                            },
                            onValueChangeFinished = {
                                val target = sliderPositionMs.toLong().coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                isScrubbingSlider = false
                            },
                            valueRange = 0f..safeDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .testTag("seek_slider")
                        )

                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Volume Mute / Slider row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isMuted) {
                                        applyVolume(preMuteVolume.coerceAtLeast(0.35f))
                                    } else {
                                        applyVolume(0f)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isMuted || currentVolumeRatio <= 0.01f) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = stringResource(R.string.mute_desc),
                                    tint = Color.White
                                )
                            }

                            Slider(
                                value = currentVolumeRatio,
                                onValueChange = { applyVolume(it) },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                ),
                                modifier = Modifier
                                    .width(110.dp)
                                    .testTag("volume_slider")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mediaItem.mediaType == MediaType.VIDEO) {
                                IconButton(
                                    onClick = {
                                        resizeIndex = (resizeIndex + 1) % RESIZE_OPTIONS.size
                                        statusBannerText = "স্ক্রিন: ${RESIZE_OPTIONS[resizeIndex].label}"
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("aspect_ratio_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AspectRatio,
                                        contentDescription = stringResource(R.string.aspect_ratio_desc),
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isLandscape = !isLandscape
                                        activity?.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("rotate_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ScreenRotation,
                                        contentDescription = stringResource(R.string.rotate_screen_desc),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.gesture_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (playbackError) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_error_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalGestureHud(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.78f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            LinearProgressIndicator(
                progress = { value.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .width(56.dp)
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun AudioPlaybackBackdrop(
    mediaItem: MediaItemModel,
    isPlaying: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        Color(0xFF0A0E18)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = stringResource(R.string.audio_track_badge),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isPlaying) stringResource(R.string.audio_track_badge) else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
