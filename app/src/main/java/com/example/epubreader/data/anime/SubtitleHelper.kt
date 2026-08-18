package com.example.epubreader.data.anime

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object SubtitleHelper {
    private const val TAG = "SubtitleHelper"

    data class SubtitleTrackMetadata(
        val isBilingual: Boolean,
        val isChinese: Boolean,
        val isSimplified: Boolean,
        val isTraditional: Boolean,
        val isJapanese: Boolean,
        val isEnglish: Boolean,
        val isBitmap: Boolean, // PGS, VobSub, SUP
        val cleanLabel: String,
        val badge: String
    )

    data class PlayerChapter(
        val title: String,
        val startMs: Long,
        val endMs: Long = 0L
    )

    data class FormattedBilingualSubtitle(
        val primaryText: String,   // Main Chinese subtitle line
        val secondaryText: String? // Secondary Japanese subtitle line
    )

    data class AssStyle(
        val name: String,
        val fontName: String = "",
        val fontSize: Float = 0f,
        val primaryColor: Color? = null,
        val outlineColor: Color? = null,
        val isBold: Boolean = false,
        val alignment: Int = 2
    )

    data class AssEvent(
        val startMs: Long,
        val endMs: Long,
        val style: String,
        val primaryText: String,
        val secondaryText: String? = null,
        val isTop: Boolean = false,
        val isTopRight: Boolean = false,
        val posX: Float? = null,
        val posY: Float? = null,
        val rotZ: Float = 0f,
        val anchorX: Float = 0.5f,
        val anchorY: Float = 0.5f,
        val isVertical: Boolean = false,
        val fontSizeSp: Float? = null,
        val primaryColor: Color? = null,
        val outlineColor: Color? = null,
        val fontName: String? = null,
        val isJapanese: Boolean = false
    )

    class AssSubtitleDocument(
        val events: List<AssEvent>,
        val styles: Map<String, AssStyle> = emptyMap(),
        val extractedChapters: List<PlayerChapter> = emptyList()
    ) {
        fun findActiveEvents(positionMs: Long): List<AssEvent> {
            return events.filter { positionMs in it.startMs..it.endMs }
        }

        fun findActiveEvent(positionMs: Long): AssEvent? {
            val active = findActiveEvents(positionMs)
            if (active.isEmpty()) return null
            if (active.size == 1) return active.first()

            val cnEvents = active.filter { hasChineseHanzi(it.primaryText) && !hasJapaneseKana(it.primaryText) }
            val jpEvents = active.filter { hasJapaneseKana(it.primaryText) || it.secondaryText != null }

            val primary = cnEvents.firstOrNull()?.primaryText ?: active.first().primaryText
            val secondary = jpEvents.firstOrNull()?.let { it.secondaryText ?: it.primaryText }
                ?: active.firstOrNull { it.primaryText != primary }?.primaryText

            return AssEvent(
                startMs = active.minOf { it.startMs },
                endMs = active.maxOf { it.endMs },
                style = "Merged",
                primaryText = primary,
                secondaryText = if (secondary != primary) secondary else null,
                isTop = active.any { it.isTop },
                primaryColor = active.firstOrNull { it.primaryColor != null }?.primaryColor,
                outlineColor = active.firstOrNull { it.outlineColor != null }?.outlineColor
            )
        }
    }

    class BilingualSubtitleState {
        var chineseText by androidx.compose.runtime.mutableStateOf("")
        var japaneseText by androidx.compose.runtime.mutableStateOf("")
        var otherText by androidx.compose.runtime.mutableStateOf("")
        var chineseUpdateTimeMs by androidx.compose.runtime.mutableLongStateOf(0L)
        var japaneseUpdateTimeMs by androidx.compose.runtime.mutableLongStateOf(0L)
        var lastUpdateTimeMs by androidx.compose.runtime.mutableLongStateOf(0L)

        fun updateFromCues(cues: List<androidx.media3.common.text.Cue>, positionMs: Long) {
            if (cues.isEmpty()) {
                if (positionMs - lastUpdateTimeMs > 400L || positionMs < lastUpdateTimeMs - 1000L) {
                    chineseText = ""
                    japaneseText = ""
                    otherText = ""
                }
                return
            }

            val textList = cues.mapNotNull { it.text?.toString() }
            val rawLines = textList.flatMap { cleanAssText(it).lines() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            if (rawLines.isEmpty()) {
                if (positionMs - lastUpdateTimeMs > 400L || positionMs < lastUpdateTimeMs - 1000L) {
                    chineseText = ""
                    japaneseText = ""
                    otherText = ""
                }
                return
            }

            val kanaLines = rawLines.filter { hasJapaneseKana(it) }
            val nonKanaLines = rawLines.filter { !hasJapaneseKana(it) }

            if (kanaLines.isNotEmpty()) {
                japaneseText = kanaLines.first()
                japaneseUpdateTimeMs = positionMs
            }
            if (nonKanaLines.isNotEmpty()) {
                chineseText = nonKanaLines.first()
                chineseUpdateTimeMs = positionMs
            } else if (rawLines.size >= 2 && kanaLines.isEmpty()) {
                chineseText = rawLines[0]
                japaneseText = rawLines[1]
                chineseUpdateTimeMs = positionMs
                japaneseUpdateTimeMs = positionMs
            }

            lastUpdateTimeMs = positionMs
        }

        fun clear() {
            chineseText = ""
            japaneseText = ""
            otherText = ""
        }

        fun updateFromAssDoc(event: AssEvent?, positionMs: Long) {
            if (event == null) {
                clear()
                return
            }
            chineseText = event.primaryText
            japaneseText = event.secondaryText ?: ""
            otherText = ""
            lastUpdateTimeMs = positionMs
        }
    }

    fun analyzeTrack(
        rawLabel: String?,
        language: String?,
        sampleMimeType: String?,
        trackIndex: Int = 0
    ): SubtitleTrackMetadata = analyzeSubtitleTrack(rawLabel, language, sampleMimeType)

    /**
     * Parses ASS hexadecimal color &H[AA]BBGGRR& or &HBBGGRR& into Compose Color.
     */
    fun parseAssColor(colorStr: String): Color? {
        val clean = colorStr.trim()
            .removePrefix("&H").removePrefix("&h")
            .removeSuffix("&").removeSuffix("#")
        if (clean.isBlank()) return null
        return try {
            val hex = clean.toLong(16)
            if (clean.length <= 6) {
                // BGR format: 0xBBGGRR
                val b = ((hex shr 16) and 0xFF).toInt()
                val g = ((hex shr 8) and 0xFF).toInt()
                val r = (hex and 0xFF).toInt()
                Color(r, g, b, 255)
            } else {
                // AABBGGRR format (ASS alpha: 00 = opaque 255, FF = transparent 0)
                val aRaw = ((hex shr 24) and 0xFF).toInt()
                val b = ((hex shr 16) and 0xFF).toInt()
                val g = ((hex shr 8) and 0xFF).toInt()
                val r = (hex and 0xFF).toInt()
                val a = (255 - aRaw).coerceIn(0, 255)
                Color(r, g, b, a)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts foreground color span from Media3 Cue.
     */
    fun extractColorFromCue(cue: androidx.media3.common.text.Cue): Color? {
        val text = cue.text ?: return null
        if (text is android.text.Spanned) {
            val spans = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java)
            if (spans != null && spans.isNotEmpty()) {
                val argb = spans[0].foregroundColor
                return Color(argb)
            }
        }
        return null
    }

    /**
     * Parses ASS header bytes from ExoPlayer Format.initializationData[0] into styles.
     */
    fun extractStylesFromInitializationData(initData: List<ByteArray>?): Map<String, AssStyle> {
        if (initData.isNullOrEmpty()) return emptyMap()
        return try {
            val headerStr = String(initData[0], StandardCharsets.UTF_8)
            val doc = parseAssDocument(headerStr)
            doc.styles
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    /**
     * Extracts inline color override tags: \c&H...& (Primary fill) and \3c&H...& (Outline).
     */
    fun extractInlineColors(rawText: String): Pair<Color?, Color?> {
        var primaryColor: Color? = null
        var outlineColor: Color? = null

        val cMatch = Regex("""\\(?:c|1c)&H([0-9a-fA-F]+)&?""").find(rawText)
        if (cMatch != null) {
            primaryColor = parseAssColor(cMatch.groupValues[1])
        }

        val outlineMatch = Regex("""\\3c&H([0-9a-fA-F]+)&?""").find(rawText)
        if (outlineMatch != null) {
            outlineColor = parseAssColor(outlineMatch.groupValues[1])
        }

        return Pair(primaryColor, outlineColor)
    }

    /**
     * Extracts normalized (x, y) coordinates from \pos(x, y) or \move(x1, y1, x2, y2).
     */
    fun extractPosOrMove(rawText: String): Pair<Float?, Float?> {
        val posMatch = Regex("""\\pos\s*\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)""").find(rawText)
        if (posMatch != null) {
            val x = posMatch.groupValues[1].toFloatOrNull() ?: 0f
            val y = posMatch.groupValues[2].toFloatOrNull() ?: 0f
            return Pair(x / 1920f, y / 1080f)
        }
        val moveMatch = Regex("""\\move\s*\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*,\s*([-\d.]+)\s*,\s*([-\d.]+)\s*""").find(rawText)
        if (moveMatch != null) {
            val x = moveMatch.groupValues[3].toFloatOrNull() ?: moveMatch.groupValues[1].toFloatOrNull() ?: 0f
            val y = moveMatch.groupValues[4].toFloatOrNull() ?: moveMatch.groupValues[2].toFloatOrNull() ?: 0f
            return Pair(x / 1920f, y / 1080f)
        }
        return Pair(null, null)
    }

    data class PosAndTransform(
        val posX: Float?,
        val posY: Float?,
        val rotZ: Float = 0f,
        val fontSizeSp: Float? = null,
        val anchorX: Float = 0.5f,
        val anchorY: Float = 0.5f,
        val isVertical: Boolean = false,
        val fontName: String? = null
    )

    fun extractPosAndTransform(rawText: String): PosAndTransform {
        val (posX, posY) = extractPosOrMove(rawText)
        var rotZ = 0f
        val frzMatch = Regex("""\\(?:frz|fr)([-\d.]+)""").find(rawText)
        if (frzMatch != null) {
            rotZ = frzMatch.groupValues[1].toFloatOrNull() ?: 0f
        }
        var fsSp: Float? = null
        val fsMatch = Regex("""\\fs([\d.]+)""").find(rawText)
        if (fsMatch != null) {
            val rawFs = fsMatch.groupValues[1].toFloatOrNull()
            if (rawFs != null && rawFs > 0) {
                fsSp = (rawFs * 0.35f).coerceIn(12f, 42f)
            }
        }
        val fnMatch = Regex("""\\fn([^\\}]+)""").find(rawText)
        val fontName = fnMatch?.groupValues?.get(1)?.trim()

        var anchorX = 0.5f
        var anchorY = 0.5f
        val anMatch = Regex("""\\an([1-9])""").find(rawText)
        val aLegacyMatch = Regex("""\\a([1-9]|1[0-1])""").find(rawText)
        if (anMatch != null) {
            when (anMatch.groupValues[1].toIntOrNull()) {
                7 -> { anchorX = 0.0f; anchorY = 0.0f } // Top-Left
                8 -> { anchorX = 0.5f; anchorY = 0.0f } // Top-Center
                9 -> { anchorX = 1.0f; anchorY = 0.0f } // Top-Right
                4 -> { anchorX = 0.0f; anchorY = 0.5f } // Mid-Left
                5 -> { anchorX = 0.5f; anchorY = 0.5f } // Center
                6 -> { anchorX = 1.0f; anchorY = 0.5f } // Mid-Right
                1 -> { anchorX = 0.0f; anchorY = 1.0f } // Bot-Left
                2 -> { anchorX = 0.5f; anchorY = 1.0f } // Bot-Center
                3 -> { anchorX = 1.0f; anchorY = 1.0f } // Bot-Right
            }
        } else if (aLegacyMatch != null) {
            when (aLegacyMatch.groupValues[1].toIntOrNull()) {
                5 -> { anchorX = 0.0f; anchorY = 0.0f }
                6 -> { anchorX = 0.5f; anchorY = 0.0f }
                7 -> { anchorX = 1.0f; anchorY = 0.0f }
                9 -> { anchorX = 0.0f; anchorY = 0.5f }
                10 -> { anchorX = 0.5f; anchorY = 0.5f }
                11 -> { anchorX = 1.0f; anchorY = 0.5f }
                1 -> { anchorX = 0.0f; anchorY = 1.0f }
                2 -> { anchorX = 0.5f; anchorY = 1.0f }
                3 -> { anchorX = 1.0f; anchorY = 1.0f }
            }
        }

        val isVertical = (fontName != null && fontName.startsWith("@")) || (Math.abs(rotZ + 90f) < 5f && fontName != null && fontName.contains("@"))

        return PosAndTransform(
            posX = posX,
            posY = posY,
            rotZ = rotZ,
            fontSizeSp = fsSp,
            anchorX = anchorX,
            anchorY = anchorY,
            isVertical = isVertical,
            fontName = fontName
        )
    }

    /**
     * Checks if the subtitle text is a Sign / OST track / BGM title (e.g. "19 木陰の憩", "Track 01 ...", "BGM: ...").
     */
    fun isSignOrTrackTitle(rawText: String): Boolean {
        val t = cleanAssText(rawText).trim()
        if (t.isBlank()) return false
        return t.matches(Regex("""^(?:Track\s*\d+|\d{1,3}\s+[\u4e00-\u9fa5\u3040-\u30ffA-Za-z]+|BGM[:：]|OST[:：]|挿入歌[:：]|插曲[:：]|片头曲[:：]|片尾曲[:：]).*"""))
    }

    /**
     * Checks if the subtitle text has top-alignment tags (excluding positioned sign objects).
     */
    fun isTopAlignedTag(rawText: String): Boolean {
        if (rawText.contains("\\pos") || rawText.contains("\\move")) return false
        if (isSignOrTrackTitle(rawText)) return true
        return rawText.contains("\\an7") || rawText.contains("\\an8") || rawText.contains("\\an9") ||
                rawText.contains("{\\an7") || rawText.contains("{\\an8") || rawText.contains("{\\an9")
    }

    fun isTopRightAlignedTag(rawText: String): Boolean {
        if (isSignOrTrackTitle(rawText)) return true
        val (posX, posY) = extractPosOrMove(rawText)
        if (posY != null && posY < 0.38f && posX != null && posX > 0.55f) return true
        return rawText.contains("\\an9") || rawText.contains("{\\an9")
    }

    /**
     * Intelligently analyzes subtitle track title, language, and MIME type to produce
     * clean human-friendly badges (e.g. "简日双语", "繁体中文", "特效双语").
     */
    fun analyzeSubtitleTrack(label: String?, language: String?, mimeType: String?): SubtitleTrackMetadata {
        val raw = "${label ?: ""} ${language ?: ""} ${mimeType ?: ""}".lowercase()
        val origLabel = label?.trim() ?: ""

        val isBitmap = raw.contains("pgs") || raw.contains("vobsub") || raw.contains("dvd_subtitle") ||
                raw.contains("subrip") == false && (raw.contains("image") || raw.contains("bitmap") || raw.contains("sup"))

        val isSimplified = raw.contains("chs") || raw.contains("sc") || raw.contains("gb") ||
                raw.contains("hans") || raw.contains("zh-cn") || raw.contains("简体") || raw.contains("简中") || raw.contains("简日") || raw.contains("简繁")
        val isTraditional = raw.contains("cht") || raw.contains("tc") || raw.contains("big5") ||
                raw.contains("hant") || raw.contains("zh-tw") || raw.contains("zh-hk") || raw.contains("繁体") || raw.contains("繁中") || raw.contains("繁日")
        val isChinese = isSimplified || isTraditional || raw.contains("chi") || raw.contains("zho") ||
                raw.contains("zh") || raw.contains("chinese") || raw.contains("中") || raw.contains("双语")
        val isJapanese = raw.contains("jpn") || raw.contains("ja") || raw.contains("japanese") || raw.contains("日")
        val isEnglish = raw.contains("eng") || raw.contains("en") || raw.contains("english") || raw.contains("英")

        val isBilingual = (isChinese && isJapanese) || raw.contains("双语") || raw.contains("bilingual") ||
                raw.contains("简日") || raw.contains("繁日") || (raw.contains("chs") && raw.contains("jpn")) ||
                raw.contains("双语字幕") || raw.contains("中日")

        val cleanLabel = when {
            origLabel.isNotBlank() && !origLabel.startsWith("Track", ignoreCase = true) && !origLabel.startsWith("Subtitle", ignoreCase = true) -> {
                origLabel
            }
            isBilingual && isSimplified -> "简日双语"
            isBilingual && isTraditional -> "繁日双语"
            isBilingual -> "中日双语"
            isSimplified -> "简体中文"
            isTraditional -> "繁体中文"
            isChinese -> "中文对白"
            isJapanese -> "日文字幕"
            isEnglish -> "英文字幕"
            else -> if (language.isNullOrBlank()) "未知语言" else language.uppercase()
        }

        val badge = when {
            isBitmap -> "PGS图像"
            isBilingual && isSimplified -> "简日双语"
            isBilingual && isTraditional -> "繁日双语"
            isBilingual -> "双语特效"
            isSimplified -> "简体中文"
            isTraditional -> "繁体中文"
            isChinese -> "中文字幕"
            isJapanese -> "日文字幕"
            isEnglish -> "英文字幕"
            raw.contains("ass") || raw.contains("ssa") -> "ASS特效"
            raw.contains("srt") -> "SRT字幕"
            else -> "外挂字幕"
        }

        return SubtitleTrackMetadata(
            isBilingual = isBilingual,
            isChinese = isChinese,
            isSimplified = isSimplified,
            isTraditional = isTraditional,
            isJapanese = isJapanese,
            isEnglish = isEnglish,
            isBitmap = isBitmap,
            cleanLabel = cleanLabel,
            badge = badge
        )
    }

    /**
     * Converts a raw subtitle stream into a clean UTF-8 local file.
     */
    fun prepareExternalSubtitle(
        context: Context,
        rawUri: Uri,
        webdavUser: String? = null,
        webdavPass: String? = null
    ): Pair<Uri, String>? {
        return try {
            val scheme = rawUri.scheme?.lowercase()
            val rawBytes = if (scheme == "http" || scheme == "https") {
                val client = OkHttpClient.Builder().build()
                val reqBuilder = Request.Builder().url(rawUri.toString())
                if (!webdavUser.isNullOrBlank() && !webdavPass.isNullOrBlank()) {
                    reqBuilder.header("Authorization", Credentials.basic(webdavUser, webdavPass))
                }
                val response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download subtitle: ${response.code}")
                    return null
                }
                response.body?.bytes() ?: return null
            } else {
                context.contentResolver.openInputStream(rawUri)?.use { it.readBytes() } ?: return null
            }

            if (rawBytes.isEmpty()) return null

            val decodedString = when {
                rawBytes.size >= 3 && rawBytes[0] == 0xEF.toByte() && rawBytes[1] == 0xBB.toByte() && rawBytes[2] == 0xBF.toByte() -> {
                    String(rawBytes, 3, rawBytes.size - 3, StandardCharsets.UTF_8)
                }
                rawBytes.size >= 2 && rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xFE.toByte() -> {
                    String(rawBytes, 2, rawBytes.size - 2, StandardCharsets.UTF_16LE)
                }
                rawBytes.size >= 2 && rawBytes[0] == 0xFE.toByte() && rawBytes[1] == 0xFF.toByte() -> {
                    String(rawBytes, 2, rawBytes.size - 2, StandardCharsets.UTF_16BE)
                }
                else -> {
                    try {
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                        decoder.decode(java.nio.ByteBuffer.wrap(rawBytes)).toString()
                    } catch (e: Exception) {
                        try {
                            String(rawBytes, Charset.forName("GB18030"))
                        } catch (e2: Exception) {
                            try {
                                String(rawBytes, Charset.forName("Big5"))
                            } catch (e3: Exception) {
                                String(rawBytes, StandardCharsets.ISO_8859_1)
                            }
                        }
                    }
                }
            }

            var cleanContent = decodedString.replace("\uFEFF", "")
            if (!cleanContent.contains("-->") && !cleanContent.contains("Dialogue:")) {
                if (cleanContent.contains("00:") || cleanContent.contains("01:")) {
                    cleanContent = "WEBVTT\n\n$cleanContent"
                }
            }

            val cacheDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
            val subFile = File(cacheDir, "cached_sub_${System.currentTimeMillis()}.ass")
            subFile.writeText(cleanContent, StandardCharsets.UTF_8)

            Log.d(TAG, "Prepared external UTF-8 subtitle: ${subFile.absolutePath}, length: ${cleanContent.length}")
            Pair(Uri.fromFile(subFile), cleanContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing external subtitle: ${e.message}", e)
            null
        }
    }

    fun prepareExternalSubtitle(
        context: Context,
        rawUrl: String,
        webDavAuth: Pair<String, String>? = null
    ): Pair<Uri, String>? {
        return prepareExternalSubtitle(
            context = context,
            rawUri = Uri.parse(rawUrl),
            webdavUser = webDavAuth?.first,
            webdavPass = webDavAuth?.second
        )
    }

    /**
     * Extracts embedded ASS subtitle document from video file or remote stream using MediaExtractor.
     */
    fun extractEmbeddedAssDocument(
        context: Context,
        videoUrl: String,
        webDavAuth: Pair<String, String>? = null
    ): AssSubtitleDocument? {
        val uri = Uri.parse(videoUrl)
        val headers = mutableMapOf<String, String>()
        if (webDavAuth != null && webDavAuth.first.isNotBlank()) {
            val credentials = Credentials.basic(webDavAuth.first, webDavAuth.second)
            headers["Authorization"] = credentials
        }

        val extractor = android.media.MediaExtractor()
        return try {
            if (headers.isNotEmpty()) {
                extractor.setDataSource(context, uri, headers)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var subTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.contains("subrip", ignoreCase = true) || mime.contains("ssa", ignoreCase = true) ||
                    mime.contains("ass", ignoreCase = true) || mime.contains("text/x-ssa", ignoreCase = true)) {
                    subTrackIndex = i
                    break
                }
            }
            if (subTrackIndex == -1) return null

            val format = extractor.getTrackFormat(subTrackIndex)
            var headerStr = ""
            if (format.containsKey("csd-0")) {
                val csd0 = format.getByteBuffer("csd-0")
                if (csd0 != null) {
                    val bytes = ByteArray(csd0.remaining())
                    csd0.get(bytes)
                    headerStr = String(bytes, StandardCharsets.UTF_8)
                }
            }

            extractor.selectTrack(subTrackIndex)
            val buffer = java.nio.ByteBuffer.allocate(1024 * 64)
            val sb = StringBuilder()
            if (headerStr.isNotBlank()) {
                sb.append(headerStr).append("\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
            }

            fun formatAssTime(ms: Long): String {
                val h = ms / 3600000
                val m = (ms % 3600000) / 60000
                val s = (ms % 60000) / 1000
                val cs = (ms % 1000) / 10
                return String.format(java.util.Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
            }

            var totalSamples = 0
            var subCount = 0
            while (totalSamples < 120000 && subCount < 6000) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize > 0) {
                    val timeUs = extractor.sampleTime
                    val bytes = ByteArray(sampleSize)
                    buffer.get(bytes)
                    val sampleStr = String(bytes, StandardCharsets.UTF_8).trim()
                    if (sampleStr.isNotBlank()) {
                        if (sampleStr.startsWith("Dialogue:", ignoreCase = true)) {
                            sb.append(sampleStr).append("\n")
                        } else {
                            val startMs = timeUs / 1000
                            val parts = sampleStr.split(",", limit = 9)
                            if (parts.size >= 9) {
                                val layer = parts[1]
                                val style = parts[2]
                                val name = parts[3]
                                val marginL = parts[4]
                                val marginR = parts[5]
                                val marginV = parts[6]
                                val effect = parts[7]
                                val text = parts[8]
                                sb.append("Dialogue: $layer,${formatAssTime(startMs)},${formatAssTime(startMs + 4000)},$style,$name,$marginL,$marginR,$marginV,$effect,$text\n")
                            } else {
                                sb.append("Dialogue: 0,${formatAssTime(startMs)},${formatAssTime(startMs + 4000)},Default,,0,0,0,,${sampleStr}\n")
                            }
                        }
                        subCount++
                    }
                }
                totalSamples++
                if (!extractor.advance()) break
            }

            if (sb.isNotBlank()) {
                parseAssDocument(sb.toString())
            } else null
        } catch (e: Throwable) {
            Log.e(TAG, "extractEmbeddedAssDocument failed: ${e.message}")
            null
        } finally {
            try { extractor.release() } catch (e: Throwable) {}
        }
    }

    /**
     * Directly extracts ASS style header from the first few megabytes of an MKV file or HTTP stream.
     */
    fun extractAssStylesFromMkv(
        context: Context,
        videoUrl: String,
        webDavAuth: Pair<String, String>? = null
    ): Map<String, AssStyle> {
        try {
            val bytes = if (videoUrl.startsWith("http://", ignoreCase = true) || videoUrl.startsWith("https://", ignoreCase = true)) {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val requestBuilder = okhttp3.Request.Builder()
                    .url(videoUrl)
                    .header("Range", "bytes=0-6291456")
                if (webDavAuth != null && webDavAuth.first.isNotBlank()) {
                    requestBuilder.header("Authorization", okhttp3.Credentials.basic(webDavAuth.first, webDavAuth.second))
                }
                client.newCall(requestBuilder.build()).execute().use { resp ->
                    resp.body?.bytes()
                }
            } else {
                val file = if (videoUrl.startsWith("file://")) File(Uri.parse(videoUrl).path ?: "") else File(videoUrl)
                if (file.exists()) {
                    file.inputStream().use { input ->
                        val buf = ByteArray(6 * 1024 * 1024)
                        val read = input.read(buf)
                        if (read > 0) buf.copyOf(read) else null
                    }
                } else null
            } ?: return emptyMap()

            val rawStr = String(bytes, StandardCharsets.ISO_8859_1)
            val scriptIdx = rawStr.indexOf("[Script Info]")
            if (scriptIdx == -1) return emptyMap()

            val eventsIdx = rawStr.indexOf("[Events]", scriptIdx)
            val headerBlock = if (eventsIdx != -1) {
                rawStr.substring(scriptIdx, eventsIdx)
            } else {
                rawStr.substring(scriptIdx, (scriptIdx + 65536).coerceAtMost(rawStr.length))
            }

            val headerBytes = headerBlock.toByteArray(StandardCharsets.ISO_8859_1)
            val utf8Header = String(headerBytes, StandardCharsets.UTF_8)
            val doc = parseAssDocument(utf8Header)
            return doc.styles
        } catch (e: Throwable) {
            Log.e(TAG, "extractAssStylesFromMkv failed: ${e.message}")
            return emptyMap()
        }
    }

    /**
     * Parses an ASS document into memory with styles, colors, alignments, and chapter markers.
     */
    fun parseAssDocument(content: String): AssSubtitleDocument {
        val events = mutableListOf<AssEvent>()
        val styles = mutableMapOf<String, AssStyle>()
        val timeRegex = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2,3})""")

        fun parseTime(timeStr: String): Long {
            val m = timeRegex.matchEntire(timeStr.trim()) ?: return 0L
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            val s = m.groupValues[3].toLong()
            val msStr = m.groupValues[4]
            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
            return (h * 3600 + min * 60 + s) * 1000 + ms
        }

        var currentSection = ""
        var eventFormatFields: List<String> = emptyList()
        var styleFormatFields: List<String> = emptyList()

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).lowercase()
                continue
            }

            if (currentSection == "v4+ styles" || currentSection == "v4 styles") {
                if (line.startsWith("Format:", ignoreCase = true)) {
                    styleFormatFields = line.substringAfter(":").split(",").map { it.trim().lowercase() }
                    continue
                }
                if (line.startsWith("Style:", ignoreCase = true)) {
                    val parts = line.substringAfter(":").split(",", limit = if (styleFormatFields.isNotEmpty()) styleFormatFields.size else 25)
                    val nameIdx = styleFormatFields.indexOfFirst { it.contains("name") && !it.contains("font") }.let { if (it != -1) it else 0 }
                    val fontIdx = styleFormatFields.indexOfFirst { it.contains("font") }.let { if (it != -1) it else 1 }
                    val sizeIdx = styleFormatFields.indexOfFirst { it.contains("size") }.let { if (it != -1) it else 2 }
                    val primaryIdx = styleFormatFields.indexOfFirst { it.contains("primary") }.let { if (it != -1) it else 3 }
                    val outlineIdx = styleFormatFields.indexOfFirst { it.contains("outline") }.let { if (it != -1) it else 5 }

                    val name = parts.getOrElse(nameIdx) { "Default" }.trim()
                    val fontName = parts.getOrElse(fontIdx) { "" }.trim()
                    val fontSize = parts.getOrElse(sizeIdx) { "0" }.trim().toFloatOrNull() ?: 0f
                    val primaryColor = parseAssColor(parts.getOrElse(primaryIdx) { "" })
                    val outlineColor = parseAssColor(parts.getOrElse(outlineIdx) { "" })

                    val alignIdx = styleFormatFields.indexOfFirst { it.contains("alignment") || it.contains("align") }.let { if (it != -1) it else -1 }
                    val alignmentVal = if (alignIdx != -1) parts.getOrElse(alignIdx) { "2" }.trim().toIntOrNull() ?: 2 else 2

                    styles[name.lowercase()] = AssStyle(
                        name = name,
                        fontName = fontName,
                        fontSize = fontSize,
                        primaryColor = primaryColor,
                        outlineColor = outlineColor,
                        alignment = alignmentVal
                    )
                }
            } else if (currentSection == "events") {
                if (line.startsWith("Format:", ignoreCase = true)) {
                    eventFormatFields = line.substringAfter(":").split(",").map { it.trim().lowercase() }
                    continue
                }

                if (line.startsWith("Dialogue:", ignoreCase = true)) {
                    val fieldCount = if (eventFormatFields.isNotEmpty()) eventFormatFields.size else 10
                    val parts = line.substringAfter(":").split(",", limit = fieldCount)
                    if (parts.size >= 9) {
                        val startIdx = eventFormatFields.indexOf("start").let { if (it != -1) it else 1 }
                        val endIdx = eventFormatFields.indexOf("end").let { if (it != -1) it else 2 }
                        val styleIdx = eventFormatFields.indexOf("style").let { if (it != -1) it else 3 }
                        val textIdx = eventFormatFields.indexOf("text").let { if (it != -1) it else parts.size - 1 }

                        val startMs = parseTime(parts.getOrElse(startIdx) { "0:00:00.00" })
                        val endMs = parseTime(parts.getOrElse(endIdx) { "0:00:00.00" })
                        val styleName = parts.getOrElse(styleIdx) { "Default" }.trim()
                        val rawText = parts.getOrElse(textIdx) { "" }

                        val transform = extractPosAndTransform(rawText)
                        val isTopRight = isTopRightAlignedTag(rawText) || isSignOrTrackTitle(rawText)
                        val isTop = isTopRight || isTopAlignedTag(rawText) || styleName.contains("top", ignoreCase = true) || styleName.contains("banner", ignoreCase = true)
                        val (inlinePrimary, inlineOutline) = extractInlineColors(rawText)
                        val styleObj = styles[styleName.lowercase()]

                        val effectivePrimaryColor = inlinePrimary ?: styleObj?.primaryColor
                        val effectiveOutlineColor = inlineOutline ?: styleObj?.outlineColor
                        val effectiveFontName = transform.fontName ?: styleObj?.fontName

                        val splitLines = splitBilingualLines(rawText)
                        val isStyleJp = styleName.contains("jp", ignoreCase = true) || styleName.contains("日", ignoreCase = true) || styleName.contains("kana", ignoreCase = true)
                        if (splitLines.isNotEmpty()) {
                            if (splitLines.size == 1) {
                                val clean = splitLines[0]
                                val isJp = isJapaneseText(clean) || isStyleJp
                                events.add(
                                    AssEvent(
                                        startMs = startMs,
                                        endMs = endMs,
                                        style = styleName,
                                        primaryText = clean,
                                        secondaryText = null,
                                        isTop = isTop,
                                        isTopRight = isTopRight,
                                        posX = transform.posX,
                                        posY = transform.posY,
                                        rotZ = transform.rotZ,
                                        anchorX = transform.anchorX,
                                        anchorY = transform.anchorY,
                                        isVertical = transform.isVertical,
                                        fontSizeSp = transform.fontSizeSp,
                                        primaryColor = effectivePrimaryColor,
                                        outlineColor = effectiveOutlineColor,
                                        fontName = effectiveFontName,
                                        isJapanese = isJp
                                    )
                                )
                            } else {
                                val cn = if (isStyleJp) splitLines.getOrNull(1) ?: splitLines[0] else splitLines[0]
                                val jp = if (isStyleJp) splitLines[0] else (splitLines.getOrNull(1) ?: "")
                                events.add(
                                    AssEvent(
                                        startMs = startMs,
                                        endMs = endMs,
                                        style = styleName,
                                        primaryText = cn,
                                        secondaryText = jp,
                                        isTop = isTop,
                                        isTopRight = isTopRight,
                                        posX = transform.posX,
                                        posY = transform.posY,
                                        rotZ = transform.rotZ,
                                        anchorX = transform.anchorX,
                                        anchorY = transform.anchorY,
                                        isVertical = transform.isVertical,
                                        fontSizeSp = transform.fontSizeSp,
                                        primaryColor = effectivePrimaryColor,
                                        outlineColor = effectiveOutlineColor,
                                        fontName = effectiveFontName,
                                        isJapanese = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val chapters = extractChaptersFromAss(events, content)
        return AssSubtitleDocument(events = events, styles = styles, extractedChapters = chapters)
    }

    /**
     * Cleans up ASS/SSA formatting tags, removes vector drawings (`{\p1}...{\p0}`),
     * and normalizes newlines.
     */
    fun cleanAssText(rawText: String): String {
        if (rawText.isBlank()) return ""

        var s = rawText
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")

        // Remove ASS drawing commands: {\p1}m 0 0 ... {\p0}
        s = s.replace(Regex("""\{\\p[1-9]\}.*?(\{\\p0\}|$)""", RegexOption.DOT_MATCHES_ALL), "")
        // Remove all ASS style tags like {\pos(100,200)}, {\c&H...&}, {\fn...}, {\fs...}, {\r...}, {\fad(...)}
        s = s.replace(Regex("""\{[^}]*\}"""), "")

        val trimmed = s.trim()
        // If there are raw vector coordinates remaining (e.g. "m 35.43 9.05 b 35.71...")
        if (trimmed.matches(Regex("""^([mblspc]\s+[-0-9.\s]+)+$""")) ||
            (trimmed.startsWith("m ") && Regex("""m\s+[-0-9.]+\s+[-0-9.]+""").containsMatchIn(trimmed) && !hasChineseHanzi(trimmed) && !hasJapaneseKana(trimmed))) {
            return ""
        }

        return trimmed
    }

    /**
     * Splits bilingual ASS / Cue strings on \N, \n, and \r\n boundaries.
     */
    fun splitBilingualLines(rawText: String): List<String> {
        val normalized = rawText
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val rawLines = normalized.split("\n")
        val result = mutableListOf<String>()

        for (line in rawLines) {
            val clean = cleanAssText(line).trim()
            if (clean.isNotBlank()) {
                result.add(clean)
            }
        }
        return result
    }

    fun isJapaneseText(text: String): Boolean = hasJapaneseKana(text)

    fun isChineseText(text: String): Boolean = hasChineseHanzi(text) && !hasJapaneseKana(text)

    /**
     * Extracts chapter cue markers from ASS events / comments.
     */
    private fun extractChaptersFromAss(events: List<AssEvent>, rawContent: String): List<PlayerChapter> {
        val chapters = mutableListOf<PlayerChapter>()
        val chapterRegex = Regex("""(?i)^(OP|Opening|ED|Ending|Part\s*[A-Z0-9]|次回予告|第\s*\d+\s*幕|A\s*Part|B\s*Part|Aパート|Bパート|Chapter\s*\d+)""")

        for (ev in events) {
            val text = ev.primaryText.trim()
            if (ev.style.contains("chapter", ignoreCase = true) ||
                ev.style.contains("title", ignoreCase = true) ||
                chapterRegex.containsMatchIn(text)
            ) {
                if (chapters.none { Math.abs(it.startMs - ev.startMs) < 3000L }) {
                    chapters.add(PlayerChapter(title = text.ifBlank { "章节 #${chapters.size + 1}" }, startMs = ev.startMs))
                }
            }
        }

        for (line in rawContent.lines()) {
            if (line.startsWith("Comment:", ignoreCase = true) && line.contains("CHAPTER", ignoreCase = true)) {
                val nameMatch = Regex("""CHAPTER\d+NAME=(.+)""", RegexOption.IGNORE_CASE).find(line)
                val timeMatch = Regex("""CHAPTER\d+=(\d{2}:\d{2}:\d{2}[\.:]\d+)""", RegexOption.IGNORE_CASE).find(line)
                if (nameMatch != null && timeMatch != null) {
                    val name = nameMatch.groupValues[1].trim()
                    val timeStr = timeMatch.groupValues[1]
                    val tRegex = Regex("""(\d+):(\d{2}):(\d{2})[\.:](\d{2,3})""").matchEntire(timeStr)
                    if (tRegex != null) {
                        val h = tRegex.groupValues[1].toLong()
                        val m = tRegex.groupValues[2].toLong()
                        val s = tRegex.groupValues[3].toLong()
                        val ms = tRegex.groupValues[4].let { if (it.length == 2) it.toLong() * 10 else it.toLong() }
                        val t = (h * 3600 + m * 60 + s) * 1000 + ms
                        if (chapters.none { Math.abs(it.startMs - t) < 3000L }) {
                            chapters.add(PlayerChapter(title = name, startMs = t))
                        }
                    }
                }
            }
        }

        return chapters.sortedBy { it.startMs }
    }

    /**
     * Checks if a string contains Japanese Kana characters (Hiragana or Katakana).
     */
    fun hasJapaneseKana(text: String): Boolean {
        for (ch in text) {
            val ub = Character.UnicodeBlock.of(ch)
            if (ub == Character.UnicodeBlock.HIRAGANA ||
                ub == Character.UnicodeBlock.KATAKANA ||
                ub == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a string contains Chinese Hanzi characters (CJK Unified Ideographs).
     */
    fun hasChineseHanzi(text: String): Boolean {
        for (ch in text) {
            val ub = Character.UnicodeBlock.of(ch)
            if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Intelligently parses cue text into primary (Chinese) and secondary (Japanese) lines.
     */
    fun formatBilingualCues(cues: List<CharSequence>): FormattedBilingualSubtitle? {
        if (cues.isEmpty()) return null

        val rawLines = cues.flatMap { cue ->
            splitBilingualLines(cue.toString())
        }.filter { it.isNotBlank() }.distinct()

        if (rawLines.isEmpty()) return null

        if (rawLines.size == 1) {
            val singleLine = rawLines[0]
            return FormattedBilingualSubtitle(primaryText = singleLine, secondaryText = null)
        }

        val chineseLines = mutableListOf<String>()
        val japaneseLines = mutableListOf<String>()
        val otherLines = mutableListOf<String>()

        for (line in rawLines) {
            when {
                hasJapaneseKana(line) -> japaneseLines.add(line)
                hasChineseHanzi(line) -> chineseLines.add(line)
                else -> otherLines.add(line)
            }
        }

        val primary = when {
            chineseLines.isNotEmpty() -> chineseLines.joinToString("\n")
            otherLines.isNotEmpty() -> otherLines.joinToString("\n")
            japaneseLines.isNotEmpty() -> japaneseLines.joinToString("\n")
            else -> rawLines.first()
        }

        val secondary = when {
            chineseLines.isNotEmpty() && japaneseLines.isNotEmpty() -> japaneseLines.joinToString("\n")
            chineseLines.isNotEmpty() && otherLines.isNotEmpty() && !primary.contains(otherLines.first()) -> otherLines.joinToString("\n")
            japaneseLines.isNotEmpty() && otherLines.isNotEmpty() && !primary.contains(otherLines.first()) -> otherLines.joinToString("\n")
            rawLines.size > 1 && !primary.contains(rawLines.last()) -> rawLines.last()
            else -> null
        }

        return FormattedBilingualSubtitle(
            primaryText = primary,
            secondaryText = secondary
        )
    }

    const val SP_RIBBON_CONTAINER_DRAWING = "m 401.48 55.55 l 14.33 55.55 b 11.7 55.55 9.43 53.69 8.92 51.1 l 0.11 6.59 b -0.57 3.18 2.04 0 5.52 0 l 401.49 0.38 b 404.53 0.38 407 2.85 407 5.9 l 407 50.03 b 407 53.08 404.53 55.55 401.48 55.55"

    const val SP_BUTTERFLY_DRAWING = "m 35.43 9.05 b 35.71 9.22 36.84 9.07 37.79 10.56 38.96 12.39 38.49 15.39 37.62 17.11 38.66 18.37 38.97 20.71 38.13 22.65 35.96 27.68 29.21 31.72 24.96 34.24 27.89 31.52 31.09 29.36 33.32 25.72 34 24.62 35.22 22.15 34.42 20.98 32.71 20.62 27.34 23.13 24.29 27.36 21.14 31.72 20.86 33.55 20.41 36.26 21.41 40.09 24.07 41.77 28.85 42.14 28.04 39.7 26.32 38.06 24.39 36.74 23.49 36.13 22.56 35.58 21.59 35.08 23.91 35.8 26.09 36.94 28 38.44 30.86 40.78 31.66 43.09 32.56 47.85 33.09 50.65 34.04 52.93 31.65 54.28 33.88 52.85 32.21 50.01 30.53 49.19 29.88 48.87 28.62 48.97 27.83 48.69 26.89 48.34 26.1 47.24 24.96 46.67 23.55 45.96 22.07 46.07 21.42 45.66 20.37 45.02 20 43.36 19.39 42.47 19.34 44.22 18.57 48.41 16.19 47.68 14.83 47.26 16.74 44.1 16.86 43.15 17.02 41.85 16.92 40.07 16.02 38.95 15.48 38.27 14.51 37.8 13.42 37.19 12.97 36.94 11.83 35.97 11.72 35.99 11 36.13 9.97 35.92 9.71 35.15 9.34 34.05 11.2 33.56 11.97 33.57 13.09 32.08 12.5 27.43 12.9 24.57 13.72 18.74 17.49 12.84 21.45 8.87 23.94 6.38 32.45 -0.83 35.61 1.11 37.19 2.74 35.4 6.32 35.43 9.05 m 26.15 6.87 b 27.67 6.5 29.55 6.48 30.37 7.37 30.76 7.99 30.99 9.37 30.93 10.78 30.86 12.01 30.68 13.23 30.4 14.43 32.65 10.95 35.03 3.99 33.4 3 31.77 2.02 27.62 5.34 26.15 6.87 l 26.15 6.87 m 22.26 24.84 b 21.3 29.83 18.63 31.37 18.38 34.75 18.38 34.58 18.42 34.82 18.81 34.31 21.79 29.32 25.11 24.53 26.48 17.62 26.91 15.46 26.96 11.22 25.64 10.73 24.32 10.24 20.77 12.86 18.89 14.76 15.03 18.67 12.58 26.37 15.01 32.06 15.4 32.98 16.32 34.73 16.86 34.41 16.7 31.82 16.05 29.04 16.36 26.35 16.76 22.75 18.7 17.85 20.91 17.95 23.29 18.49 22.69 22.63 22.26 24.84 l 22.26 24.84 m 36.95 14.43 b 37.2 12.8 36.46 11.44 35.43 11.4 34.52 11.37 33.47 12.92 32.9 13.75 30.72 16.92 29.1 20.44 27.5 22.99 30.27 21.03 36.46 17.5 36.95 14.43 l 36.95 14.43 m 27.8 44.82 b 28.7 45.17 29.23 45.1 29.01 45.5 28.78 45.94 28.02 45.83 27.15 45.3 26.19 44.71 25.1 43.65 24.29 43.31 25.31 45.54 26.82 47.29 30.53 46.84 30.54 43.65 27.03 42.72 24.37 42.74 24.95 43.49 26.64 44.37 27.8 44.82 l 27.8 44.82 m 2.91 31.6 b 1.88 32.14 2.35 32.94 1.34 32.94 0.69 32.79 0.27 32.07 1 31.09 1.73 30.12 8 30.61 9.61 33.44 8.86 32.82 5.71 30.95 2.91 31.6 l 2.91 31.6 m 5.55 35.8 b 4.41 36.18 3.51 36.9 3.18 37.99 0.92 37.14 3.86 35.44 5.55 35.8 m 10.03 9.51 b 11.15 9.51 12.68 9.53 13.69 10.73 14.89 12.15 15.24 14.51 15.05 15.64 15.01 13.74 12.87 10.65 11.65 10.81 9.2 11.14 11.84 17.53 12.39 21.33 11.58 19.21 10.25 15.65 9.12 13.83 8.31 12.53 7.32 11.78 6.81 11.81 5.58 11.9 4.84 14.37 4.8 15.82 4.69 19.94 6.27 24.48 9.34 27.33 7.66 23.9 6.26 17.57 7.47 17.32 8.68 17.07 11.44 22.7 11.46 26.68 11.47 29.25 11.35 31.33 11.25 32.47 4.02 24.72 3.37 18.17 2.8 8.97 2.59 5.6 2.83 3.31 4.63 2.97 7.83 2.36 8.51 8.15 10.03 9.51 m 9.23 10.62 b 8.96 8.75 7.1 5.82 6.64 5.47 3.72 3.25 3.2 9.2 3.97 12.15 4.01 10.44 5.29 9.41 6.35 9.45 7.33 9.49 9.01 11.46 9.45 12.77 9.34 12.24 9.45 12.15 9.23 10.62"

    fun parseAssDrawingToPath(drawingCommands: String, targetWidth: Float, targetHeight: Float): Path {
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        val tokens = drawingCommands.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return path

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        var idx = 0
        while (idx < tokens.size) {
            val t = tokens[idx]
            val num = t.toFloatOrNull()
            if (num != null) {
                val nextNum = tokens.getOrNull(idx + 1)?.toFloatOrNull()
                if (nextNum != null) {
                    if (num < minX) minX = num
                    if (num > maxX) maxX = num
                    if (nextNum < minY) minY = nextNum
                    if (nextNum > maxY) maxY = nextNum
                    idx += 2
                    continue
                }
            }
            idx++
        }

        val origW = if (maxX > minX) maxX - minX else 1f
        val origH = if (maxY > minY) maxY - minY else 1f
        val scaleX = targetWidth / origW
        val scaleY = targetHeight / origH

        idx = 0
        var currentCmd = ""
        while (idx < tokens.size) {
            val t = tokens[idx]
            when (t) {
                "m" -> {
                    currentCmd = "m"
                    val x = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minX
                    val y = (tokens.getOrNull(idx + 2)?.toFloatOrNull() ?: 0f) - minY
                    path.moveTo(x * scaleX, y * scaleY)
                    idx += 3
                }
                "l" -> {
                    currentCmd = "l"
                    val x = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minX
                    val y = (tokens.getOrNull(idx + 2)?.toFloatOrNull() ?: 0f) - minY
                    path.lineTo(x * scaleX, y * scaleY)
                    idx += 3
                }
                "b" -> {
                    currentCmd = "b"
                    val x1 = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minX
                    val y1 = (tokens.getOrNull(idx + 2)?.toFloatOrNull() ?: 0f) - minY
                    val x2 = (tokens.getOrNull(idx + 3)?.toFloatOrNull() ?: 0f) - minX
                    val y2 = (tokens.getOrNull(idx + 4)?.toFloatOrNull() ?: 0f) - minY
                    val x3 = (tokens.getOrNull(idx + 5)?.toFloatOrNull() ?: 0f) - minX
                    val y3 = (tokens.getOrNull(idx + 6)?.toFloatOrNull() ?: 0f) - minY
                    path.cubicTo(x1 * scaleX, y1 * scaleY, x2 * scaleX, y2 * scaleY, x3 * scaleX, y3 * scaleY)
                    idx += 7
                }
                "c" -> {
                    path.close()
                    idx++
                }
                else -> {
                    if (currentCmd == "m") {
                        val x = (t.toFloatOrNull() ?: 0f) - minX
                        val y = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minY
                        path.moveTo(x * scaleX, y * scaleY)
                        idx += 2
                    } else if (currentCmd == "l") {
                        val x = (t.toFloatOrNull() ?: 0f) - minX
                        val y = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minY
                        path.lineTo(x * scaleX, y * scaleY)
                        idx += 2
                    } else if (currentCmd == "b") {
                        val x1 = (t.toFloatOrNull() ?: 0f) - minX
                        val y1 = (tokens.getOrNull(idx + 1)?.toFloatOrNull() ?: 0f) - minY
                        val x2 = (tokens.getOrNull(idx + 2)?.toFloatOrNull() ?: 0f) - minX
                        val y2 = (tokens.getOrNull(idx + 3)?.toFloatOrNull() ?: 0f) - minY
                        val x3 = (tokens.getOrNull(idx + 4)?.toFloatOrNull() ?: 0f) - minX
                        val y3 = (tokens.getOrNull(idx + 5)?.toFloatOrNull() ?: 0f) - minY
                        path.cubicTo(x1 * scaleX, y1 * scaleY, x2 * scaleX, y2 * scaleY, x3 * scaleX, y3 * scaleY)
                        idx += 6
                    } else {
                        idx++
                    }
                }
            }
        }
        return path
    }
}
