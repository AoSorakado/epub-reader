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
    onPositionUpdate: (currentMs: Long, durationMs: Long) -> Unit,
    onBufferingUpdate: (Boolean) -> Unit,
    onPlaybackEnded: () -> Unit = {},
    onErrorFallback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // Initialize ExoPlayer instance
    DisposableEffect(videoUrl) {
        Log.i(TAG, "Initializing HdrExoPlayerView with URL: $videoUrl, startPos=$startPositionMs")
        val uri = Uri.parse(videoUrl)
        val dataSourceFactory: DataSource.Factory = if (uri.scheme == "http" || uri.scheme == "https") {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
            if (headers != null) {
                httpFactory.setDefaultRequestProperties(headers)
            }
            httpFactory
        } else {
            DefaultDataSource.Factory(context)
        }

        // Configure TS / M2TS extractor flags for universal Blu-ray and stream support
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )
            .setTextTrackTranscodingEnabled(true)

        val effectiveDataSourceFactory = if (videoUrl.contains(".m2ts", ignoreCase = true)) {
            BdavDataSource.Factory(dataSourceFactory)
        } else {
            dataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(effectiveDataSourceFactory, extractorsFactory)

        // High-bitrate load control for Blu-ray original discs
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3000,  // minBufferMs (3s buffer before starting)
                60000, // maxBufferMs (up to 60s buffer)
                1000,  // bufferForPlaybackMs
                2500   // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(128 * 1024 * 1024) // 128MB target buffer
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Track selector configured to pick supported AC3/AAC/EAC3 audio and subtitle tracks
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioMimeTypes("audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/raw", "audio/flac", "audio/opus")
                    .setPreferredTextLanguage("zh")
                    .setSelectUndeterminedTextLanguage(true)
                    .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

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
            if (startPositionMs > 1000L) {
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

                override fun onTracksChanged(tracks: Tracks) {
                    Log.i(TAG, "onTracksChanged: groups=${tracks.groups.size}")
                    var hasPlayableAudio = false
                    var audioTrackCount = 0
                    for (group in tracks.groups) {
                        val type = when (group.type) {
                            C.TRACK_TYPE_VIDEO -> "VIDEO"
                            C.TRACK_TYPE_AUDIO -> "AUDIO"
                            C.TRACK_TYPE_TEXT -> "TEXT"
                            else -> "OTHER"
                        }
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            audioTrackCount += group.length
                            for (i in 0 until group.length) {
                                if (group.isTrackSupported(i)) {
                                    hasPlayableAudio = true
                                }
                            }
                        }
                        for (i in 0 until group.length) {
                            val f = group.getTrackFormat(i)
                            val isSel = group.isTrackSelected(i)
                            val isSupp = group.isTrackSupported(i)
                            Log.i(TAG, "  TrackGroup [$type] #$i: mime=${f.sampleMimeType}, codecs=${f.codecs}, res=${f.width}x${f.height}, selected=$isSel, supported=$isSupp")
                        }
                    }

                    // If audio tracks exist but NONE are supported (e.g. DTS-HD / TrueHD on devices without hardware DTS decoder),
                    // disable audio renderer so video clock is not blocked and video can play immediately in HDR!
                    if (audioTrackCount > 0 && !hasPlayableAudio) {
                        Log.w(TAG, "No supported audio codec found on device. Disabling audio renderer to unlock standalone video clock.")
                        val audioRendererIndex = (0 until rendererCount).firstOrNull {
                            getRendererType(it) == C.TRACK_TYPE_AUDIO
                        }
                        if (audioRendererIndex != null) {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters().setRendererDisabled(audioRendererIndex, true)
                            )
                        }
                    }
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

        exoPlayer = player
        currentSurfaceView?.let { player.setVideoSurfaceView(it) }

        onDispose {
            Log.i(TAG, "Disposing HdrExoPlayerView")
            try {
                player.stop()
                player.release()
            } catch (e: Throwable) {
                // Ignore
            }
            exoPlayer = null
        }
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
            if (player != null && player.playbackState == Player.STATE_READY) {
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
                    setZOrderMediaOverlay(true)
                    try {
                        holder.setFormat(android.graphics.PixelFormat.RGBA_1010102)
                    } catch (e: Throwable) {}
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        try {
                            setDesiredHdrHeadroom(10.0f)
                        } catch (e: Throwable) {}
                    }
                    currentSurfaceView = this
                    exoPlayer?.setVideoSurfaceView(this)
                }
            },
            update = { surfaceView ->
                currentSurfaceView = surfaceView
                exoPlayer?.setVideoSurfaceView(surfaceView)
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
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(true)
                    exoPlayer?.addListener(object : Player.Listener {
                        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                            setCues(cueGroup.cues)
                        }
                    })
                }
            },
            update = { subtitleView ->
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
