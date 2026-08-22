package com.example.epubreader.ui.player

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "HdrExo"

/**
 * Native AndroidX Media3 (ExoPlayer) Video View connected directly to SurfaceView.
 * Used specifically for the "HDR 硬件直通通道" (HDR Passthrough Channel).
 *
 * Direct Hardware HDR10 / ST 2084 / BT.2020 passthrough with full support for MKV, MP4, M2TS, BDMV.
 */
@OptIn(UnstableApi::class)
@Composable
fun HdrExoPlayerView(
    videoUrl: String,
    headers: Map<String, String>? = null,
    startPositionMs: Long = 0L,
    isPlaying: Boolean,
    playbackSpeed: Float = 1.0f,
    resizeMode: MpvPlayerManager.ResizeMode = MpvPlayerManager.ResizeMode.FIT,
    seekCommandMs: Long? = null,
    selectedSubtitleIndex: Int? = null,
    isSubtitleDisabled: Boolean = false,
    onPositionUpdate: (currentMs: Long, durationMs: Long) -> Unit,
    onBufferingUpdate: (Boolean) -> Unit,
    onPlaybackEnded: () -> Unit = {},
    onErrorFallback: () -> Unit = {},
    onAudioSupportStatus: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentTrackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }
    var currentSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var currentSubtitleView by remember { mutableStateOf<androidx.media3.ui.SubtitleView?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // Initialize ExoPlayer instance
    DisposableEffect(videoUrl) {
        Log.i(TAG, "Initializing HdrExoPlayerView with URL: $videoUrl, startPos=$startPositionMs")
        val uri = Uri.parse(videoUrl)
        val dataSourceFactory: DataSource.Factory = if (uri.scheme == "http" || uri.scheme == "https") {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            if (headers != null) {
                httpFactory.setDefaultRequestProperties(headers)
            }
            httpFactory
        } else {
            DefaultDataSource.Factory(context)
        }

        // Configure extractor factory:
        // For M2TS (BDMV), use BdavTsPayloadReaderFactory which remaps HDMV-specific audio
        // stream types (0x83 TrueHD, 0x86 DTS-HD MA, etc.) to their core equivalents and routes PGS subtitles.
        // For other formats, use DefaultExtractorsFactory with standard TS flags.
        val extractorsFactory = if (videoUrl.contains(".m2ts", ignoreCase = true)) {
            androidx.media3.extractor.ExtractorsFactory {
                arrayOf(
                    TsExtractor(
                        TsExtractor.MODE_MULTI_PMT,
                        androidx.media3.common.util.TimestampAdjuster(0),
                        BdavTsPayloadReaderFactory()
                    )
                )
            }
        } else {
            DefaultExtractorsFactory()
                .setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
                )
                .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT)
        }

        val effectiveDataSourceFactory = if (videoUrl.contains(".m2ts", ignoreCase = true)) {
            BdavDataSource.Factory(dataSourceFactory)
        } else {
            dataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(effectiveDataSourceFactory, extractorsFactory)

        // Ultra-low latency load control for responsive 4K HDR playback & instant switching
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500,  // minBufferMs (2.5s)
                30000, // maxBufferMs (30s)
                500,   // bufferForPlaybackMs (500ms for instant start)
                1500   // bufferForPlaybackAfterRebufferMs (1.5s)
            )
            .setTargetBufferBytes(64 * 1024 * 1024) // 64MB target buffer
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Track selector configured to pick supported audio and subtitle tracks
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Audio: ensure audio renderer is enabled and prefer Japanese
                    .setPreferredAudioLanguage("ja")
                    .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
                    // Subtitles: prefer Chinese, enable undetermined language selection
                    .setPreferredTextLanguage("zh")
                    .setSelectUndeterminedTextLanguage(true)
                    .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
            )
        }

        val subtitleDecoderFactory = object : androidx.media3.exoplayer.text.SubtitleDecoderFactory {
            override fun supportsFormat(format: androidx.media3.common.Format): Boolean {
                return format.sampleMimeType == androidx.media3.common.MimeTypes.APPLICATION_PGS ||
                        androidx.media3.exoplayer.text.SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
            }

            override fun createDecoder(format: androidx.media3.common.Format): androidx.media3.extractor.text.SubtitleDecoder {
                return if (format.sampleMimeType == androidx.media3.common.MimeTypes.APPLICATION_PGS) {
                    CustomPgsDecoder()
                } else {
                    androidx.media3.exoplayer.text.SubtitleDecoderFactory.DEFAULT.createDecoder(format)
                }
            }
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildTextRenderers(
                context: android.content.Context,
                output: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                out.add(androidx.media3.exoplayer.text.TextRenderer(output, outputLooper, subtitleDecoderFactory))
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        player.apply {
            videoScalingMode = when (resizeMode) {
                MpvPlayerManager.ResizeMode.ZOOM -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                else -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (startPositionMs > 0L) {
                seekTo(startPositionMs)
            }
            playWhenReady = isPlaying
            playbackParameters = PlaybackParameters(playbackSpeed)
            
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    Log.i(TAG, "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}, ratio=${videoSize.pixelWidthHeightRatio}")
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val width = videoSize.width.toFloat()
                        val height = videoSize.height.toFloat()
                        val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1.0f
                        val aspect = (width / height) * pixelRatio
                        if (aspect > 0.1f && aspect < 10f) {
                            videoAspectRatio = aspect
                        }
                    }
                }

                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    Log.d(TAG, "onCues received: ${cueGroup.cues.size} cues, view=${currentSubtitleView != null}")
                    currentSubtitleView?.setCues(cueGroup.cues)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    Log.i(TAG, "onTracksChanged: groups=${tracks.groups.size}, url=$videoUrl")
                    var hasAudio = false
                    var hasSupportedAudio = false
                    for (group in tracks.groups) {
                        val type = when (group.type) {
                            C.TRACK_TYPE_VIDEO -> "VIDEO"
                            C.TRACK_TYPE_AUDIO -> "AUDIO"
                            C.TRACK_TYPE_TEXT  -> "TEXT"
                            else               -> "OTHER"
                        }
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            hasAudio = true
                            for (i in 0 until group.length) {
                                if (group.isTrackSupported(i)) {
                                    hasSupportedAudio = true
                                }
                            }
                        }
                        for (i in 0 until group.length) {
                            val f = group.getTrackFormat(i)
                            val isSel = group.isTrackSelected(i)
                            val isSupp = group.isTrackSupported(i)
                            Log.i(TAG, "  TrackGroup [$type] #$i: mime=${f.sampleMimeType}, codecs=${f.codecs}, res=${f.width}x${f.height}, lang=${f.language}, selected=$isSel, supported=$isSupp")
                        }
                    }
                    val isExoAudioPlayable = !hasAudio || hasSupportedAudio
                    onAudioSupportStatus(isExoAudioPlayable)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "onPlayerError: [${error.errorCodeName}] ${error.message}", error)
                    onErrorFallback()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    val stateName = when (state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    Log.i(TAG, "onPlaybackStateChanged: $stateName, playWhenReady=${playWhenReady}, pos=${currentPosition}/${duration}")
                    when (state) {
                        Player.STATE_BUFFERING -> onBufferingUpdate(true)
                        Player.STATE_READY -> onBufferingUpdate(false)
                        Player.STATE_ENDED -> onPlaybackEnded()
                        else -> {}
                    }
                }
            })
        }

        currentTrackSelector = trackSelector
        exoPlayer = player
        currentSurfaceView?.let { player.setVideoSurfaceView(it) }

        onDispose {
            Log.i(TAG, "Disposing HdrExoPlayerView")
            currentTrackSelector = null
            exoPlayer = null
            currentSurfaceView = null
            currentSubtitleView = null
            player.release()
        }
    }

    // Sync subtitle track selection
    LaunchedEffect(exoPlayer, selectedSubtitleIndex, isSubtitleDisabled) {
        val player = exoPlayer ?: return@LaunchedEffect
        val selector = currentTrackSelector ?: return@LaunchedEffect
        val parameters = selector.buildUponParameters()
        if (isSubtitleDisabled) {
            parameters.setRendererDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            parameters.setRendererDisabled(C.TRACK_TYPE_TEXT, false)
            val currentTracks = player.currentTracks
            val textGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val targetIndex = selectedSubtitleIndex ?: if (textGroups.size > 1) 1 else 0
            if (targetIndex in textGroups.indices) {
                val group = textGroups[targetIndex]
                parameters.setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, 0)
                )
                Log.i(TAG, "Selected subtitle track #$targetIndex (${group.getTrackFormat(0).sampleMimeType})")
            }
        }
        selector.setParameters(parameters)
    }

    // Sync scaling mode
    LaunchedEffect(resizeMode) {
        exoPlayer?.videoScalingMode = when (resizeMode) {
            MpvPlayerManager.ResizeMode.ZOOM -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // Sync play/pause
    LaunchedEffect(isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    // Sync playback speed
    LaunchedEffect(playbackSpeed) {
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Sync seek
    LaunchedEffect(seekCommandMs) {
        if (seekCommandMs != null && seekCommandMs >= 0L) {
            exoPlayer?.seekTo(seekCommandMs)
        }
    }

    // Position reporting ticker
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            val player = exoPlayer
            if (player != null && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)) {
                val current = player.currentPosition
                val duration = player.duration.coerceAtLeast(0L)
                onPositionUpdate(current, duration)
            }
            delay(250L)
        }
    }

    // Centered Aspect Ratio Box in Jetpack Compose with solid Black background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setZOrderOnTop(false)
                    setZOrderMediaOverlay(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        try {
                            setDesiredHdrHeadroom(4.0f)
                        } catch (e: Throwable) {}
                    }
                    currentSurfaceView = this
                    exoPlayer?.setVideoSurfaceView(this)
                }
            },
            update = { surfaceView ->
                if (currentSurfaceView !== surfaceView) {
                    currentSurfaceView = surfaceView
                    exoPlayer?.setVideoSurfaceView(surfaceView)
                }
            },
            modifier = when (resizeMode) {
                MpvPlayerManager.ResizeMode.FIT -> Modifier
                    .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false)
                    .fillMaxSize()
                MpvPlayerManager.ResizeMode.ZOOM -> Modifier.fillMaxSize()
                MpvPlayerManager.ResizeMode.FILL -> Modifier.fillMaxSize()
            }
        )

        // Subtitle Overlay (Renders PGS Blu-ray bitmap subtitles, SRT, and VTT over HDR video)
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.SubtitleView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_CANVAS)
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(true)
                    currentSubtitleView = this
                    exoPlayer?.currentCues?.let { cueGroup ->
                        setCues(cueGroup.cues)
                    }
                }
            },
            update = { subtitleView ->
                currentSubtitleView = subtitleView
                exoPlayer?.currentCues?.let { cueGroup ->
                    subtitleView.setCues(cueGroup.cues)
                }
            },
            modifier = when (resizeMode) {
                MpvPlayerManager.ResizeMode.FIT -> Modifier
                    .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false)
                    .fillMaxSize()
                MpvPlayerManager.ResizeMode.ZOOM -> Modifier.fillMaxSize()
                MpvPlayerManager.ResizeMode.FILL -> Modifier.fillMaxSize()
            }
        )
    }
}
