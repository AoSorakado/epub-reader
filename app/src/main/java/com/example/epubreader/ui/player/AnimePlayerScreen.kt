@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.example.epubreader.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.epubreader.data.anime.DandanplayApiClient
import com.example.epubreader.data.anime.DanmakuItem
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnimePlayerScreen(
    anime: AnimeEntity,
    episode: AnimeEpisodeEntity,
    allEpisodes: List<AnimeEpisodeEntity>,
    backdrop: Backdrop,
    themeAccent: Color,
    webDavAuth: Pair<String, String>?, // username, password
    onExit: (positionMs: Long, durationMs: Long) -> Unit,
    onNextEpisode: (nextEp: AnimeEpisodeEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Keep screen on & landscape orientation
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Audio Manager for Volume control
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(episode.lastPlayedPositionMs) }
    var durationMs by remember { mutableStateOf(episode.durationMs.coerceAtLeast(1L)) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSpeedBoosting by remember { mutableStateOf(false) }

    // Gesture HUD States
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var showGestureHud by remember { mutableStateOf(false) }

    // Danmaku States
    var danmakuList by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var danmakuConfig by remember { mutableStateOf(DanmakuConfig()) }
    var showDanmakuSettings by remember { mutableStateOf(false) }

    // Track Selector States
    var showTrackSelector by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }

    // Initialize Media3 ExoPlayer with OkHttp DataSource (WebDAV Direct Streaming)
    val exoPlayer = remember {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val newReq = if (webDavAuth != null && webDavAuth.first.isNotBlank()) {
                    val cred = Credentials.basic(webDavAuth.first, webDavAuth.second)
                    req.newBuilder().header("Authorization", cred).build()
                } else req
                chain.proceed(newReq)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // min buffer 15s
                50000, // max buffer 50s
                2500,  // playback start buffer 2.5s
                5000   // rebuffer 5s
            )
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                playWhenReady = true

                // Build MediaItem
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(episode.videoUrl)

                // Attach external subtitle if present
                if (!episode.subtitleUrl.isNullOrBlank()) {
                    val subMime = if (episode.subtitleUrl.endsWith(".ass", ignoreCase = true)) {
                        MimeTypes.TEXT_SSA
                    } else MimeTypes.APPLICATION_SUBRIP

                    val subConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(episode.subtitleUrl))
                        .setMimeType(subMime)
                        .setLanguage("zh")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                if (episode.lastPlayedPositionMs > 5000L) {
                    seekTo(episode.lastPlayedPositionMs)
                }
            }
    }

    // Load Danmaku Comments from dandanplay
    LaunchedEffect(episode.id) {
        try {
            val match = DandanplayApiClient.matchEpisode(anime.title, episode.episodeNumber)
            if (match != null) {
                val comments = DandanplayApiClient.getDanmakuComments(match.episodeId)
                danmakuList = comments
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Update Player Time Loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            bufferedPositionMs = exoPlayer.bufferedPosition
            isPlaying = exoPlayer.isPlaying
            delay(200L)
        }
    }

    // Auto Hide Controls timer
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isLocked) {
            delay(5000L)
            isControlsVisible = false
        }
    }

    // Handle Back Navigation
    BackHandler {
        onExit(exoPlayer.currentPosition, exoPlayer.duration)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Media3 Video Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Danmaku Canvas Overlay
        DanmakuCanvas(
            danmakuList = danmakuList,
            currentPositionMs = currentPositionMs,
            config = danmakuConfig,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Gesture Detection Layer
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var dragStartY by remember { mutableFloatStateOf(0f) }
        var dragMode by remember { mutableIntStateOf(0) } // 0: None, 1: Volume, 2: Brightness, 3: Seek
        var seekTargetMs by remember { mutableLongStateOf(0L) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                    } else {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible },
                            onDoubleTap = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            onLongPress = {
                                isSpeedBoosting = true
                                exoPlayer.setPlaybackSpeed(playbackSpeed * 2.0f)
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragStartY = offset.y
                                dragMode = 0
                                seekTargetMs = exoPlayer.currentPosition
                            },
                            onDragEnd = {
                                if (dragMode == 3) {
                                    exoPlayer.seekTo(seekTargetMs)
                                }
                                showGestureHud = false
                                dragMode = 0
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                }
                            },
                            onDragCancel = {
                                showGestureHud = false
                                dragMode = 0
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val width = size.width
                                val height = size.height

                                if (dragMode == 0) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 10f) {
                                        dragMode = 3 // Seek
                                    } else if (abs(dragAmount.y) > 10f) {
                                        dragMode = if (dragStartX < width / 2f) 2 else 1 // 2: Brightness, 1: Volume
                                    }
                                }

                                when (dragMode) {
                                    1 -> {
                                        // Volume (Right side)
                                        val delta = -dragAmount.y / height
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val nextVol = (curVol + (delta * maxVolume * 1.5f).toInt()).coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                        gestureHudText = "音量: ${(nextVol * 100 / maxVolume)}%"
                                        gestureHudIcon = if (nextVol == 0) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp
                                        showGestureHud = true
                                    }
                                    2 -> {
                                        // Brightness (Left side)
                                        val delta = -dragAmount.y / height
                                        val lp = activity?.window?.attributes
                                        val currentBrightness = lp?.screenBrightness ?: -1f
                                        val curBright = if (currentBrightness < 0f) 0.5f else currentBrightness
                                        val nextBright = (curBright + delta * 1.2f).coerceIn(0.05f, 1f)
                                        lp?.screenBrightness = nextBright
                                        activity?.window?.attributes = lp
                                        gestureHudText = "亮度: ${(nextBright * 100).toInt()}%"
                                        gestureHudIcon = Icons.Filled.BrightnessMedium
                                        showGestureHud = true
                                    }
                                    3 -> {
                                        // Seek
                                        val deltaSec = (dragAmount.x / width) * 120f
                                        seekTargetMs = (seekTargetMs + (deltaSec * 1000).toLong()).coerceIn(0L, durationMs)
                                        val currentSec = seekTargetMs / 1000
                                        val totalSec = durationMs / 1000
                                        gestureHudText = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60)
                                        gestureHudIcon = if (dragAmount.x >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind
                                        showGestureHud = true
                                    }
                                }
                            }
                        )
                    }
                }
        )

        // 4. Gesture HUD Pill Indicator (Center Screen)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureHudIcon?.let {
                        Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = gestureHudText ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Long Press Speed Boost Indicator
        AnimatedVisibility(
            visible = isSpeedBoosting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "▶▶ 2.0X 快进中",
                    color = themeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Liquid Frosted Glass Overlay Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    LiquidButton(
                        onClick = { onExit(exoPlayer.currentPosition, exoPlayer.duration) },
                        backdrop = backdrop,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    // Anime Title & Episode Name Pill
                    LiquidButton(
                        onClick = {},
                        backdrop = backdrop,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "${anime.title} · ${episode.title}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // Aspect Ratio Button
                    LiquidButton(
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        backdrop = backdrop,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(44.dp)
                    ) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }
                }

                // Lock Button (Left Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                ) {
                    LiquidButton(
                        onClick = { isLocked = !isLocked },
                        backdrop = backdrop,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Lock",
                            tint = if (isLocked) themeAccent else Color.White
                        )
                    }
                }

                if (!isLocked) {
                    // Bottom Control Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        // Progress Bar & Times
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val curSec = currentPositionMs / 1000
                            val durSec = durationMs / 1000
                            Text(
                                text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Slider(
                                value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                                onValueChange = { currentPositionMs = it.toLong() },
                                onValueChangeFinished = { exoPlayer.seekTo(currentPositionMs) },
                                valueRange = 0f..durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = themeAccent,
                                    activeTrackColor = themeAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = String.format("%02d:%02d", durSec / 60, durSec % 60),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Play / Pause
                                LiquidButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    backdrop = backdrop,
                                    shape = CircleShape,
                                    modifier = Modifier.requiredSize(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White
                                    )
                                }

                                // Next Episode
                                val curIndex = allEpisodes.indexOfFirst { it.id == episode.id }
                                val nextEpisode = allEpisodes.getOrNull(curIndex + 1)
                                if (nextEpisode != null) {
                                    LiquidButton(
                                        onClick = { onNextEpisode(nextEpisode) },
                                        backdrop = backdrop,
                                        shape = CircleShape,
                                        modifier = Modifier.requiredSize(44.dp)
                                    ) {
                                        Icon(Icons.Filled.SkipNext, contentDescription = "Next Episode", tint = Color.White)
                                    }
                                }

                                // Danmaku Toggle Pill
                                LiquidButton(
                                    onClick = {
                                        danmakuConfig = danmakuConfig.copy(isEnabled = !danmakuConfig.isEnabled)
                                    },
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        text = if (danmakuConfig.isEnabled) "弹幕 开" else "弹幕 关",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (danmakuConfig.isEnabled) themeAccent else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }

                                // Danmaku Settings Button
                                LiquidButton(
                                    onClick = { showDanmakuSettings = true },
                                    backdrop = backdrop,
                                    shape = CircleShape,
                                    modifier = Modifier.requiredSize(44.dp)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = "Danmaku Settings", tint = Color.White)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Playback Speed Pill
                                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                LiquidButton(
                                    onClick = {
                                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                        playbackSpeed = speeds[nextIdx]
                                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                                    },
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (playbackSpeed != 1.0f) themeAccent else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Danmaku Settings Bottom Sheet
        if (showDanmakuSettings) {
            ModalBottomSheet(
                onDismissRequest = { showDanmakuSettings = false },
                containerColor = Color(0xFF1E1A29)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "弹幕设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "不透明度: ${(danmakuConfig.opacity * 100).toInt()}%", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = danmakuConfig.opacity,
                            onValueChange = { danmakuConfig = danmakuConfig.copy(opacity = it) },
                            valueRange = 0.2f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.width(180.dp)
                        )
                    }

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "字体大小: ${danmakuConfig.fontSizeSp.toInt()}sp", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = danmakuConfig.fontSizeSp,
                            onValueChange = { danmakuConfig = danmakuConfig.copy(fontSizeSp = it) },
                            valueRange = 12f..26f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.width(180.dp)
                        )
                    }

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "滚动速度: ${String.format("%.1f", danmakuConfig.speedFactor)}x", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = danmakuConfig.speedFactor,
                            onValueChange = { danmakuConfig = danmakuConfig.copy(speedFactor = it) },
                            valueRange = 0.6f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = themeAccent, activeTrackColor = themeAccent),
                            modifier = Modifier.width(180.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Time Offset Fine Tune (±0.5s)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "弹幕时间轴微调: ${(danmakuConfig.timeOffsetMs / 1000f)}s", fontSize = 13.sp, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { danmakuConfig = danmakuConfig.copy(timeOffsetMs = danmakuConfig.timeOffsetMs - 500L) },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("-0.5s", color = Color.White, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { danmakuConfig = danmakuConfig.copy(timeOffsetMs = danmakuConfig.timeOffsetMs + 500L) },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+0.5s", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
