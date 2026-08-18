package com.example.epubreader.ui.player

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import com.example.epubreader.R
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import java.io.File
import java.io.FileOutputStream

data class MpvTrack(
    val id: Int,
    val type: String,
    val title: String?,
    val lang: String?,
    val isSelected: Boolean,
    val codec: String?,
    val isExternal: Boolean
)

class MpvPlayerManager(private val context: Context) : MPV.EventObserver, MPV.LogObserver {

    companion object {
        private const val TAG = "MpvPlayer"
    }

    private val mpv = MPV()
    private var isDestroyed = false
    private var isSurfaceAttached = false

    val isPlaying = mutableStateOf(true)
    val positionMs = mutableStateOf(0L)
    val durationMs = mutableStateOf(0L)
    val bufferedDurationMs = mutableStateOf(0L)
    val isBuffering = mutableStateOf(false)
    val tracks = mutableStateOf<List<MpvTrack>>(emptyList())
    val videoWidth = mutableStateOf(0)
    val videoHeight = mutableStateOf(0)
    val eofReached = mutableStateOf(false)

    val videoCodec = mutableStateOf("")
    val audioCodec = mutableStateOf("")
    val pixelFormat = mutableStateOf("")
    val colorMatrix = mutableStateOf("")
    val colorPrimaries = mutableStateOf("")
    val colorGamma = mutableStateOf("")
    val videoFps = mutableStateOf(23.976)
    val aspectRatio = mutableStateOf(16.0 / 9.0)
    val hwdecActive = mutableStateOf("mediacodec")
    val audioChannels = mutableStateOf("")
    val audioSampleRate = mutableStateOf(48000)
    val audioDelayMs = mutableStateOf(0L)
    val subtitleDelayMs = mutableStateOf(0L)

    enum class ResizeMode {
        FIT, ZOOM, FILL
    }

    init {
        try {
            val appCtx = context.applicationContext

            // 1. Copy mpv core assets (subfont.ttf, cacert.pem) into app files directory
            Utils.copyAssets(appCtx)

            val configDir = appCtx.filesDir
            val cacheDir = appCtx.cacheDir
            val fontsDir = File(configDir, "fonts").apply { mkdirs() }

            // 2. Copy bundled high-quality fonts (DFLeiGaSo, GenRyuMin, LXGW WenKai) to fonts directory
            copyAppFonts(appCtx, fontsDir)

            // 3. Write fontconfig config with desktop mpv matching aliases
            setupFontConfig(configDir, fontsDir)

            // 4. Create MPV instance
            mpv.create(appCtx)

            // Essential directory configuration
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("config-dir", configDir.absolutePath)
            mpv.setOptionString("gpu-shader-cache-dir", cacheDir.absolutePath)
            mpv.setOptionString("icc-cache-dir", cacheDir.absolutePath)

            // Video output & hardware decoding
            mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("hwdec", "auto")
            mpv.setOptionString("force-window", "no")
            mpv.setOptionString("video-sync", "audio")

            // Subtitle & ASS rendering settings (Matches desktop mpv-lazy-2026 standard)
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("sub-ass", "yes")
            mpv.setOptionString("sub-visibility", "yes")
            mpv.setOptionString("sub-ass-override", "no") // Full ASS styling retention
            mpv.setOptionString("sub-ass-force-margins", "yes")
            mpv.setOptionString("sub-ass-hinting", "native")
            mpv.setOptionString("sub-ass-shaper", "harfbuzz")
            mpv.setOptionString("embeddedfonts", "yes")
            mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
            mpv.setOptionString("sub-font", "GenRyuMin2 TW SB")
            mpv.setOptionString("sub-font-size", "44")
            mpv.setOptionString("sub-codepage", "GB18030")
            mpv.setOptionString("slang", "chs,sc,zh-cn,chi,zh,ja,jp,jpn,en,eng")
            mpv.setOptionString("subs-fallback", "yes")
            mpv.setOptionString("blend-subtitles", "yes")

            // Streaming Cache
            mpv.setOptionString("cache", "yes")
            mpv.setOptionString("demuxer-max-bytes", "150MiB")
            mpv.setOptionString("demuxer-readahead-secs", "60")

            // Disable default mpv key/gesture handlers
            mpv.setOptionString("input-default-bindings", "no")
            mpv.setOptionString("input-vo-keyboard", "no")

            // Enable verbose logging
            mpv.setOptionString("msg-level", "all=v")

            // 5. Initialize MPV engine
            mpv.init()
            mpv.addObserver(this)
            mpv.addLogObserver(this)

            // 6. Observe properties
            mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("demuxer-cache-duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("video-params/w", MPV.mpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("video-params/h", MPV.mpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("track-list/count", MPV.mpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("sub-visibility", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("video-params/pixelformat", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("video-params/colormatrix", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("video-params/primaries", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("video-params/gamma", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("container-fps", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("hwdec-current", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("audio-params/channels", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("audio-params/samplerate", MPV.mpvFormat.MPV_FORMAT_INT64)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
        }
    }

    private fun copyAppFonts(context: Context, fontsDir: File) {
        try {
            // Copy all assets/fonts into runtime fonts directory
            val assetList = context.assets.list("fonts") ?: emptyArray()
            for (fontName in assetList) {
                val destFile = File(fontsDir, fontName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    context.assets.open("fonts/$fontName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied asset font: $fontName -> ${destFile.absolutePath}")
                }
            }
            // Copy LXGW WenKai from res/font
            val wenkaiDest = File(fontsDir, "lxgw_wenkai.ttf")
            if (!wenkaiDest.exists() || wenkaiDest.length() == 0L) {
                try {
                    context.resources.openRawResource(R.font.lxgw_wenkai).use { input ->
                        FileOutputStream(wenkaiDest).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy font assets", e)
        }
    }

    private fun setupFontConfig(configDir: File, fontsDir: File) {
        val fontsConfFile = File(configDir, "fonts.conf")
        val fontsConfContent = """
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
            <fontconfig>
                <dir>/system/fonts</dir>
                <dir>/system/font</dir>
                <dir>/data/fonts</dir>
                <dir>${fontsDir.absolutePath}</dir>
                <dir>${configDir.absolutePath}</dir>
                <match target="pattern">
                    <test qual="any" name="family"><string>sans-serif</string></test>
                    <edit name="family" mode="prepend" binding="strong">
                        <string>GenRyuMin2 TW SB</string>
                        <string>GenRyuMin2-SB</string>
                        <string>DFLeiGaSo-W9</string>
                        <string>DFPLeiGaSo-W9</string>
                        <string>LXGWWenKaiMono-Regular</string>
                        <string>LXGW WenKai Mono</string>
                        <string>LXGW WenKai</string>
                    </edit>
                </match>
                <match target="pattern">
                    <test qual="any" name="family"><string>serif</string></test>
                    <edit name="family" mode="prepend" binding="strong">
                        <string>GenRyuMin2 TW SB</string>
                        <string>GenRyuMin2-SB</string>
                        <string>DFLeiGaSo-W9</string>
                        <string>LXGW WenKai</string>
                    </edit>
                </match>
            </fontconfig>
        """.trimIndent()
        try {
            fontsConfFile.writeText(fontsConfContent)
            Log.d(TAG, "Wrote fontconfig file to ${fontsConfFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write fonts.conf", e)
        }
    }

    fun attachSurface(surface: Surface) {
        if (isDestroyed) return
        try {
            Log.d(TAG, "Attaching surface to mpv")
            mpv.attachSurface(surface)
            isSurfaceAttached = true
            mpv.setPropertyBoolean("sub-visibility", true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface", e)
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        if (isDestroyed) return
        try {
            Log.d(TAG, "Setting surface size: ${width}x${height}")
            mpv.setPropertyString("android-surface-size", "${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set surface size", e)
        }
    }

    fun detachSurface() {
        if (isDestroyed || !isSurfaceAttached) return
        try {
            Log.d(TAG, "Detaching surface from mpv")
            isSurfaceAttached = false
            mpv.detachSurface()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detach surface", e)
        }
    }

    fun loadFile(url: String, headers: Map<String, String>? = null) {
        if (isDestroyed) return
        try {
            if (headers != null && headers.isNotEmpty()) {
                val headerFields = headers.entries.joinToString(separator = ",") { entry -> "${entry.key}: ${entry.value}" }
                mpv.setPropertyString("http-header-fields", headerFields)
            }
            Log.d(TAG, "Loading media file: $url")
            mpv.command("loadfile", url)
            mpv.setPropertyBoolean("sub-visibility", true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load file", e)
        }
    }

    fun play() {
        if (isDestroyed) return
        try {
            mpv.setPropertyBoolean("pause", false)
            isPlaying.value = true
            isBuffering.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play", e)
        }
    }

    fun pause() {
        if (isDestroyed) return
        try {
            mpv.setPropertyBoolean("pause", true)
            isPlaying.value = false
            isBuffering.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause", e)
        }
    }

    fun togglePlayPause() {
        if (isDestroyed) return
        try {
            val paused = mpv.getPropertyBoolean("pause") ?: false
            val newPaused = !paused
            mpv.setPropertyBoolean("pause", newPaused)
            isPlaying.value = !newPaused
            if (newPaused) {
                isBuffering.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle play/pause", e)
        }
    }

    fun seekTo(positionMs: Long) {
        if (isDestroyed) return
        try {
            mpv.setPropertyDouble("time-pos", positionMs / 1000.0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek to $positionMs", e)
        }
    }

    fun seekRelative(deltaMs: Long) {
        if (isDestroyed) return
        try {
            mpv.command("seek", (deltaMs / 1000.0).toString(), "relative")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek relative $deltaMs", e)
        }
    }

    fun setSpeed(speed: Float) {
        if (isDestroyed) return
        try {
            mpv.setPropertyDouble("speed", speed.toDouble())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speed $speed", e)
        }
    }

    fun setResizeMode(mode: ResizeMode) {
        if (isDestroyed) return
        try {
            when (mode) {
                ResizeMode.FIT -> {
                    mpv.setPropertyBoolean("keepaspect", true)
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                ResizeMode.ZOOM -> {
                    mpv.setPropertyBoolean("keepaspect", true)
                    mpv.setPropertyDouble("panscan", 1.0)
                }
                ResizeMode.FILL -> {
                    mpv.setPropertyBoolean("keepaspect", false)
                    mpv.setPropertyDouble("panscan", 0.0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set resize mode", e)
        }
    }

    fun selectSubtitleTrack(id: Int) {
        if (isDestroyed) return
        try {
            Log.d(TAG, "Selecting subtitle track: $id")
            mpv.setPropertyString("sid", id.toString())
            mpv.setPropertyBoolean("sub-visibility", true)
            updateTracks()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select subtitle track $id", e)
        }
    }

    fun disableSubtitleTrack() {
        if (isDestroyed) return
        try {
            Log.d(TAG, "Disabling subtitle track")
            mpv.setPropertyString("sid", "no")
            mpv.setPropertyBoolean("sub-visibility", false)
            updateTracks()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable subtitle track", e)
        }
    }

    fun selectAudioTrack(id: Int) {
        if (isDestroyed) return
        try {
            Log.d(TAG, "Selecting audio track: $id")
            mpv.setPropertyString("aid", id.toString())
            updateTracks()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select audio track $id", e)
        }
    }

    fun addExternalSubtitle(path: String, select: Boolean = true) {
        if (isDestroyed) return
        try {
            val flag = if (select) "select" else "auto"
            Log.d(TAG, "Adding external subtitle: $path (select=$select)")
            mpv.command("sub-add", path, flag)
            if (select) {
                mpv.setPropertyBoolean("sub-visibility", true)
            }
            updateTracks()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add external subtitle $path", e)
        }
    }

    fun setSubtitleDelay(ms: Long) {
        if (isDestroyed) return
        try {
            subtitleDelayMs.value = ms
            mpv.setPropertyDouble("sub-delay", ms / 1000.0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle delay $ms", e)
        }
    }

    fun setAudioDelay(ms: Long) {
        if (isDestroyed) return
        try {
            audioDelayMs.value = ms
            mpv.setPropertyDouble("audio-delay", ms / 1000.0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set audio delay $ms", e)
        }
    }

    fun getTracks(): List<MpvTrack> {
        return tracks.value
    }

    private fun updateTracks() {
        if (isDestroyed) return
        try {
            val count = mpv.getPropertyInt("track-list/count") ?: 0
            val newTracks = mutableListOf<MpvTrack>()
            for (i in 0 until count) {
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val type = mpv.getPropertyString("track-list/$i/type") ?: continue
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang")
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                val codec = mpv.getPropertyString("track-list/$i/codec")
                val external = mpv.getPropertyBoolean("track-list/$i/external") ?: false

                newTracks.add(MpvTrack(id, type, title, lang, selected, codec, external))
            }
            tracks.value = newTracks
            Log.d(TAG, "Updated tracks (count=$count): ${newTracks.map { "${it.type}:${it.id}(${it.title}, sel=${it.isSelected})" }}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update tracks", e)
        }
    }

    private fun updateMediaParams() {
        try {
            val dur = mpv.getPropertyDouble("duration")
            if (dur != null) durationMs.value = (dur * 1000.0).toLong()

            val w = mpv.getPropertyInt("video-params/w")
            if (w != null) videoWidth.value = w
            val h = mpv.getPropertyInt("video-params/h")
            if (h != null) videoHeight.value = h

            videoCodec.value = mpv.getPropertyString("video-codec") ?: ""
            audioCodec.value = mpv.getPropertyString("audio-codec-name") ?: ""
            pixelFormat.value = mpv.getPropertyString("video-params/pixelformat") ?: ""
            colorMatrix.value = mpv.getPropertyString("video-params/colormatrix") ?: ""
            colorPrimaries.value = mpv.getPropertyString("video-params/primaries") ?: ""
            colorGamma.value = mpv.getPropertyString("video-params/gamma") ?: ""

            val fpsVal = mpv.getPropertyDouble("container-fps") ?: mpv.getPropertyDouble("estimated-vf-fps")
            if (fpsVal != null && fpsVal > 0) videoFps.value = fpsVal

            val aspectVal = mpv.getPropertyDouble("video-params/aspect")
            if (aspectVal != null && aspectVal > 0) aspectRatio.value = aspectVal

            hwdecActive.value = mpv.getPropertyString("hwdec-current") ?: "mediacodec"
            audioChannels.value = mpv.getPropertyString("audio-params/channels") ?: ""
            val sRate = mpv.getPropertyInt("audio-params/samplerate")
            if (sRate != null) audioSampleRate.value = sRate
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        try {
            Log.d(TAG, "Destroying MpvPlayerManager")
            if (isSurfaceAttached) {
                isSurfaceAttached = false
                mpv.detachSurface()
            }
            mpv.removeObserver(this)
            mpv.removeLogObserver(this)
            mpv.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy MPV", e)
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        Log.d("MpvLog", "[$prefix] ($level) $text")
    }

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "video-params/w" -> videoWidth.value = value.toInt()
            "video-params/h" -> videoHeight.value = value.toInt()
            "track-list/count" -> updateTracks()
            "audio-params/samplerate" -> audioSampleRate.value = value.toInt()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> {
                isPlaying.value = !value
                if (value) isBuffering.value = false
            }
            "eof-reached" -> eofReached.value = value
            "paused-for-cache" -> {
                isBuffering.value = value && isPlaying.value
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> positionMs.value = (value * 1000).toLong()
            "duration" -> durationMs.value = (value * 1000).toLong()
            "demuxer-cache-duration" -> bufferedDurationMs.value = (value * 1000).toLong()
            "container-fps" -> if (value > 0) videoFps.value = value
            "video-params/aspect" -> if (value > 0) aspectRatio.value = value
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "sid", "aid" -> updateTracks()
            "video-params/pixelformat" -> pixelFormat.value = value
            "video-params/colormatrix" -> colorMatrix.value = value
            "video-params/primaries" -> colorPrimaries.value = value
            "video-params/gamma" -> colorGamma.value = value
            "hwdec-current" -> hwdecActive.value = value
            "audio-params/channels" -> audioChannels.value = value
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, data: MPVNode) {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                eofReached.value = false
                mpv.setPropertyBoolean("sub-visibility", true)
                updateTracks()
                updateMediaParams()
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                eofReached.value = true
            }
            MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                isBuffering.value = false
                updateMediaParams()
            }
        }
    }
}
