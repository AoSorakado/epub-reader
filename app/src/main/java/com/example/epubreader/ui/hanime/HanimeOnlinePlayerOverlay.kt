@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.epubreader.ui.hanime

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.epubreader.data.hanime.HanimeApiClient
import com.example.epubreader.data.hanime.HanimeVideo
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.components.liquid.LiquidSlider
import com.example.epubreader.ui.player.MpvPlayerManager
import com.example.epubreader.ui.player.MpvVideoView
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HanimeOnlinePlayerOverlay(
    video: HanimeVideo,
    initialResolution: String,
    currentEpIndex: Int,
    onExit: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onSelectEpisode: ((String, Int) -> Unit)? = null,
    backdrop: Backdrop? = null,
    themeAccent: Color = Color(0xFF6366F1)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var activeResolution by remember(video.videoCode) {
        val res = if (video.videoUrls.containsKey(initialResolution)) {
            initialResolution
        } else {
            video.videoUrls.keys.firstOrNull() ?: initialResolution
        }
        mutableStateOf(res)
    }

    var currentStreamUrl by remember(video.videoCode, activeResolution) {
        val target = video.videoUrls[activeResolution] ?: video.bestStreamUrl.orEmpty()
        mutableStateOf(target)
    }

    val playerManager = remember { MpvPlayerManager(context) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragSeekPositionMs by remember { mutableLongStateOf(0L) }

    // Gestures States
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragMode by remember { mutableIntStateOf(0) } // 0: None, 1: Volume, 2: Brightness, 3: Seek
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var showGestureHud by remember { mutableStateOf(false) }
    var isSpeedBoosting by remember { mutableStateOf(false) }

    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeModeIndex by remember { mutableStateOf(0) } // 0: FIT, 1: ZOOM, 2: FILL

    var isSpeedMenuOpen by remember { mutableStateOf(false) }
    var isResolutionMenuOpen by remember { mutableStateOf(false) }
    var isPlaylistSheetOpen by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // 1. Hide System Status Bar & Navigation Bar + Lock Landscape Orientation + Cutout support
    DisposableEffect(Unit) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (window != null && activity != null) {
            try {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())

                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }
            } catch (e: Throwable) {
                android.util.Log.e("HanimeOnlinePlayer", "Orientation/Cutout setup error: ${e.message}")
            }
        }

        onDispose {
            playerManager.destroy()
            if (window != null && activity != null) {
                try {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    activity.requestedOrientation = originalOrientation
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val lp = window.attributes
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        window.attributes = lp
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("HanimeOnlinePlayer", "Orientation restore error: ${e.message}")
                }
            }
        }
    }

    // Auto load stream URL with necessary custom headers
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotBlank()) {
            val headers = mapOf(
                "User-Agent" to HanimeApiClient.USER_AGENT,
                "Referer" to HanimeApiClient.DEFAULT_BASE_URL
            )
            playerManager.loadFile(currentStreamUrl, headers)
            playerManager.setSpeed(selectedSpeed)
            playerManager.play()
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(areControlsVisible, isDraggingSeek, isSpeedMenuOpen, isResolutionMenuOpen, isPlaylistSheetOpen) {
        if (areControlsVisible && !isDraggingSeek && !isSpeedMenuOpen && !isResolutionMenuOpen && !isPlaylistSheetOpen) {
            delay(4000)
            areControlsVisible = false
        }
    }

    BackHandler {
        onExit()
    }

    val isPlaying by playerManager.isPlaying
    val positionMs by playerManager.positionMs
    val durationMs by playerManager.durationMs
    val isBuffering by playerManager.isBuffering

    val displayPositionMs = if (isDraggingSeek) dragSeekPositionMs else positionMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isSpeedBoosting) {
                                isSpeedBoosting = false
                                playerManager.setSpeed(selectedSpeed)
                            }
                        }
                    },
                    onTap = {
                        areControlsVisible = !areControlsVisible
                    },
                    onDoubleTap = {
                        playerManager.togglePlayPause()
                    },
                    onLongPress = {
                        isSpeedBoosting = true
                        playerManager.setSpeed(2.0f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragStartY = offset.y
                        dragMode = 0
                    },
                    onDragEnd = {
                        if (dragMode == 3) {
                            playerManager.seekTo(seekTargetMs)
                        }
                        dragMode = 0
                        showGestureHud = false
                        if (isSpeedBoosting) {
                            isSpeedBoosting = false
                            playerManager.setSpeed(selectedSpeed)
                        }
                    },
                    onDragCancel = {
                        dragMode = 0
                        showGestureHud = false
                        if (isSpeedBoosting) {
                            isSpeedBoosting = false
                            playerManager.setSpeed(selectedSpeed)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val totalDx = change.position.x - dragStartX
                        val totalDy = change.position.y - dragStartY

                        if (dragMode == 0) {
                            if (abs(totalDx) > abs(totalDy) && abs(totalDx) > 30f) {
                                dragMode = 3 // Seek
                                seekTargetMs = positionMs
                            } else if (abs(totalDy) > 30f) {
                                val screenWidth = size.width
                                dragMode = if (dragStartX > screenWidth / 2) 1 else 2 // 1: Volume, 2: Brightness
                            }
                        }

                        when (dragMode) {
                            1 -> {
                                // Volume Control (Right side vertical drag)
                                val delta = -dragAmount.y / size.height
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val targetVol = (currentVol + (delta * maxVolume * 2)).roundToInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                val pct = targetVol * 100 / maxVolume
                                gestureHudText = "音量: $pct%"
                                gestureHudIcon = if (targetVol == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp
                                showGestureHud = true
                            }
                            2 -> {
                                // Brightness Control (Left side vertical drag)
                                val delta = -dragAmount.y / size.height
                                val win = activity?.window
                                if (win != null) {
                                    val currentBrightness = win.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                                    val targetBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    val lp = win.attributes
                                    lp.screenBrightness = targetBrightness
                                    win.attributes = lp
                                    val pct = (targetBrightness * 100).toInt()
                                    gestureHudText = "亮度: $pct%"
                                    gestureHudIcon = Icons.Filled.BrightnessMedium
                                    showGestureHud = true
                                }
                            }
                            3 -> {
                                // Seek Control (Horizontal drag)
                                val deltaMs = ((dragAmount.x / size.width) * 90000L).toLong()
                                val safeDur = durationMs.coerceAtLeast(1L)
                                seekTargetMs = (seekTargetMs + deltaMs).coerceIn(0L, safeDur)
                                val sec = seekTargetMs / 1000
                                val durSec = safeDur / 1000
                                gestureHudText = String.format("%02d:%02d / %02d:%02d", sec / 60, sec % 60, durSec / 60, durSec % 60)
                                gestureHudIcon = if (deltaMs >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind
                                showGestureHud = true
                            }
                        }
                    }
                )
            }
    ) {
        // MPV Video Surface View
        AndroidView(
            factory = { ctx ->
                MpvVideoView(ctx).apply {
                    setPlayer(playerManager)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Spinner
        if (isBuffering || (durationMs == 0L && isPlaying)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = themeAccent, strokeWidth = 3.dp)
            }
        }

        // Center Gesture HUD (Volume / Brightness / Seek Progress Pill)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(icon, contentDescription = null, tint = themeAccent, modifier = Modifier.size(26.dp))
                    }
                    gestureHudText?.let { text ->
                        Text(
                            text = text,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "back",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = video.chineseTitle ?: video.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Resolution Switcher Dropdown
                        val availableRes = video.videoUrls.keys.toList().ifEmpty { listOf("1080P", "720P", "480P") }
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { isResolutionMenuOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Resolution",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeResolution,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            DropdownMenu(
                                expanded = isResolutionMenuOpen,
                                onDismissRequest = { isResolutionMenuOpen = false }
                            ) {
                                availableRes.forEach { res ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = res,
                                                fontWeight = if (res == activeResolution) FontWeight.Bold else FontWeight.Normal,
                                                color = if (res == activeResolution) themeAccent else Color.Unspecified
                                            )
                                        },
                                        onClick = {
                                            if (res != activeResolution) {
                                                val targetUrl = video.videoUrls[res]
                                                if (!targetUrl.isNullOrBlank()) {
                                                    val currPos = playerManager.positionMs.value
                                                    activeResolution = res
                                                    currentStreamUrl = targetUrl
                                                    scope.launch {
                                                        delay(300)
                                                        playerManager.seekTo(currPos)
                                                    }
                                                }
                                            }
                                            isResolutionMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Aspect Ratio Toggle (FIT / ZOOM / FILL)
                        IconButton(
                            onClick = {
                                resizeModeIndex = (resizeModeIndex + 1) % 3
                                val mode = when (resizeModeIndex) {
                                    0 -> MpvPlayerManager.ResizeMode.FIT
                                    1 -> MpvPlayerManager.ResizeMode.ZOOM
                                    else -> MpvPlayerManager.ResizeMode.FILL
                                }
                                playerManager.setResizeMode(mode)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "aspect ratio",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play/Pause & Quick Seek Buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newPos = (positionMs - 10000L).coerceAtLeast(0L)
                            playerManager.seekTo(newPos)
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { playerManager.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "play/pause",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val newPos = (positionMs + 10000L).coerceAtMost(durationMs)
                            playerManager.seekTo(newPos)
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    // Time text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMs(displayPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatMs(durationMs),
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Liquid Progress Slider
                    val progressRatio = if (durationMs > 0L) (displayPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    LiquidSlider(
                        value = { progressRatio },
                        onValueChange = { ratio ->
                            isDraggingSeek = true
                            dragSeekPositionMs = (ratio * durationMs.toFloat()).toLong()
                        },
                        onValueChangeFinished = {
                            playerManager.seekTo(dragSeekPositionMs)
                            isDraggingSeek = false
                        },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop,
                        accentColor = themeAccent,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Secondary Action Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Play/Pause Small
                            IconButton(
                                onClick = { playerManager.togglePlayPause() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "play pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Next Episode Button (if available)
                            if (onNextEpisode != null) {
                                IconButton(
                                    onClick = onNextEpisode,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "next episode",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Speed Selector
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .clickable { isSpeedMenuOpen = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${selectedSpeed}x",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                DropdownMenu(
                                    expanded = isSpeedMenuOpen,
                                    onDismissRequest = { isSpeedMenuOpen = false }
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    fontWeight = if (speed == selectedSpeed) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (speed == selectedSpeed) themeAccent else Color.Unspecified
                                                )
                                            },
                                            onClick = {
                                                selectedSpeed = speed
                                                playerManager.setSpeed(speed)
                                                isSpeedMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Episode Selection Sheet Trigger (if has playlist)
                            val playlistEpisodes = video.playlist?.episodes
                            if (!playlistEpisodes.isNullOrEmpty() && playlistEpisodes.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .clickable { isPlaylistSheetOpen = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = "episodes",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "选集",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playlist / Episodes Bottom Sheet
        if (isPlaylistSheetOpen) {
            val playlistEpisodes = video.playlist?.episodes ?: emptyList()
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { isPlaylistSheetOpen = false },
                sheetState = sheetState,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "系列选集",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    ) {
                        items(playlistEpisodes.size) { idx ->
                            val ep = playlistEpisodes[idx]
                            val isCurrent = ep.videoCode == video.videoCode

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) themeAccent else Color(0xFF1E293B))
                                    .clickable {
                                        isPlaylistSheetOpen = false
                                        onSelectEpisode?.invoke(ep.videoCode, idx)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "第 ${idx + 1} 集  ${ep.title}",
                                    color = Color.White,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isCurrent) {
                                    Text(
                                        text = "播放中",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remMinutes = minutes % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, remMinutes, seconds)
    } else {
        "%02d:%02d".format(remMinutes, seconds)
    }
}
