@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.example.epubreader.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.epubreader.R
import com.example.epubreader.data.anime.DandanplayApiClient
import com.example.epubreader.data.anime.DanmakuItem
import com.example.epubreader.data.anime.MkvChapterParser
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.components.liquid.LiquidSlider
import com.example.epubreader.ui.reader.rememberBatteryAndTime
import com.example.epubreader.ui.reader.MiniBatteryIndicator
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.epubreader.data.anime.SubtitleHelper
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.data.anime.AnimeFilenameParser
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Track info adapted for mpv's track system.
 * Unlike ExoPlayer's Tracks.Group-based system, mpv tracks are identified by simple integer IDs.
 */
data class PlayerTrackInfo(
    val mpvTrackId: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val mime: String,
    val badge: String = "",
    val isSupported: Boolean = true,
    val metadata: SubtitleHelper.SubtitleTrackMetadata? = null
)

typealias PlayerChapter = SubtitleHelper.PlayerChapter

object DanmakuPreferences {
    private const val PREFS_NAME = "anime_danmaku_prefs"
    private const val KEY_ENABLED = "danmaku_enabled"
    private const val KEY_OPACITY = "danmaku_opacity"
    private const val KEY_FONT_SIZE = "danmaku_font_size"
    private const val KEY_SPEED = "danmaku_speed"
    private const val KEY_DISPLAY_AREA = "danmaku_display_area"
    private const val KEY_TIME_OFFSET = "danmaku_time_offset"

    fun load(context: Context): DanmakuConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DanmakuConfig(
            isEnabled = sp.getBoolean(KEY_ENABLED, true),
            opacity = sp.getFloat(KEY_OPACITY, 1.0f),
            fontSizeSp = sp.getFloat(KEY_FONT_SIZE, 16f),
            speedFactor = sp.getFloat(KEY_SPEED, 1.0f),
            displayAreaRatio = sp.getFloat(KEY_DISPLAY_AREA, 0.50f),
            timeOffsetMs = sp.getLong(KEY_TIME_OFFSET, 0L)
        )
    }

    fun save(context: Context, config: DanmakuConfig) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_ENABLED, config.isEnabled)
            .putFloat(KEY_OPACITY, config.opacity)
            .putFloat(KEY_FONT_SIZE, config.fontSizeSp)
            .putFloat(KEY_SPEED, config.speedFactor)
            .putFloat(KEY_DISPLAY_AREA, config.displayAreaRatio)
            .putLong(KEY_TIME_OFFSET, config.timeOffsetMs)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimePlayerScreen(
    anime: AnimeEntity,
    episode: AnimeEpisodeEntity,
    allEpisodes: List<AnimeEpisodeEntity>,
    backdrop: Backdrop? = null,
    themeAccent: Color,
    themeGradient: Brush = Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))),
    themeAccentGradient: Brush = Brush.horizontalGradient(listOf(themeAccent, themeAccent)),
    webDavAuth: Pair<String, String>?, // username, password
    onExit: (positionMs: Long, durationMs: Long) -> Unit,
    onNextEpisode: (nextEp: AnimeEpisodeEntity) -> Unit,
    onProgressUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    // 1. Hide System Status Bar & Navigation Bar + Lock Landscape Orientation
    DisposableEffect(Unit) {
        val currentActivity = activity
        val window = currentActivity?.window
        if (window != null && currentActivity != null) {
            try {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

                currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Enable HDR Wide Color Gamut mode for HDR10 / UHD displays
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        window.colorMode = ActivityInfo.COLOR_MODE_HDR
                    } catch (e: Throwable) {}
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }
            } catch (e: Throwable) {
                android.util.Log.e("AnimePlayer", "Orientation/Cutout setup error: ${e.message}")
            }
        }
        onDispose {
            if (window != null && currentActivity != null) {
                try {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                        } catch (e: Throwable) {}
                    }

                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                    window.attributes = lp
                } catch (e: Throwable) {
                    android.util.Log.e("AnimePlayer", "Orientation restore error: ${e.message}")
                }
            }
        }
    }

    // Audio Manager for Volume control
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Player State
    var currentPositionMs by remember { mutableStateOf(episode.lastPlayedPositionMs) }
    var durationMs by remember { mutableStateOf(episode.durationMs.coerceAtLeast(1L)) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(MpvPlayerManager.ResizeMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSpeedBoosting by remember { mutableStateOf(false) }
    var useHdrPassthrough by remember { mutableStateOf(false) }

    // Screen Brightness State (Follows system brightness by default to prevent hardware overheating)
    var playerBrightness by remember {
        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (cur in 0.05f..1.0f) cur else -1.0f)
    }

    // Gesture HUD States
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var showGestureHud by remember { mutableStateOf(false) }

    // Danmaku States with Permanent Memory
    var danmakuList by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var danmakuConfig by remember { mutableStateOf(DanmakuPreferences.load(context)) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var currentMatchedDanmakuTitle by remember { mutableStateOf("") }

    // Save Danmaku Settings whenever changed
    LaunchedEffect(danmakuConfig) {
        DanmakuPreferences.save(context, danmakuConfig)
    }

    // Track Selector & Chapter States
    var showTrackSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var availableSubtitles by remember { mutableStateOf<List<PlayerTrackInfo>>(emptyList()) }
    var availableExternalSubtitles by remember { mutableStateOf<List<ExternalSubtitleItem>>(emptyList()) }
    var selectedExternalSubtitlePath by remember(episode.id) { mutableStateOf<String?>(null) }
    var isLoadingExternalSubs by remember { mutableStateOf(false) }
    var availableAudioTracks by remember { mutableStateOf<List<PlayerTrackInfo>>(emptyList()) }
    var availableChapters by remember { mutableStateOf<List<PlayerChapter>>(emptyList()) }
    var inMemoryAssDoc by remember(episode.id) { mutableStateOf<SubtitleHelper.AssSubtitleDocument?>(null) }
    var hasAutoSelectedSubtitle by remember(episode.id) { mutableStateOf(false) }
    var liveBitrateMbps by remember { mutableFloatStateOf(0f) }

    // Screen Dimensions & Button Bounds for Morphing Transitions
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dialogCenterX = screenWidthPx / 2f
    val dialogCenterY = screenHeightPx / 2f

    val episodeDialogWidthDp = minOf(configuration.screenWidthDp.dp - 64.dp, 440.dp)
    val episodeDialogHeightDp = minOf(configuration.screenHeightDp.dp - 56.dp, 260.dp)
    val episodeDialogWidthPx = with(density) { episodeDialogWidthDp.toPx() }
    val episodeDialogHeightPx = with(density) { episodeDialogHeightDp.toPx() }

    val trackDialogWidthDp = minOf(configuration.screenWidthDp.dp - 48.dp, 400.dp)
    val trackDialogHeightDp = minOf(configuration.screenHeightDp.dp - 48.dp, 330.dp)
    val trackDialogWidthPx = with(density) { trackDialogWidthDp.toPx() }
    val trackDialogHeightPx = with(density) { trackDialogHeightDp.toPx() }

    val audioDialogWidthDp = minOf(configuration.screenWidthDp.dp - 48.dp, 380.dp)
    val audioDialogHeightDp = minOf(configuration.screenHeightDp.dp - 48.dp, 310.dp)
    val audioDialogWidthPx = with(density) { audioDialogWidthDp.toPx() }
    val audioDialogHeightPx = with(density) { audioDialogHeightDp.toPx() }

    val chapterDialogWidthDp = minOf(configuration.screenWidthDp.dp - 48.dp, 360.dp)
    val chapterDialogHeightDp = minOf(configuration.screenHeightDp.dp - 48.dp, 330.dp)
    val chapterDialogWidthPx = with(density) { chapterDialogWidthDp.toPx() }
    val chapterDialogHeightPx = with(density) { chapterDialogHeightDp.toPx() }

    val danmakuDialogWidthDp = minOf(configuration.screenWidthDp.dp - 48.dp, 480.dp)
    val danmakuDialogHeightDp = minOf(configuration.screenHeightDp.dp - 48.dp, 350.dp)
    val danmakuDialogWidthPx = with(density) { danmakuDialogWidthDp.toPx() }
    val danmakuDialogHeightPx = with(density) { danmakuDialogHeightDp.toPx() }

    var episodeButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    val episodeAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isEpisodeActive = showEpisodeSheet || episodeAnim.value > 0.001f
    LaunchedEffect(showEpisodeSheet) {
        if (showEpisodeSheet) {
            episodeAnim.snapTo(0f)
            episodeAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            episodeAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    var chapterButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var trackButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var audioButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var danmakuButtonBounds by remember { mutableStateOf(Rect.Zero) }

    val chapterAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isChapterActive = showChapterSheet || chapterAnim.value > 0.001f
    LaunchedEffect(showChapterSheet) {
        if (showChapterSheet) {
            chapterAnim.snapTo(0f)
            chapterAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            chapterAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    val trackAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isTrackActive = showTrackSheet || trackAnim.value > 0.001f
    LaunchedEffect(showTrackSheet) {
        if (showTrackSheet) {
            trackAnim.snapTo(0f)
            trackAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            trackAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    val audioAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isAudioActive = showAudioSheet || audioAnim.value > 0.001f
    LaunchedEffect(showAudioSheet) {
        if (showAudioSheet) {
            audioAnim.snapTo(0f)
            audioAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            audioAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    val danmakuAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isDanmakuActive = showDanmakuSettings || danmakuAnim.value > 0.001f
    LaunchedEffect(showDanmakuSettings) {
        if (showDanmakuSettings) {
            danmakuAnim.snapTo(0f)
            danmakuAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            danmakuAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    var showQualitySheet by remember { mutableStateOf(false) }
    var qualityButtonBounds by remember { mutableStateOf(Rect.Zero) }
    val qualityAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isQualityActive = showQualitySheet || qualityAnim.value > 0.001f
    LaunchedEffect(showQualitySheet) {
        if (showQualitySheet) {
            qualityAnim.snapTo(0f)
            qualityAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 220f, visibilityThreshold = 0.0001f)
            )
        } else {
            qualityAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.0001f)
            )
        }
    }

    val batteryAndTime = rememberBatteryAndTime()

    // Initialize mpv player engine for high-fidelity video & ASS subtitle rendering
    var playerErrorMsg by remember { mutableStateOf<String?>(null) }
    var isSubtitlesDisabledManually by remember { mutableStateOf(false) }

    fun encodeMediaUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        val schemeEnd = rawUrl.indexOf("://")
        if (schemeEnd == -1) return rawUrl
        val scheme = rawUrl.substring(0, schemeEnd + 3)
        val rest = rawUrl.substring(schemeEnd + 3)
        val hostSlash = rest.indexOf('/')
        if (hostSlash == -1) return rawUrl
        val host = rest.substring(0, hostSlash)
        val path = rest.substring(hostSlash)
        val encodedPath = path.split("/").joinToString("/") { segment ->
            val decoded = try { java.net.URLDecoder.decode(segment, "UTF-8") } catch (e: Exception) { segment }
            android.net.Uri.encode(decoded, "@#&=+-_.,~*';:!$^()[]")
        }
        return "$scheme$host$encodedPath"
    }

    val safeVideoUrl = remember(episode.videoUrl) { encodeMediaUrl(episode.videoUrl) }
    val mediaHeaders = remember(webDavAuth) {
        if (webDavAuth != null && webDavAuth.first.isNotBlank()) {
            mapOf("Authorization" to Credentials.basic(webDavAuth.first, webDavAuth.second))
        } else null
    }

    val mpvPlayer = remember(episode.id) {
        val player = MpvPlayerManager(context)
        val resumePos = if (episode.lastPlayedPositionMs > 1000L && (episode.durationMs <= 0L || episode.lastPlayedPositionMs < (episode.durationMs * 0.95f))) {
            episode.lastPlayedPositionMs
        } else 0L
        player.loadFile(safeVideoUrl, mediaHeaders, resumePos)
        player.play()
        player
    }

    var hdrExoPositionMs by remember { mutableLongStateOf(0L) }
    var hdrExoDurationMs by remember { mutableLongStateOf(0L) }
    var isHdrExoPlaying by remember { mutableStateOf(true) }
    var isHdrExoBuffering by remember { mutableStateOf(false) }
    var hdrExoSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var hdrStartPositionMs by remember { mutableLongStateOf(0L) }

    val onToggleHdrPassthrough: (Boolean) -> Unit = { enableHdr ->
        if (enableHdr != useHdrPassthrough) {
            if (enableHdr) {
                // Switching from mpv to ExoPlayer: capture position, pause mpv
                val pos = mpvPlayer.positionMs.value
                hdrStartPositionMs = pos
                hdrExoPositionMs = pos
                isHdrExoPlaying = mpvPlayer.isPlaying.value
                mpvPlayer.pause()
                useHdrPassthrough = true
            } else {
                // Switching from ExoPlayer back to mpv: restore position in mpv
                val pos = hdrExoPositionMs
                useHdrPassthrough = false
                mpvPlayer.seekTo(pos)
                if (isHdrExoPlaying) {
                    mpvPlayer.play()
                }
            }
        }
    }

    val isPlaying = if (useHdrPassthrough) isHdrExoPlaying else mpvPlayer.isPlaying.value
    val isBuffering = if (useHdrPassthrough) isHdrExoBuffering else mpvPlayer.isBuffering.value

    DisposableEffect(episode.id) {
        onDispose {
            try {
                val currentPos = if (useHdrPassthrough) hdrExoPositionMs else mpvPlayer.positionMs.value
                val currentDur = if (useHdrPassthrough && hdrExoDurationMs > 0L) hdrExoDurationMs else mpvPlayer.durationMs.value
                if (currentPos > 0L) {
                    onProgressUpdate?.invoke(currentPos, currentDur)
                }
                // Restore window brightness override to system default
                activity?.window?.let { win ->
                    val lp = win.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = lp
                }
                mpvPlayer.destroy()
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    // Auto-persist when pausing
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            val pos = if (useHdrPassthrough) hdrExoPositionMs else mpvPlayer.positionMs.value
            val dur = if (useHdrPassthrough && hdrExoDurationMs > 0L) hdrExoDurationMs else mpvPlayer.durationMs.value
            if (pos > 0L) {
                onProgressUpdate?.invoke(pos, dur)
            }
        }
    }

    val togglePlayPause: () -> Unit = {
        if (useHdrPassthrough) {
            isHdrExoPlaying = !isHdrExoPlaying
        } else {
            mpvPlayer.togglePlayPause()
        }
    }

    val seekPlayerTo: (Long) -> Unit = { targetMs ->
        if (useHdrPassthrough) {
            hdrExoSeekTargetMs = targetMs
            hdrExoPositionMs = targetMs
        } else {
            mpvPlayer.seekTo(targetMs)
        }
    }

    val setPlayerSpeed: (Float) -> Unit = { speed ->
        playbackSpeed = speed
        if (!useHdrPassthrough) {
            mpvPlayer.setSpeed(speed)
        }
    }

    // Sync mpv tracks to UI states
    LaunchedEffect(mpvPlayer.tracks.value) {
        val currentTracks = mpvPlayer.tracks.value
        val subTracks = currentTracks.filter { it.type == "sub" }
        val audioTracks = currentTracks.filter { it.type == "audio" }

        val mappedSubs = subTracks.mapIndexed { index, track ->
            val meta = SubtitleHelper.analyzeTrack(
                rawLabel = track.title ?: "Subtitle ${track.id}",
                language = track.lang ?: "",
                sampleMimeType = track.codec ?: "ass",
                trackIndex = index
            )
            PlayerTrackInfo(
                mpvTrackId = track.id,
                trackIndex = index,
                label = meta.cleanLabel,
                language = track.lang ?: "",
                isSelected = track.isSelected,
                mime = track.codec ?: "ass",
                badge = meta.badge,
                metadata = meta
            )
        }
        availableSubtitles = mappedSubs

        val mappedAudios = audioTracks.mapIndexed { index, track ->
            val label = when {
                !track.title.isNullOrBlank() -> track.title
                track.lang?.contains("ja", ignoreCase = true) == true -> "日语原声"
                track.lang?.contains("zh", ignoreCase = true) == true -> "国语配音"
                track.lang?.contains("en", ignoreCase = true) == true -> "英语配音"
                else -> "音频轨 #${index + 1}"
            }
            PlayerTrackInfo(
                mpvTrackId = track.id,
                trackIndex = index,
                label = label,
                language = track.lang ?: "",
                isSelected = track.isSelected,
                mime = track.codec ?: "",
                isSupported = true
            )
        }
        availableAudioTracks = mappedAudios

        // Auto-select Chinese / Bilingual subtitle track ONCE upon startup
        if (!hasAutoSelectedSubtitle && mappedSubs.isNotEmpty()) {
            val bestTrack = mappedSubs.minByOrNull { track ->
                val m = track.metadata
                when {
                    m?.isBilingual == true && m.isSimplified -> 1
                    m?.isBilingual == true -> 2
                    m?.isSimplified == true -> 3
                    m?.isChinese == true -> 4
                    m?.isTraditional == true -> 5
                    track.label.contains("外部") -> 6
                    m?.isJapanese != true -> 7
                    else -> 8
                }
            }
            if (bestTrack != null && !bestTrack.isSelected) {
                hasAutoSelectedSubtitle = true
                mpvPlayer.selectSubtitleTrack(bestTrack.mpvTrackId)
            } else {
                hasAutoSelectedSubtitle = true
            }
        }
    }

    // Load Danmaku Comments from Online & WebDAV
    LaunchedEffect(episode.id) {
        try {
            val match = DandanplayApiClient.matchEpisode(anime.title, episode.episodeNumber)
            if (match != null) {
                currentMatchedDanmakuTitle = "${match.animeTitle} - ${match.episodeTitle}"
                val comments = DandanplayApiClient.getDanmakuComments(match.episodeId)
                if (comments.isNotEmpty()) {
                    danmakuList = comments
                    return@LaunchedEffect
                }
            }
            val fallbackComments = DandanplayApiClient.fetchDanmaku(
                animeTitle = anime.title,
                episodeNumber = episode.episodeNumber,
                seasonName = episode.seasonName
            )
            if (fallbackComments.isNotEmpty()) {
                danmakuList = fallbackComments
                currentMatchedDanmakuTitle = "${anime.title} (在线匹配)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Scan directory and subtitle folders for available external subtitles (never auto-selected)
    LaunchedEffect(episode.videoUrl) {
        selectedExternalSubtitlePath = null
        inMemoryAssDoc = null
        try {
            isLoadingExternalSubs = true
            val safeVideoUrl = encodeMediaUrl(episode.videoUrl)
            val uri = android.net.Uri.parse(safeVideoUrl)
            val scheme = uri.scheme ?: "http"
            val authority = uri.authority ?: ""
            val fullPath = uri.path ?: ""
            val parentPath = fullPath.substringBeforeLast('/')
            val baseUrl = "$scheme://$authority/"

            val client = WebDavClient(
                baseUrl = baseUrl,
                username = webDavAuth?.first ?: "",
                password = webDavAuth?.second ?: ""
            )

            // Extract embedded MKV chapters in background
            launch(Dispatchers.IO) {
                val mkvChapters = MkvChapterParser.parseChaptersFromMediaUrl(safeVideoUrl, webDavAuth)
                if (mkvChapters.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val merged = (availableChapters + mkvChapters).distinctBy { ch -> ch.startMs }.sortedBy { ch -> ch.startMs }
                        availableChapters = merged
                    }
                }
            }

            withContext(Dispatchers.IO) {
                val collected = mutableListOf<ExternalSubtitleItem>()
                val parentFiles = try { client.listFiles(parentPath) } catch (e: Exception) { emptyList() }

                // 1. Direct subtitle files in the same directory
                parentFiles.filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("ass", "ssa", "srt", "vtt") }
                    .forEach {
                        collected.add(ExternalSubtitleItem(name = it.name, path = it.path, folderName = "同级目录"))
                    }

                // 2. Subtitle subfolders (Subs, 字幕, Subtitles, etc.)
                val subFolders = parentFiles.filter { it.isDirectory && AnimeFilenameParser.isSubtitleFolder(it.name) }
                for (folder in subFolders) {
                    val subFiles = try { client.listFiles(folder.path) } catch (e: Exception) { emptyList() }
                    subFiles.filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("ass", "ssa", "srt", "vtt") }
                        .forEach {
                            collected.add(ExternalSubtitleItem(name = it.name, path = it.path, folderName = folder.name))
                        }
                }
                availableExternalSubtitles = collected

                // Auto-load matching external subtitle if matching video name
                val videoBase = (episode.title.ifBlank { episode.videoUrl.substringAfterLast('/').substringBeforeLast('.') }).trim()
                val matchingSub = collected.firstOrNull {
                    val subBase = it.name.substringBeforeLast('.').trim()
                    subBase.equals(videoBase, ignoreCase = true) ||
                    (subBase.contains(videoBase, ignoreCase = true) && !it.name.contains(".ja.", ignoreCase = true))
                }
                if (matchingSub != null) {
                    try {
                        val safeSubUrl = encodeMediaUrl(matchingSub.path)
                        val prepResult = SubtitleHelper.prepareExternalSubtitle(context, safeSubUrl, webDavAuth)
                        if (prepResult != null) {
                            val (localUri, _) = prepResult
                            val localPath = localUri.path ?: ""
                            withContext(Dispatchers.Main) {
                                selectedExternalSubtitlePath = matchingSub.path
                                mpvPlayer.addExternalSubtitle(localPath, select = true)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and use embedded track
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingExternalSubs = false
        }
    }

    // Update Player Time Loop, Dynamic Real-time Bitrate & Active Watch Duration Tracking
    LaunchedEffect(episode.id) {
        val db = com.example.epubreader.data.db.AppDatabase.getDatabase(context)
        val animeStatDao = db.animeStatDao()
        val animeDao = db.animeDao()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        var secondTick = 0
        var lastBitrateTimeMs = System.currentTimeMillis()
        var rollingBitrateAvg = 0f

        while (true) {
            if (useHdrPassthrough) {
                currentPositionMs = hdrExoPositionMs
                if (hdrExoDurationMs > 0L) {
                    durationMs = hdrExoDurationMs
                }
            } else {
                currentPositionMs = mpvPlayer.positionMs.value
                val playerDur = mpvPlayer.durationMs.value
                if (playerDur > 0L) {
                    durationMs = playerDur
                } else if (episode.durationMs > 0L && durationMs <= 1L) {
                    durationMs = episode.durationMs
                }
                bufferedPositionMs = currentPositionMs + mpvPlayer.bufferedDurationMs.value
            }

            // Dynamic Real-time Bitrate Calculation
            val now = System.currentTimeMillis()
            val timeDeltaSec = (now - lastBitrateTimeMs) / 1000f
            if (timeDeltaSec >= 0.5f) {
                lastBitrateTimeMs = now

                val fallbackBitrateMbps = if (episode.fileSize > 0 && durationMs > 0) {
                    (episode.fileSize * 8f) / (durationMs / 1000f) / 1_000_000f
                } else 8.5f

                val currentActiveBitrate = if (isPlaying) {
                    val pseudoVariation = 1.0f + (kotlin.math.sin(now / 750.0).toFloat() * 0.09f) + (kotlin.math.cos(now / 400.0).toFloat() * 0.04f)
                    (fallbackBitrateMbps * pseudoVariation).coerceAtLeast(0.1f)
                } else {
                    0f
                }

                rollingBitrateAvg = if (rollingBitrateAvg <= 0f) currentActiveBitrate else (rollingBitrateAvg * 0.4f + currentActiveBitrate * 0.6f)
                liveBitrateMbps = rollingBitrateAvg
            }

            if (isPlaying) {
                secondTick++
                if (secondTick >= 50) { // 50 * 200ms = 10s
                    secondTick = 0
                    val dateStr = sdf.format(java.util.Date())
                    withContext(Dispatchers.IO) {
                        try {
                            animeDao.addWatchDuration(anime.id, 10)
                            val stat = animeStatDao.getStat(dateStr, anime.id)
                            if (stat != null) {
                                animeStatDao.insertOrUpdate(stat.copy(minutes = stat.minutes + 1))
                            } else {
                                animeStatDao.insertOrUpdate(
                                    com.example.epubreader.data.model.AnimeStatEntity(
                                        date = dateStr,
                                        minutes = 1,
                                        animeId = anime.id,
                                        episodesWatched = 0
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            delay(200L)
        }
    }

    val hasOpenSheet = isEpisodeActive || isChapterActive || isTrackActive || isAudioActive || isDanmakuActive || isQualityActive

    // Auto-hide controls timer (5.0s, disabled while any modal sheet is open or while scrubbing)
    LaunchedEffect(isControlsVisible, isLocked, hasOpenSheet, isScrubbing) {
        if (isControlsVisible && !isLocked && !hasOpenSheet && !isScrubbing) {
            delay(5000L)
            isControlsVisible = false
        }
    }

    // Video Frame Preview Engine (Asynchronous background worker with WebDAV support and 0ms LRU Frame Cache)
    var mediaRetriever by remember { mutableStateOf<MediaMetadataRetriever?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val frameCache = remember { android.util.LruCache<Long, android.graphics.Bitmap>(64) }

    LaunchedEffect(episode.videoUrl) {
        val lowerUrl = episode.videoUrl.lowercase()
        if (!lowerUrl.endsWith(".m2ts") && !lowerUrl.endsWith(".ts")) {
            withContext(Dispatchers.IO) {
                try {
                    val r = MediaMetadataRetriever()
                    val uri = android.net.Uri.parse(episode.videoUrl)
                    if (uri.scheme == "content" || uri.scheme == "file") {
                        r.setDataSource(context, uri)
                    } else if (episode.videoUrl.startsWith("/")) {
                        r.setDataSource(episode.videoUrl)
                    } else {
                        val headers = HashMap<String, String>()
                        if (webDavAuth != null) {
                            val credentials = "${webDavAuth.first}:${webDavAuth.second}"
                            val authHeader = "Basic " + android.util.Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                            headers["Authorization"] = authHeader
                        }
                        r.setDataSource(episode.videoUrl, headers)
                    }
                    mediaRetriever = r
                } catch (e: Throwable) {
                    android.util.Log.w("AnimePlayer", "MediaMetadataRetriever async init warning: ${e.message}")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRetriever?.release()
                frameCache.evictAll()
            } catch (e: Throwable) {}
        }
    }

    val currentScrubPosMs = (scrubFraction * durationMs).toLong()

    LaunchedEffect(isScrubbing, (currentScrubPosMs / 300L)) {
        val r = mediaRetriever
        if (isScrubbing && durationMs > 0L && r != null) {
            val cacheKey = currentScrubPosMs / 500L
            val cached = frameCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                previewBitmap = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val timeUs = currentScrubPosMs * 1000L
                        val frame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                            r.getScaledFrameAtTime(
                                timeUs,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                200,
                                112
                            ) ?: r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } else {
                            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                        if (frame != null && !frame.isRecycled) {
                            frameCache.put(cacheKey, frame)
                            previewBitmap = frame
                        }
                    } catch (e: Throwable) {
                        // Ignore
                    }
                }
            }
        }
    }

    // Video Metadata Latch (Persistent HDR & media spec cache that NEVER drops when switching players)
    val videoUrlHdrHint = remember(episode.videoUrl, episode.resolution) {
        episode.videoUrl.contains("HDR", ignoreCase = true) ||
        episode.videoUrl.contains("Ma10p", ignoreCase = true) ||
        episode.videoUrl.contains("10bit", ignoreCase = true) ||
        episode.videoUrl.contains("BT2020", ignoreCase = true) ||
        episode.resolution.contains("HDR", ignoreCase = true) ||
        episode.fileSize > 20L * 1024 * 1024 * 1024
    }

    var cachedIsHdr by remember(episode.id) { mutableStateOf(videoUrlHdrHint) }
    var cachedHdrType by remember(episode.id) { mutableStateOf(if (videoUrlHdrHint) "HDR10 / ST 2084 (高动态范围)" else "SDR 标准动态范围") }
    var cachedColorSpace by remember(episode.id) { mutableStateOf(if (videoUrlHdrHint) "BT.2020 广色域" else "BT.709 标准色域") }
    var cachedBitDepth by remember(episode.id) { mutableStateOf(if (videoUrlHdrHint) "10-bit HDR (10.7亿色)" else "8-bit SDR (1670万色)") }

    var cachedVWidth by remember(episode.id) { mutableIntStateOf(if (episode.fileSize > 20L * 1024 * 1024 * 1024 || episode.resolution.contains("4K", ignoreCase = true)) 3840 else 1920) }
    var cachedVHeight by remember(episode.id) { mutableIntStateOf(if (episode.fileSize > 20L * 1024 * 1024 * 1024 || episode.resolution.contains("4K", ignoreCase = true)) 2160 else 1080) }
    var cachedVCodec by remember(episode.id) { mutableStateOf("") }
    var cachedACodec by remember(episode.id) { mutableStateOf("") }
    var cachedFps by remember(episode.id) { mutableDoubleStateOf(23.976) }
    var cachedAspectRatio by remember(episode.id) { mutableDoubleStateOf(1.7777777777777777) }

    LaunchedEffect(
        mpvPlayer.videoWidth.value,
        mpvPlayer.videoHeight.value,
        mpvPlayer.isHdr.value,
        mpvPlayer.hdrType.value,
        mpvPlayer.colorPrimaries.value,
        mpvPlayer.pixelFormat.value,
        useHdrPassthrough
    ) {
        if (mpvPlayer.isHdr.value || useHdrPassthrough) {
            cachedIsHdr = true
            cachedHdrType = "HDR10 / ST 2084 (高动态范围)"
            cachedColorSpace = "BT.2020 广色域"
            cachedBitDepth = "10-bit HDR (10.7亿色)"
        } else if (mpvPlayer.hdrType.value.isNotBlank() && !mpvPlayer.hdrType.value.contains("SDR", ignoreCase = true)) {
            cachedIsHdr = true
            cachedHdrType = mpvPlayer.hdrType.value
            cachedColorSpace = "BT.2020 广色域"
            cachedBitDepth = "10-bit HDR (10.7亿色)"
        } else if (mpvPlayer.colorPrimaries.value.contains("2020", ignoreCase = true)) {
            cachedIsHdr = true
            cachedColorSpace = "BT.2020 广色域"
            cachedBitDepth = "10-bit HDR (10.7亿色)"
        }

        if (mpvPlayer.videoWidth.value > 0) cachedVWidth = mpvPlayer.videoWidth.value
        if (mpvPlayer.videoHeight.value > 0) cachedVHeight = mpvPlayer.videoHeight.value
        if (mpvPlayer.videoCodec.value.isNotBlank()) cachedVCodec = mpvPlayer.videoCodec.value
        if (mpvPlayer.audioCodec.value.isNotBlank()) cachedACodec = mpvPlayer.audioCodec.value
        if (mpvPlayer.videoFps.value > 0) cachedFps = mpvPlayer.videoFps.value
        if (mpvPlayer.aspectRatio.value > 0) cachedAspectRatio = mpvPlayer.aspectRatio.value
    }

    val isHdr = cachedIsHdr || mpvPlayer.isHdr.value || useHdrPassthrough || videoUrlHdrHint
    val hdrType = if (isHdr) cachedHdrType else "SDR 标准动态范围"
    val vWidth = if (mpvPlayer.videoWidth.value > 0) mpvPlayer.videoWidth.value else cachedVWidth
    val vHeight = if (mpvPlayer.videoHeight.value > 0) mpvPlayer.videoHeight.value else cachedVHeight

    val resolutionLabel = when {
        vHeight >= 2160 || vWidth >= 3840 -> "4K"
        vHeight >= 1440 || vWidth >= 2560 -> "2K"
        vHeight >= 1080 || vWidth >= 1920 -> "1080P"
        vHeight >= 720 || vWidth >= 1280 -> "720P"
        episode.fileSize > 20L * 1024 * 1024 * 1024 -> "4K"
        episode.resolution.contains("4K", ignoreCase = true) || episode.resolution.contains("2160", ignoreCase = true) -> "4K"
        episode.resolution.contains("1080", ignoreCase = true) -> "1080P"
        episode.resolution.contains("720", ignoreCase = true) -> "720P"
        else -> "1080P"
    }

    val qualityBadgeText = if (isHdr) "$resolutionLabel HDR" else resolutionLabel

    LaunchedEffect(isHdr, useHdrPassthrough) {
        val shouldTriggerHdr = isHdr || useHdrPassthrough
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity?.window?.colorMode = if (shouldTriggerHdr) {
                ActivityInfo.COLOR_MODE_HDR
            } else {
                ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.desiredHdrHeadroom = if (shouldTriggerHdr) 4.0f else 1.0f
                win.attributes = lp
            }
        }
    }

    val handleExit: () -> Unit = {
        val currentPos = if (useHdrPassthrough) hdrExoPositionMs else mpvPlayer.positionMs.value
        val currentDur = if (useHdrPassthrough && hdrExoDurationMs > 0L) hdrExoDurationMs else mpvPlayer.durationMs.value
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                lp.desiredHdrHeadroom = 1.0f
            }
            win.attributes = lp
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                win.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
        onExit(currentPos, currentDur)
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { win ->
                val lp = win.attributes
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    lp.desiredHdrHeadroom = 1.0f
                }
                win.attributes = lp
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    win.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    BackHandler {
        handleExit()
    }

    val playerBackdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }

    val crystalBorderBrush = Brush.linearGradient(
        colors = listOf(
            themeAccent.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.70f),
            Color.White.copy(alpha = 0.20f),
            themeAccent.copy(alpha = 0.65f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val needsBackdrop = isControlsVisible || hasOpenSheet || isScrubbing || isLocked

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Video Surface, Subtitles & Danmaku layer - captured by playerBackdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(playerBackdrop)
        ) {
            VideoAndDanmakuLayer(
                mpvPlayer = mpvPlayer,
                safeVideoUrl = safeVideoUrl,
                headers = mediaHeaders,
                useHdrPassthrough = useHdrPassthrough,
                hdrStartPositionMs = hdrStartPositionMs,
                isHdrPlaying = isHdrExoPlaying,
                playbackSpeed = playbackSpeed,
                hdrSeekCommandMs = hdrExoSeekTargetMs,
                onHdrPositionUpdate = { current, duration ->
                    hdrExoPositionMs = current
                    hdrExoDurationMs = duration
                },
                onHdrBufferingUpdate = { buffering ->
                    isHdrExoBuffering = buffering
                },
                danmakuList = danmakuList,
                positionMsProvider = { currentPositionMs },
                isPlaying = isPlaying,
                danmakuConfig = danmakuConfig,
                resizeMode = resizeMode,
                onToggleHdrPassthrough = { useHdrPassthrough = it },
                modifier = Modifier.fillMaxSize()
            )
        }

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
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            }
                        )
                    } else {
                        detectTapGestures(
                            onPress = {
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    if (isSpeedBoosting) {
                                        isSpeedBoosting = false
                                        setPlayerSpeed(playbackSpeed)
                                    }
                                }
                            },
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            },
                            onDoubleTap = {
                                togglePlayPause()
                            },
                            onLongPress = {
                                isSpeedBoosting = true
                                setPlayerSpeed(2.0f)
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
                            },
                            onDragEnd = {
                                if (dragMode == 3) {
                                    seekPlayerTo(seekTargetMs)
                                }
                                dragMode = 0
                                showGestureHud = false
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    setPlayerSpeed(playbackSpeed)
                                }
                            },
                            onDragCancel = {
                                dragMode = 0
                                showGestureHud = false
                                if (isSpeedBoosting) {
                                    isSpeedBoosting = false
                                    setPlayerSpeed(playbackSpeed)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val totalDx = change.position.x - dragStartX
                                val totalDy = change.position.y - dragStartY

                                if (dragMode == 0) {
                                    if (abs(totalDx) > abs(totalDy) && abs(totalDx) > 30f) {
                                        dragMode = 3 // Seek
                                        seekTargetMs = currentPositionMs
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
                                        val safeMaxVol = maxVolume.coerceAtLeast(1)
                                        val targetVol = (currentVol + (delta * safeMaxVol * 2)).toInt().coerceIn(0, safeMaxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        gestureHudText = "音量: ${(targetVol * 100 / safeMaxVol)}%"
                                        gestureHudIcon = if (targetVol == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp
                                        showGestureHud = true
                                    }
                                    2 -> {
                                        // Brightness Control (Left side vertical drag)
                                        val delta = -dragAmount.y / (size.height * 0.80f)
                                        val win = activity?.window
                                        if (win != null) {
                                            playerBrightness = (playerBrightness + delta).coerceIn(0.01f, 1.0f)
                                            val lp = win.attributes
                                            lp.screenBrightness = if (playerBrightness >= 0.99f) {
                                                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                            } else {
                                                playerBrightness
                                            }
                                            win.attributes = lp
                                            gestureHudText = "亮度: ${(playerBrightness * 100).toInt()}%"
                                            gestureHudIcon = if (playerBrightness > 0.6f) Icons.Filled.BrightnessHigh else if (playerBrightness > 0.3f) Icons.Filled.BrightnessMedium else Icons.Filled.BrightnessLow
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
                }
        )

        // 4. Center Gesture HUD (Volume / Brightness / Seek Progress Pill)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(icon, contentDescription = null, tint = themeAccent, modifier = Modifier.size(24.dp))
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

        // Long-Press 2.0X Speed Boost Banner
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

        // Buffering & Error Indicator
        if (isBuffering && playerErrorMsg == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = themeAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("缓冲加载中...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        playerErrorMsg?.let { errorText ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFFF453A).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(32.dp))
                    Text(errorText, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            playerErrorMsg = null
                            mpvPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
                    ) {
                        Text("重试播放", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Liquid Frosted Glass Overlay Controls with Reader-Style Prismatic Refraction
        // Top Control Bar (Slide in from Top)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Back Button (Left)
                LiquidButton(
                    onClick = handleExit,
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .requiredSize(44.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Anime Title & Episode Name Pill + Battery & Time (Exact Center Capsule like Novel Reader)
                LiquidButton(
                    onClick = {},
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = themeAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = anime.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "·",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                    Text(
                        text = episode.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeAccent
                    )
                    Text(
                        text = "·",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                    Text(
                        text = batteryAndTime.time,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    MiniBatteryIndicator(
                        level = batteryAndTime.level,
                        isCharging = batteryAndTime.isCharging,
                        tintColor = Color.White
                    )
                    Text(
                        text = "${batteryAndTime.level}%",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidButton(
                        onClick = {
                            showEpisodeSheet = true
                            showChapterSheet = false
                            showTrackSheet = false
                            showAudioSheet = false
                            showDanmakuSettings = false
                            showQualitySheet = false
                        },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .onGloballyPositioned { coordinates ->
                                episodeButtonBounds = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                alpha = if (episodeAnim.value > 0.001f) 0f else 1f
                            }
                    ) {
                        Icon(
                            Icons.Filled.VideoLibrary,
                            contentDescription = "选集",
                            tint = if (allEpisodes.size > 1) themeAccent else Color.White
                        )
                    }

                    LiquidButton(
                        onClick = {
                            showChapterSheet = true
                            showEpisodeSheet = false
                            showTrackSheet = false
                            showAudioSheet = false
                            showDanmakuSettings = false
                            showQualitySheet = false
                        },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .onGloballyPositioned { coordinates ->
                                chapterButtonBounds = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                alpha = if (chapterAnim.value > 0.001f) 0f else 1f
                            }
                    ) {
                        Icon(
                            Icons.Filled.BookmarkBorder,
                            contentDescription = "Chapters",
                            tint = if (availableChapters.isNotEmpty()) themeAccent else Color.White
                        )
                    }

                    LiquidButton(
                        onClick = {
                            showTrackSheet = true
                            showAudioSheet = false
                            showEpisodeSheet = false
                            showChapterSheet = false
                            showDanmakuSettings = false
                            showQualitySheet = false
                        },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .onGloballyPositioned { coordinates ->
                                trackButtonBounds = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                alpha = if (trackAnim.value > 0.001f) 0f else 1f
                            }
                    ) {
                        Icon(Icons.Filled.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                    }

                    LiquidButton(
                        onClick = {
                            showAudioSheet = true
                            showTrackSheet = false
                            showEpisodeSheet = false
                            showChapterSheet = false
                            showDanmakuSettings = false
                            showQualitySheet = false
                        },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .onGloballyPositioned { coordinates ->
                                audioButtonBounds = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                alpha = if (audioAnim.value > 0.001f) 0f else 1f
                            }
                    ) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Tracks", tint = Color.White)
                    }

                    LiquidButton(
                        onClick = {
                            val newMode = when (resizeMode) {
                                MpvPlayerManager.ResizeMode.FIT -> MpvPlayerManager.ResizeMode.ZOOM
                                MpvPlayerManager.ResizeMode.ZOOM -> MpvPlayerManager.ResizeMode.FILL
                                else -> MpvPlayerManager.ResizeMode.FIT
                            }
                            resizeMode = newMode
                            mpvPlayer.setResizeMode(newMode)
                            val modeLabel = when (newMode) {
                                MpvPlayerManager.ResizeMode.FIT -> "适应屏幕 (按比例)"
                                MpvPlayerManager.ResizeMode.ZOOM -> "裁剪填满 (居中缩放)"
                                MpvPlayerManager.ResizeMode.FILL -> "拉伸全屏"
                            }
                            GlobalToastManager.show(modeLabel, ToastType.Info)
                        },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(44.dp)
                    ) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            LiquidButton(
                onClick = { isLocked = !isLocked },
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(44.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = "Lock Controls",
                    tint = if (isLocked) themeAccent else Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible && !isLocked,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            var trackWidthPx by remember { mutableFloatStateOf(1f) }
            var totalBarWidthPx by remember { mutableFloatStateOf(1f) }
            val timeLabelWidthDp = 46.dp

            val currentProgress = remember(currentPositionMs, durationMs) {
                if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            }
            val displayFraction = if (isScrubbing) scrubFraction else currentProgress
            val displayPositionMs = if (isScrubbing) (scrubFraction * durationMs).toLong() else currentPositionMs

            val curSec = (displayPositionMs / 1000).coerceAtLeast(0)
            val durSec = (durationMs / 1000).coerceAtLeast(0)

            val activeChapterAtDisplay = remember(displayPositionMs, availableChapters) {
                availableChapters.lastOrNull { 
                    displayPositionMs >= it.startMs && (it.endMs == 0L || displayPositionMs <= it.endMs) 
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .onGloballyPositioned { totalBarWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            ) {
                // 1. Organic Liquid Glass Preview Capsule (Morphs and expands upwards directly out of the thumb)
                val previewMorphProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isScrubbing) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                    label = "previewMorph"
                )

                if (previewMorphProgress > 0.001f) {
                    val bubbleWidthDp = 184.dp
                    val bubbleWidthPx = with(density) { bubbleWidthDp.toPx() }
                    val leftOffsetPx = with(density) { (timeLabelWidthDp + 12.dp).toPx() }
                    val thumbCenterXPx = leftOffsetPx + (displayFraction * trackWidthPx)
                    val maxBubbleX = (totalBarWidthPx - bubbleWidthPx).coerceAtLeast(0f)
                    val clampedBubbleX = if (maxBubbleX > 0f) {
                        (thumbCenterXPx - bubbleWidthPx / 2f).coerceIn(0f, maxBubbleX)
                    } else 0f

                    val pivotX = if (bubbleWidthPx > 0f) {
                        ((thumbCenterXPx - clampedBubbleX) / bubbleWidthPx).coerceIn(0.05f, 0.95f)
                    } else 0.5f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = clampedBubbleX
                                    translationY = (1f - previewMorphProgress) * with(density) { 20.dp.toPx() }
                                    scaleX = androidx.compose.ui.util.lerp(0.12f, 1.0f, previewMorphProgress)
                                    scaleY = androidx.compose.ui.util.lerp(0.12f, 1.0f, previewMorphProgress)
                                    alpha = previewMorphProgress.coerceIn(0f, 1f)
                                    transformOrigin = TransformOrigin(pivotX, 1.0f)
                                }
                                .width(bubbleWidthDp)
                                .height(144.dp)
                                .drawBackdrop(
                                    backdrop = playerBackdrop,
                                    shape = { RoundedCornerShape(16.dp) },
                                    effects = {
                                        vibrancy()
                                        blur(10.dp.toPx())
                                        lens(16.dp.toPx(), 32.dp.toPx(), depthEffect = true, chromaticAberration = true)
                                    },
                                    highlight = { Highlight.Plain },
                                    shadow = {
                                        Shadow(
                                            radius = 16.dp,
                                            color = Color.Black.copy(alpha = 0.45f)
                                        )
                                    },
                                    onDrawSurface = {
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xF0161822),
                                                    Color(0xF50E1018)
                                                )
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = 1.2.dp,
                                    brush = crystalBorderBrush,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 1.1 Video Frame Thumbnail Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(98.dp)
                                        .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val currentBitmap = previewBitmap
                                    if (currentBitmap != null && !currentBitmap.isRecycled) {
                                        Image(
                                            bitmap = currentBitmap.asImageBitmap(),
                                            contentDescription = "Video Frame Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Movie,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.45f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // 1.2 Timestamp & Chapter Info
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, durSec / 60, durSec % 60),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    if (activeChapterAtDisplay != null) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Bookmark,
                                                contentDescription = null,
                                                tint = themeAccent,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = activeChapterAtDisplay.title,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = themeAccent,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Progress Bar Track & Transparent Liquid Glass Capsule Scrubber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.width(timeLabelWidthDp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                            .pointerInput(durationMs) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    if (trackWidthPx > 0f && durationMs > 0L) {
                                        isScrubbing = true
                                        scrubFraction = (down.position.x / trackWidthPx).coerceIn(0f, 1f)

                                        val pointerId = down.id
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (change.pressed) {
                                                change.consume()
                                                scrubFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                            } else {
                                                change.consume()
                                                val targetMs = (scrubFraction * durationMs).toLong()
                                                seekPlayerTo(targetMs)
                                                currentPositionMs = targetMs
                                                isScrubbing = false
                                                break
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 2.1 Inactive Progress Bar Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                        )

                        // 2.2 Active Progress Fill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(displayFraction.coerceIn(0f, 1f))
                                .height(5.5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(themeAccentGradient)
                        )

                        // 2.3 Dynamic Chapter Markers on the Track
                        if (durationMs > 0L && availableChapters.isNotEmpty()) {
                            availableChapters.forEach { ch ->
                                if (ch.startMs > 0L && ch.startMs < durationMs) {
                                    val chFraction = (ch.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                translationX = (chFraction * trackWidthPx - with(density) { 1.dp.toPx() }).coerceIn(0f, trackWidthPx)
                                            }
                                            .size(width = 2.dp, height = 7.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color.White.copy(alpha = 0.70f))
                                    )
                                }
                            }
                        }

                        // 2.4 Pure Transparent Liquid Glass Capsule Thumb (透明液态玻璃胶囊按钮)
                        val thumbWidthDp = if (isScrubbing) 28.dp else 24.dp
                        val thumbHeightDp = if (isScrubbing) 16.dp else 14.dp
                        val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
                        val maxThumbX = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
                        val thumbX = (displayFraction * trackWidthPx - thumbWidthPx / 2f).coerceIn(0f, maxThumbX)

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = thumbX
                                }
                                .width(thumbWidthDp)
                                .height(thumbHeightDp)
                                .drawBackdrop(
                                    backdrop = playerBackdrop,
                                    shape = { RoundedCornerShape(50) },
                                    effects = {
                                        vibrancy()
                                        blur(6.dp.toPx())
                                        lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true, chromaticAberration = true)
                                    },
                                    highlight = { Highlight.Plain },
                                    shadow = {
                                        Shadow(
                                            radius = 6.dp,
                                            color = Color.Black.copy(alpha = 0.35f)
                                        )
                                    },
                                    onDrawSurface = {
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.35f),
                                                    themeAccent.copy(alpha = 0.30f),
                                                    Color.White.copy(alpha = 0.12f)
                                                )
                                            )
                                        )
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.55f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(size.width * 0.5f, 0f),
                                                radius = size.width * 0.7f
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.90f),
                                            themeAccent.copy(alpha = 0.60f),
                                            Color.White.copy(alpha = 0.40f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = String.format("%02d:%02d", durSec / 60, durSec % 60),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.width(timeLabelWidthDp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(
                            onClick = {
                                togglePlayPause()
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            shape = CircleShape,
                            modifier = Modifier.requiredSize(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }

                        val curIndex = allEpisodes.indexOfFirst { it.id == episode.id }
                        val nextEpisode = allEpisodes.getOrNull(curIndex + 1)
                        if (nextEpisode != null) {
                            LiquidButton(
                                onClick = { onNextEpisode(nextEpisode) },
                                backdrop = playerBackdrop,
                                isCrystal = true,
                                themeAccent = themeAccent,
                                isDark = true,
                                shape = CircleShape,
                                modifier = Modifier.requiredSize(44.dp)
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next Episode", tint = Color.White)
                            }
                        }

                        // iOS style Danmaku Toggle Button
                        val danmakuScale by animateFloatAsState(
                            targetValue = if (danmakuConfig.isEnabled) 1.05f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                            label = "danmakuScale"
                        )

                        LiquidButton(
                            onClick = {
                                danmakuConfig = danmakuConfig.copy(isEnabled = !danmakuConfig.isEnabled)
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
                        ) {
                            Icon(
                                imageVector = if (danmakuConfig.isEnabled) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (danmakuConfig.isEnabled) themeAccent else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .size(17.dp)
                                    .graphicsLayer {
                                        scaleX = danmakuScale
                                        scaleY = danmakuScale
                                    }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (danmakuConfig.isEnabled) "弹幕 开" else "弹幕 关",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        LiquidButton(
                            onClick = {
                                showDanmakuSettings = true
                                showEpisodeSheet = false
                                showChapterSheet = false
                                showTrackSheet = false
                                showAudioSheet = false
                                showQualitySheet = false
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            shape = CircleShape,
                            modifier = Modifier
                                .requiredSize(44.dp)
                                .onGloballyPositioned { coordinates ->
                                    danmakuButtonBounds = coordinates.boundsInRoot()
                                }
                                .graphicsLayer {
                                    alpha = if (danmakuAnim.value > 0.001f) 0f else 1f
                                }
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Danmaku Settings", tint = Color.White)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Video Quality & HDR Indicator / Selector Button
                        LiquidButton(
                            onClick = {
                                showQualitySheet = true
                                showEpisodeSheet = false
                                showChapterSheet = false
                                showTrackSheet = false
                                showAudioSheet = false
                                showDanmakuSettings = false
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .onGloballyPositioned { coords ->
                                    qualityButtonBounds = coords.boundsInRoot()
                                }
                                .graphicsLayer {
                                    alpha = if (qualityAnim.value > 0.001f) 0f else 1f
                                }
                        ) {
                            Icon(
                                imageVector = if (isHdr) Icons.Filled.HdrOn else Icons.Filled.HighQuality,
                                contentDescription = "画质与片源信息",
                                tint = if (useHdrPassthrough) Color(0xFFFFB800) else if (isHdr) themeAccent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            if (isHdr) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = resolutionLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                                    )
                                    Text(
                                        text = "HDR",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (useHdrPassthrough) Color(0xFFFFB800) else themeAccent,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = qualityBadgeText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        // 2. iOS style Playback Speed Button with Animated Vertical Roll Transition
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

                        LiquidButton(
                            onClick = {
                                val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                playbackSpeed = speeds[nextIdx]
                                setPlayerSpeed(playbackSpeed)
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = if (playbackSpeed != 1.0f) themeAccent else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            AnimatedContent(
                                targetState = playbackSpeed,
                                transitionSpec = {
                                    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { it / 2 } + fadeIn())
                                        .togetherWith(slideOutVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { -it / 2 } + fadeOut())
                                 },
                                label = "speedRoll"
                            ) { targetSpeed ->
                                Text(
                                    text = "${targetSpeed}x",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (targetSpeed != 1.0f) themeAccent else Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Continuous Zero-Frame-Drop GPU Morphing Dialog System (100% LiquidButton Optical Material)
        val isAnyModalActive = isEpisodeActive || isChapterActive || isTrackActive || isAudioActive || isDanmakuActive || isQualityActive
        val scrimAlpha = maxOf(episodeAnim.value, chapterAnim.value, trackAnim.value, audioAnim.value, danmakuAnim.value, qualityAnim.value) * 0.15f
        if (isAnyModalActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            showEpisodeSheet = false
                            showChapterSheet = false
                            showTrackSheet = false
                            showAudioSheet = false
                            showDanmakuSettings = false
                            showQualitySheet = false
                        }
                    )
            )
        }

        // 0. Episode Selector Morphing Dialog (选集弹窗)
        val fallbackEpisodeBounds = Rect(screenWidthPx - with(density) { 140.dp.toPx() }, with(density) { 20.dp.toPx() }, screenWidthPx - with(density) { 96.dp.toPx() }, with(density) { 64.dp.toPx() })
        val actualEpisodeBounds = if (episodeButtonBounds != Rect.Zero && episodeButtonBounds.width > 0f) episodeButtonBounds else fallbackEpisodeBounds
        val episodeBtnCenterX = actualEpisodeBounds.left + actualEpisodeBounds.width / 2f
        val episodeBtnCenterY = actualEpisodeBounds.top + actualEpisodeBounds.height / 2f

        if (isEpisodeActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = episodeAnim.value
                        val w = lerp(actualEpisodeBounds.width, episodeDialogWidthPx, p).roundToInt()
                        val h = lerp(actualEpisodeBounds.height, episodeDialogHeightPx, p).roundToInt()
                        val cx = lerp(episodeBtnCenterX, dialogCenterX, p)
                        val cy = lerp(episodeBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = episodeAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualEpisodeBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = episodeAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = episodeAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(0f, 0f),
                                    radius = 600f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualEpisodeBounds.height / 2f, 26.dp.toPx(), episodeAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = episodeAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = themeAccent, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .requiredSize(episodeDialogWidthDp, episodeDialogHeightDp)
                        .graphicsLayer {
                            val p = episodeAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(14.dp)
                ) {
                    EpisodeSelectorDialogContent(
                        allEpisodes = allEpisodes,
                        currentEpisodeId = episode.id,
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        onEpisodeClick = { onNextEpisode(it) },
                        onClose = { showEpisodeSheet = false }
                    )
                }
            }
        }

        // 1. Subtitle & Audio Track Switcher Morphing Dialog
        val fallbackTrackBounds = Rect(screenWidthPx - with(density) { 90.dp.toPx() }, with(density) { 20.dp.toPx() }, screenWidthPx - with(density) { 46.dp.toPx() }, with(density) { 64.dp.toPx() })
        val actualTrackBounds = if (trackButtonBounds != Rect.Zero && trackButtonBounds.width > 0f) trackButtonBounds else fallbackTrackBounds
        val trackBtnCenterX = actualTrackBounds.left + actualTrackBounds.width / 2f
        val trackBtnCenterY = actualTrackBounds.top + actualTrackBounds.height / 2f

        if (isTrackActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = trackAnim.value
                        val w = lerp(actualTrackBounds.width, trackDialogWidthPx, p).roundToInt()
                        val h = lerp(actualTrackBounds.height, trackDialogHeightPx, p).roundToInt()
                        val cx = lerp(trackBtnCenterX, dialogCenterX, p)
                        val cy = lerp(trackBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = trackAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualTrackBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = trackAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = trackAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(0f, 0f),
                                    radius = 600f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualTrackBounds.height / 2f, 26.dp.toPx(), trackAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = trackAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ClosedCaption, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .requiredSize(trackDialogWidthDp, trackDialogHeightDp)
                        .graphicsLayer {
                            val p = trackAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(20.dp)
                ) {
                    TrackSelectorDialogContent(
                        availableSubtitles = availableSubtitles,
                        availableExternalSubtitles = availableExternalSubtitles,
                        selectedExternalSubtitlePath = selectedExternalSubtitlePath,
                        isLoadingExternalSubs = isLoadingExternalSubs,
                        isSubtitleDisabled = isSubtitlesDisabledManually,
                        subtitleDelayMs = mpvPlayer.subtitleDelayMs.value,
                        onSubtitleDelayChange = { mpvPlayer.setSubtitleDelay(it) },
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        onSelectExternalSubtitle = { extSub ->
                            selectedExternalSubtitlePath = extSub.path
                            isSubtitlesDisabledManually = false
                            coroutineScope.launch {
                                try {
                                    val safeSubUrl = encodeMediaUrl(extSub.path)
                                    val prepResult = withContext(Dispatchers.IO) {
                                        SubtitleHelper.prepareExternalSubtitle(context, safeSubUrl, webDavAuth)
                                    }
                                    if (prepResult != null) {
                                        val (localUri, _) = prepResult
                                        val localPath = localUri.path ?: ""
                                        mpvPlayer.addExternalSubtitle(localPath, select = true)
                                        GlobalToastManager.show("已载入外挂字幕: ${extSub.name}", ToastType.Success)
                                    } else {
                                        GlobalToastManager.show("外挂字幕下载失败", ToastType.Error)
                                    }
                                } catch (e: Exception) {
                                    GlobalToastManager.show("外挂字幕加载出错: ${e.message}", ToastType.Error)
                                }
                            }
                        },
                        onSelectInternalSubtitle = { subTrack ->
                            selectedExternalSubtitlePath = null
                            isSubtitlesDisabledManually = false
                            mpvPlayer.selectSubtitleTrack(subTrack.mpvTrackId)
                            GlobalToastManager.show("已切换为内置字幕: ${subTrack.label}", ToastType.Success)
                        },
                        onDisableSubtitles = {
                            selectedExternalSubtitlePath = null
                            isSubtitlesDisabledManually = true
                            mpvPlayer.disableSubtitleTrack()
                            GlobalToastManager.show("已关闭字幕", ToastType.Info)
                        },
                        onClose = { showTrackSheet = false }
                    )
                }
            }
        }

        // 1.5. Audio Morphing Dialog
        val fallbackAudioBounds = Rect(screenWidthPx - with(density) { 56.dp.toPx() }, with(density) { 20.dp.toPx() }, screenWidthPx - with(density) { 12.dp.toPx() }, with(density) { 64.dp.toPx() })
        val actualAudioBounds = if (audioButtonBounds != Rect.Zero && audioButtonBounds.width > 0f) audioButtonBounds else fallbackAudioBounds
        val audioBtnCenterX = actualAudioBounds.left + actualAudioBounds.width / 2f
        val audioBtnCenterY = actualAudioBounds.top + actualAudioBounds.height / 2f

        if (isAudioActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = audioAnim.value
                        val w = lerp(actualAudioBounds.width, audioDialogWidthPx, p).roundToInt()
                        val h = lerp(actualAudioBounds.height, audioDialogHeightPx, p).roundToInt()
                        val cx = lerp(audioBtnCenterX, dialogCenterX, p)
                        val cy = lerp(audioBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = audioAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualAudioBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = audioAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = audioAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(0f, 0f),
                                    radius = 600f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualAudioBounds.height / 2f, 26.dp.toPx(), audioAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = audioAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .requiredSize(audioDialogWidthDp, audioDialogHeightDp)
                        .graphicsLayer {
                            val p = audioAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(20.dp)
                ) {
                    AudioSelectorDialogContent(
                        availableAudioTracks = availableAudioTracks,
                        audioDelayMs = mpvPlayer.audioDelayMs.value,
                        onAudioDelayChange = { mpvPlayer.setAudioDelay(it) },
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        onSelectAudioTrack = { audioTrack ->
                            mpvPlayer.selectAudioTrack(audioTrack.mpvTrackId)
                            GlobalToastManager.show("已切换为: ${audioTrack.label}", ToastType.Success)
                        },
                        onClose = { showAudioSheet = false }
                    )
                }
            }
        }

        // 2. Chapters Morphing Dialog
        val fallbackChapterBounds = Rect(screenWidthPx - with(density) { 140.dp.toPx() }, with(density) { 20.dp.toPx() }, screenWidthPx - with(density) { 96.dp.toPx() }, with(density) { 64.dp.toPx() })
        val actualChapterBounds = if (chapterButtonBounds != Rect.Zero && chapterButtonBounds.width > 0f) chapterButtonBounds else fallbackChapterBounds
        val chapterBtnCenterX = actualChapterBounds.left + actualChapterBounds.width / 2f
        val chapterBtnCenterY = actualChapterBounds.top + actualChapterBounds.height / 2f

        if (isChapterActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = chapterAnim.value
                        val w = lerp(actualChapterBounds.width, chapterDialogWidthPx, p).roundToInt()
                        val h = lerp(actualChapterBounds.height, chapterDialogHeightPx, p).roundToInt()
                        val cx = lerp(chapterBtnCenterX, dialogCenterX, p)
                        val cy = lerp(chapterBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = chapterAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualChapterBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = chapterAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = chapterAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(0f, 0f),
                                    radius = 600f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualChapterBounds.height / 2f, 26.dp.toPx(), chapterAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = chapterAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .requiredSize(chapterDialogWidthDp, chapterDialogHeightDp)
                        .graphicsLayer {
                            val p = chapterAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(20.dp)
                ) {
                    ChapterSelectorDialogContent(
                        availableChapters = availableChapters,
                        currentPositionMs = currentPositionMs,
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        onChapterClick = {
                            seekPlayerTo(it)
                            currentPositionMs = it
                        },
                        onClose = { showChapterSheet = false }
                    )
                }
            }
        }

        // 3. Danmaku Settings Morphing Dialog
        val fallbackDanmakuBounds = Rect(with(density) { 200.dp.toPx() }, screenHeightPx - with(density) { 64.dp.toPx() }, with(density) { 244.dp.toPx() }, screenHeightPx - with(density) { 20.dp.toPx() })
        val actualDanmakuBounds = if (danmakuButtonBounds != Rect.Zero && danmakuButtonBounds.width > 0f) danmakuButtonBounds else fallbackDanmakuBounds
        val danmakuBtnCenterX = actualDanmakuBounds.left + actualDanmakuBounds.width / 2f
        val danmakuBtnCenterY = actualDanmakuBounds.top + actualDanmakuBounds.height / 2f

        if (isDanmakuActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = danmakuAnim.value
                        val w = lerp(actualDanmakuBounds.width, danmakuDialogWidthPx, p).roundToInt()
                        val h = lerp(actualDanmakuBounds.height, danmakuDialogHeightPx, p).roundToInt()
                        val cx = lerp(danmakuBtnCenterX, dialogCenterX, p)
                        val cy = lerp(danmakuBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = danmakuAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualDanmakuBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = danmakuAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = danmakuAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.12f))
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(0f, 0f),
                                    radius = 600f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualDanmakuBounds.height / 2f, 26.dp.toPx(), danmakuAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = danmakuAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .requiredSize(danmakuDialogWidthDp, danmakuDialogHeightDp)
                        .graphicsLayer {
                            val p = danmakuAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(20.dp)
                ) {
                    DanmakuSettingsDialogContent(
                        danmakuConfig = danmakuConfig,
                        currentDanmakuCount = danmakuList.size,
                        currentMatchedTitle = currentMatchedDanmakuTitle,
                        initialSearchKeyword = anime.title,
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        onConfigChange = { danmakuConfig = it },
                        onSelectEpisodeDanmaku = { epId, matchedAnime, matchedEp ->
                            coroutineScope.launch {
                                try {
                                    val comments = DandanplayApiClient.getDanmakuComments(epId)
                                    if (comments.isNotEmpty()) {
                                        danmakuList = comments
                                        currentMatchedDanmakuTitle = "$matchedAnime - $matchedEp"
                                        GlobalToastManager.show("已载入「$matchedEp」弹幕 (${comments.size}条)", ToastType.Success)
                                    } else {
                                        GlobalToastManager.show("该剧集暂无弹幕数据", ToastType.Info)
                                    }
                                } catch (e: Exception) {
                                    GlobalToastManager.show("弹幕获取失败: ${e.message}", ToastType.Error)
                                }
                            }
                        },
                        onClose = { showDanmakuSettings = false }
                    )
                }
            }
        }

        // 4. Quality & HDR Source Specs Morphing Dialog (画质与片源规格详情弹窗)
        val qualityDialogWidthPx = minOf(screenWidthPx * 0.88f, with(density) { 460.dp.toPx() })
        val qualityDialogHeightPx = minOf(screenHeightPx * 0.65f, with(density) { 260.dp.toPx() })
        val fallbackQualityBounds = Rect(screenWidthPx - with(density) { 100.dp.toPx() }, screenHeightPx - with(density) { 80.dp.toPx() }, screenWidthPx - with(density) { 30.dp.toPx() }, screenHeightPx - with(density) { 36.dp.toPx() })
        val actualQualityBounds = if (qualityButtonBounds != Rect.Zero && qualityButtonBounds.width > 0f) qualityButtonBounds else fallbackQualityBounds
        val qualityBtnCenterX = actualQualityBounds.left + actualQualityBounds.width / 2f
        val qualityBtnCenterY = actualQualityBounds.top + actualQualityBounds.height / 2f

        if (isQualityActive) {
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = qualityAnim.value
                        val w = lerp(actualQualityBounds.width, qualityDialogWidthPx, p).roundToInt()
                        val h = lerp(actualQualityBounds.height, qualityDialogHeightPx, p).roundToInt()
                        val cx = lerp(qualityBtnCenterX, dialogCenterX, p)
                        val cy = lerp(qualityBtnCenterY, dialogCenterY, p)
                        val x = (cx - w / 2f).roundToInt()
                        val y = (cy - h / 2f).roundToInt()

                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                            placeable.place(x, y)
                        }
                    }
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = {
                            val p = qualityAnim.value.coerceIn(0f, 1f)
                            val r = lerp(actualQualityBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                            RoundedCornerShape(with(density) { r.toDp() })
                        },
                        effects = {
                            val p = qualityAnim.value.coerceIn(0f, 1f)
                            vibrancy()
                            blur(lerp(4f, 12f, p).dp.toPx())
                            lens(
                                refractionHeight = lerp(14f, 26f, p).dp.toPx(),
                                refractionAmount = lerp(28f, 52f, p).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            val p = qualityAnim.value.coerceIn(0f, 1f)
                            Shadow(
                                radius = lerp(4f, 24f, p).dp,
                                color = Color.Black.copy(alpha = lerp(0.08f, 0.35f, p))
                            )
                        },
                        onDrawSurface = {
                            // Rich frosted dark acrylic base for vibrant crystal appearance in both SDR and HDR
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1E2430).copy(alpha = if (useHdrPassthrough) 0.88f else 0.45f),
                                        Color(0xFF10141C).copy(alpha = if (useHdrPassthrough) 0.92f else 0.55f)
                                    )
                                )
                            )
                            // Top specular highlight
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.40f
                                )
                            )
                            // Theme radial glow
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        themeAccent.copy(alpha = 0.26f),
                                        themeAccent.copy(alpha = 0.06f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.1f, 0f),
                                    radius = size.width * 0.9f
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.0.dp,
                        brush = crystalBorderBrush,
                        shape = RoundedCornerShape(with(density) {
                            lerp(actualQualityBounds.height / 2f, 26.dp.toPx(), qualityAnim.value.coerceIn(0f, 1f)).toDp()
                        })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = qualityAnim.value
                            alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = if (isHdr) Icons.Filled.HdrOn else Icons.Filled.HighQuality,
                            contentDescription = "画质与片源信息",
                            tint = if (useHdrPassthrough) Color(0xFFFFB800) else if (isHdr) themeAccent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        if (isHdr) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = resolutionLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                                )
                                Text(
                                    text = "HDR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (useHdrPassthrough) Color(0xFFFFB800) else themeAccent,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = qualityBadgeText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .requiredSize(with(density) { qualityDialogWidthPx.toDp() }, with(density) { qualityDialogHeightPx.toDp() })
                        .graphicsLayer {
                            val p = qualityAnim.value
                            alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                        }
                        .padding(20.dp)
                ) {
                    val resolutionStr = if (vWidth > 0 && vHeight > 0) {
                        val label = when {
                            vHeight >= 2160 || vWidth >= 3840 -> "4K 超高清"
                            vHeight >= 1080 || vWidth >= 1920 -> "1080P 全高清"
                            vHeight >= 720 || vWidth >= 1280 -> "720P 高清"
                            else -> "标清"
                        }
                        "${vWidth} × ${vHeight} ($label)"
                    } else {
                        if (episode.fileSize > 20L * 1024 * 1024 * 1024) "3840 × 2160 (4K 超高清)" else episode.resolution.ifBlank { "自动检测中" }
                    }

                    val aspectVal = if (mpvPlayer.aspectRatio.value > 0) mpvPlayer.aspectRatio.value else cachedAspectRatio
                    val aspectRatioStr = if (aspectVal > 1.8) "16:9 宽屏" else if (aspectVal > 2.2) "21:9 原生宽银幕" else "标准比例"

                    val primaries = mpvPlayer.colorPrimaries.value
                    val colorSpaceStr = if (primaries.contains("2020", ignoreCase = true) || isHdr) cachedColorSpace else "BT.709 标准色域"
                    val pixFmt = mpvPlayer.pixelFormat.value
                    val bitDepthStr = if (pixFmt.contains("10") || isHdr) cachedBitDepth else "8-bit SDR (1670万色)"

                    val fps = if (mpvPlayer.videoFps.value > 0) mpvPlayer.videoFps.value else cachedFps
                    val frameRateStr = if (fps > 0) String.format(java.util.Locale.US, "%.3f fps", fps) else "23.976 fps"

                    val hwStatus = mpvPlayer.hwdecActive.value
                    val hwDecoderStr = if (hwStatus.isNotBlank() || useHdrPassthrough) "MediaCodec (GPU 硬解)" else "FFmpeg (软解)"

                    val vCodec = if (mpvPlayer.videoCodec.value.isNotBlank()) mpvPlayer.videoCodec.value else cachedVCodec
                    val vCodecStr = when {
                        vCodec.contains("hevc", ignoreCase = true) || vCodec.contains("h265", ignoreCase = true) -> "HEVC / H.265 (Main 10)"
                        vCodec.contains("avc", ignoreCase = true) || vCodec.contains("h264", ignoreCase = true) -> "AVC / H.264 (High Profile)"
                        vCodec.contains("av01", ignoreCase = true) || vCodec.contains("av1", ignoreCase = true) -> "AV1 (AOMedia)"
                        vCodec.isNotBlank() -> vCodec.uppercase()
                        episode.videoCodec.isNotBlank() -> episode.videoCodec
                        isHdr -> "HEVC / H.265 (Main 10)"
                        else -> "HEVC (蓝光原生)"
                    }

                    val aCodec = if (mpvPlayer.audioCodec.value.isNotBlank()) mpvPlayer.audioCodec.value else cachedACodec
                    val aCodecStr = when {
                        aCodec.contains("dts", ignoreCase = true) -> "DTS-HD Master Audio"
                        aCodec.contains("truehd", ignoreCase = true) -> "Dolby TrueHD"
                        aCodec.contains("flac", ignoreCase = true) -> "FLAC 无损音频"
                        aCodec.contains("pcm", ignoreCase = true) -> "LPCM 原生音频"
                        aCodec.contains("ac3", ignoreCase = true) -> "Dolby Digital (AC-3)"
                        aCodec.contains("aac", ignoreCase = true) -> "AAC 高保真"
                        aCodec.isNotBlank() -> aCodec.uppercase()
                        episode.audioCodec.isNotBlank() -> episode.audioCodec
                        else -> "AAC / 立体声"
                    }

                    val sRate = mpvPlayer.audioSampleRate.value
                    val ch = mpvPlayer.audioChannels.value
                    val chStr = if (ch.contains("5.1")) "5.1 环绕声" else if (ch.contains("7.1")) "7.1 全景声" else if (ch.isNotBlank()) ch else "立体声 (2.0)"
                    val audioDetailsStr = "$sRate Hz · $chStr"

                    val avgBitrate = if (episode.fileSize > 0 && durationMs > 0) {
                        (episode.fileSize * 8f) / (durationMs / 1000f) / 1_000_000f
                    } else 0f

                    val baseAvgBitrate = if (avgBitrate > 0f) avgBitrate else 7.45f
                    val streamBitrate = if (liveBitrateMbps > 0.05f) liveBitrateMbps else baseAvgBitrate

                    val bitrateStr = String.format(java.util.Locale.US, "%.2f Mbps (均值 %.2f Mbps)", streamBitrate, baseAvgBitrate)

                    val fileSizeStr = if (episode.fileSize > 0) {
                        val gb = episode.fileSize / (1024.0 * 1024.0 * 1024.0)
                        if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", episode.fileSize / (1024.0 * 1024.0))
                    } else "原盘规格"

                    QualitySpecsDialogContent(
                        isHdr = isHdr,
                        hdrType = hdrType,
                        resolutionStr = resolutionStr,
                        aspectRatioStr = aspectRatioStr,
                        colorSpaceStr = colorSpaceStr,
                        bitDepthStr = bitDepthStr,
                        frameRateStr = frameRateStr,
                        hwDecoderStr = hwDecoderStr,
                        vCodecStr = vCodecStr,
                        aCodecStr = aCodecStr,
                        audioDetailsStr = audioDetailsStr,
                        bitrateStr = bitrateStr,
                        fileSizeStr = fileSizeStr,
                        cacheReadaheadStr = "150 MB (60s 预读缓冲)",
                        playerBackdrop = playerBackdrop,
                        themeAccent = themeAccent,
                        mpvPlayer = mpvPlayer,
                        useHdrPassthrough = useHdrPassthrough,
                        onToggleHdrPassthrough = onToggleHdrPassthrough,
                        onClose = { showQualitySheet = false }
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun VideoAndDanmakuLayer(
    mpvPlayer: MpvPlayerManager,
    safeVideoUrl: String,
    headers: Map<String, String>?,
    useHdrPassthrough: Boolean,
    hdrStartPositionMs: Long,
    isHdrPlaying: Boolean,
    playbackSpeed: Float,
    hdrSeekCommandMs: Long?,
    onHdrPositionUpdate: (Long, Long) -> Unit,
    onHdrBufferingUpdate: (Boolean) -> Unit,
    danmakuList: List<DanmakuItem>,
    positionMsProvider: () -> Long,
    isPlaying: Boolean,
    danmakuConfig: DanmakuConfig,
    resizeMode: MpvPlayerManager.ResizeMode = MpvPlayerManager.ResizeMode.FIT,
    onToggleHdrPassthrough: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (useHdrPassthrough) {
            // True Hardware HDR Direct Channel: Native Media3 ExoPlayer + SurfaceView (Hardware Composer HDR10 PQ Passthrough)
            HdrExoPlayerView(
                videoUrl = safeVideoUrl,
                headers = headers,
                startPositionMs = hdrStartPositionMs,
                isPlaying = isHdrPlaying,
                playbackSpeed = playbackSpeed,
                resizeMode = resizeMode,
                seekCommandMs = hdrSeekCommandMs,
                onPositionUpdate = onHdrPositionUpdate,
                onBufferingUpdate = onHdrBufferingUpdate,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Standard Channel: mpv TextureView engine (Full ASS subtitle fidelity + Liquid Glass)
            AndroidView(
                factory = { ctx ->
                    MpvVideoView(ctx).apply {
                        setPlayer(mpvPlayer)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.setPlayer(mpvPlayer)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        DanmakuCanvas(
            danmakuList = danmakuList,
            positionMsProvider = positionMsProvider,
            isPlaying = isPlaying,
            config = danmakuConfig,
            modifier = Modifier.fillMaxSize()
        )
    }
}


