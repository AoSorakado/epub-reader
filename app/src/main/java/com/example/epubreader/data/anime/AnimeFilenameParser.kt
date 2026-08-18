package com.example.epubreader.data.anime

data class ParsedEpisodeInfo(
    val animeTitle: String,
    val seasonName: String,
    val episodeNumber: String,
    val episodeIndex: Int,
    val cleanTitle: String,
    val resolution: String,
    val videoCodec: String,
    val audioCodec: String,
    val releaseGroup: String?
)

object AnimeFilenameParser {

    private val RELEASE_GROUP_REGEX = Regex("^\\[([^\\]]+)\\]")
    private val RESOLUTION_REGEX = Regex("(?i)(2160p|4k|1080p|720p|480p)")
    private val VIDEO_CODEC_REGEX = Regex("(?i)(x265|h265|hevc|x264|h264|avc|av1|ma10p|10bit|8bit)")
    private val AUDIO_CODEC_REGEX = Regex("(?i)(flac|aac|pcm|dts|truehd|ac3|eac3|opus)")
    private val SEASON_REGEX = Regex("(?i)(第[0-9一二三四五六七八九十]+季|S[0-9]+|Season\\s*[0-9]+|剧场版|总集篇|圣王国篇|篇|OVA|OAD|SP|特典)")
    private val NON_SEASON_FOLDER_NAMES = setOf(
        "备份字幕", "subtitles", "subs", "subtitle", "menu", "certificate", "playlist", "clipinf", "stream", "jar", "backup", "auxdata", "出包女王字幕", "出包王女字幕", "字幕包", "字幕备份", "fonts", "font"
    )

    fun isNonSeasonFolder(folderName: String): Boolean {
        val lower = folderName.lowercase().trim()
        return lower in NON_SEASON_FOLDER_NAMES || lower.contains("字幕") || lower.contains("fonts") || lower.contains("font")
    }

    fun isSubtitleFolder(folderName: String): Boolean {
        val lower = folderName.lowercase().trim()
        return lower in setOf("subs", "subtitles", "sub", "subtitle", "字幕", "字幕包", "字幕备份", "备份字幕", "chs", "cht", "utf8", "sc", "tc") ||
                lower.contains("字幕")
    }

    /**
     * Identifies junk / extra / creditless / menu files that must be cleaned out:
     * - NCOP, NCED, OP, ED clips
     * - Menu, MENU, Disc Menu
     * - SP, Special, Extra, Bonus clips
     * - PV, Trailer, CM, Preview
     */
    fun isIgnoredExtraFile(filename: String): Boolean {
        val name = filename.lowercase()
        // Never scan or add any .m2ts files
        if (name.endsWith(".m2ts")) {
            return true
        }

        // Quick keyword check
        if (name.contains("ncop") || name.contains("nced") || name.contains("menu") ||
            name.contains("trailer") || name.contains("preview") || name.contains("bonus") ||
            name.contains("creditless") || name.contains("textless")) {
            return true
        }

        // Bracketed patterns e.g. [NCOP], [NCED], [NCOP_EP08], [OP], [ED], [SP], [SP01], [PV], [CM], [Menu]
        val bracketExtraRegex = Regex("(?i)\\[(NCOP|NCED|OP|ED|SP|PV|CM|MENU|EXTRA|BONUS|TRAILER)[^\\]]*\\]")
        if (bracketExtraRegex.containsMatchIn(filename)) {
            return true
        }

        // Word boundary patterns e.g. NCOP01, NCED_EP01, SP01, PV1, Menu.mkv
        val wordExtraRegex = Regex("(?i)(?:^|[\\s._\\-])(NCOP|NCED|MENU|TRAILER|BONUS|EXTRA|PREVIEW)(?:[0-9._\\-]|$)")
        if (wordExtraRegex.containsMatchIn(filename)) {
            return true
        }

        // Specific OP / ED / SP standalone tags (e.g. "xxx_OP.mkv", "xxx_ED.mkv", "xxx_SP.mkv")
        val standaloneOpEd = Regex("(?i)(?:^|[\\s._\\-])(OP|ED|SP)(?:[0-9]{1,2})?(?:[\\s._\\-]|\\.[a-z0-9]+$)")
        if (standaloneOpEd.containsMatchIn(filename) && !filename.contains(Regex("(?i)\\[[0-9]{2}\\]"))) {
            return true
        }

        return false
    }

    // Season sorting weight
    fun getSeasonSortWeight(name: String): Int {
        val lower = name.lowercase().trim()
        val numRegex = Regex("[0-9一二三四五六七八九十]+")
        val matchedNum = numRegex.find(lower)?.value
        val numVal = when (matchedNum) {
            "一", "1" -> 1
            "二", "2" -> 2
            "三", "3" -> 3
            "四", "4" -> 4
            "五", "5" -> 5
            "六", "6" -> 6
            "七", "7" -> 7
            "八", "8" -> 8
            "九", "9" -> 9
            "十", "10" -> 10
            else -> matchedNum?.toIntOrNull() ?: 1
        }

        return when {
            lower == "正片" || lower.startsWith("第1季") || lower.startsWith("第一季") || lower.startsWith("s1") || lower.startsWith("season 1") -> 10
            lower.startsWith("第2季") || lower.startsWith("第二季") || lower.startsWith("s2") || lower.startsWith("season 2") -> 20
            lower.startsWith("第3季") || lower.startsWith("第三季") || lower.startsWith("s3") || lower.startsWith("season 3") -> 30
            lower.startsWith("第4季") || lower.startsWith("第四季") || lower.startsWith("s4") || lower.startsWith("season 4") -> 40
            lower.startsWith("第5季") || lower.startsWith("第五季") || lower.startsWith("s5") || lower.startsWith("season 5") -> 50
            lower.startsWith("第6季") || lower.startsWith("第六季") || lower.startsWith("s6") || lower.startsWith("season 6") -> 60
            lower.startsWith("第") && lower.contains("季") -> 10 * numVal
            lower.startsWith("s") && numVal in 1..20 -> 10 * numVal
            lower.contains("剧场版") || lower.contains("movie") -> 1000
            lower.contains("总集篇") -> 1100
            lower.contains("ova") -> 2000 + numVal
            lower.contains("oad") -> 2100 + numVal
            lower.contains("sp") || lower.contains("特典") || lower.contains("special") -> 3000 + numVal
            lower.contains("op") || lower.contains("ed") || lower.contains("ncop") || lower.contains("nced") -> 4000 + numVal
            else -> 500
        }
    }

    // Clean prefixes like "B 4k ", "A 4k ", "[4K_60FPS_SDR]", "(1)", "(2)"
    fun cleanAnimeFolderName(folderName: String): String {
        var clean = folderName
        clean = clean.replace(Regex("(?i)^[A-Z]\\s*4k\\s*"), "") // e.g. "B 4k ", "A 4k "
        clean = clean.replace(Regex("\\[[^\\]]*\\]"), " ")       // e.g. "[DBD-Raws]"
        clean = clean.replace(Regex("\\([0-9]+\\)"), " ")        // e.g. "(1)"
        clean = clean.replace(Regex("(?i)(2160p|4k|1080p|60fps|sdr|hdr|bdremux|简繁外挂|pcm|mkv|m2ts)"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean.ifBlank { folderName }
    }

    // Clean episode file names
    fun parseEpisodeFilename(
        filename: String,
        parentFolderName: String,
        grandParentFolderName: String? = null
    ): ParsedEpisodeInfo {
        val nameWithoutExt = filename.substringBeforeLast(".")

        // 1. Extract Release Group
        val releaseGroupMatch = RELEASE_GROUP_REGEX.find(nameWithoutExt)
        val releaseGroup = releaseGroupMatch?.groupValues?.getOrNull(1)

        // 2. Extract Resolution & Codecs
        val resolution = RESOLUTION_REGEX.find(nameWithoutExt)?.value?.uppercase() ?: "1080P"
        val videoCodec = VIDEO_CODEC_REGEX.find(nameWithoutExt)?.value?.uppercase() ?: "HEVC"
        val audioCodec = AUDIO_CODEC_REGEX.find(nameWithoutExt)?.value?.uppercase() ?: "AAC"

        // 3. Determine Anime Base Title
        val cleanAnimeTitle = if (!grandParentFolderName.isNullOrBlank() && SEASON_REGEX.containsMatchIn(parentFolderName)) {
            cleanAnimeFolderName(grandParentFolderName)
        } else {
            cleanAnimeFolderName(parentFolderName)
        }

        // 4. Check for Special / Extra Types: OP, ED, NCOP, NCED, SP, OVA, OAD
        var detectedSeason = if (SEASON_REGEX.containsMatchIn(parentFolderName)) {
            parentFolderName.trim()
        } else {
            "正片"
        }

        var episodeStr = "01"
        var episodeIdx = 1
        var cleanTitle = "第 01 集"

        // Match OP / NCOP / ED / NCED (e.g. [NCOP], [NCED], [NCOP01], [NCED_EP01-04], OP, ED)
        val opEdRegex = Regex("(?i)(?:\\[|\\b)(NCOP|NCED|OP|ED)(?:_EP[0-9\\-]+|\\s*[0-9]{1,2})?\\b")
        val opEdMatch = opEdRegex.find(nameWithoutExt)

        val specialRegex = Regex("(?i)(?:\\[|\\b)(OVA|OAD|SP|EX|SPECIAL|EXTRA)\\s*([0-9]{1,3})?\\b")
        val specialMatch = specialRegex.find(nameWithoutExt)

        if (opEdMatch != null && !nameWithoutExt.contains(Regex("(?i)\\[[0-9]{2}\\]"))) {
            val tag = opEdMatch.groupValues[1].uppercase()
            detectedSeason = if (detectedSeason == "正片") "OP / ED" else "$detectedSeason OP/ED"
            episodeStr = tag
            episodeIdx = when (tag) {
                "OP", "NCOP" -> 6001
                else -> 7001
            }
            cleanTitle = when (tag) {
                "NCOP" -> "片头曲 (NCOP)"
                "OP" -> "片头曲 (OP)"
                "NCED" -> "片尾曲 (NCED)"
                "ED" -> "片尾曲 (ED)"
                else -> tag
            }
        } else if (specialMatch != null && (specialMatch.groupValues[1].uppercase() in setOf("OVA", "OAD", "SP", "EX") || specialMatch.groupValues[2].isNotBlank())) {
            val tag = specialMatch.groupValues[1].uppercase()
            val num = specialMatch.groupValues[2].toIntOrNull() ?: 1
            val formatted = String.format("%02d", num)
            episodeStr = "$tag $formatted"
            episodeIdx = when (tag) {
                "OVA" -> 1000 + num
                "OAD" -> 2000 + num
                "SP", "SPECIAL" -> 3000 + num
                "EX", "EXTRA" -> 4000 + num
                else -> 5000 + num
            }
            cleanTitle = "$tag $formatted"
            if (detectedSeason == "正片") {
                detectedSeason = when (tag) {
                    "OVA" -> "OVA"
                    "OAD" -> "OAD"
                    else -> "SP 特典"
                }
            }
        } else {
            // Check Fractional numbers: [11.5], EP11.5, 第11.5集, 11.5
            val decimalRegex = Regex("(?:\\[|EP|E|第|\\s|_|-)([0-9]{1,3}\\.5)(?:\\]|集|话|\\s|_|-|\\.)")
            val decimalMatch = decimalRegex.find(nameWithoutExt)

            if (decimalMatch != null) {
                val numStr = decimalMatch.groupValues[1]
                val baseInt = numStr.substringBefore(".").toIntOrNull() ?: 1
                episodeStr = numStr
                episodeIdx = baseInt
                cleanTitle = "第 $numStr 集"
            } else {
                // Check Bracketed numbers: [01], [12], [26]
                val bracketRegex = Regex("\\[([0-9]{1,3})\\]")
                val bracketMatch = bracketRegex.find(nameWithoutExt)

                if (bracketMatch != null) {
                    val num = bracketMatch.groupValues[1].toIntOrNull() ?: 1
                    val formatted = String.format("%02d", num)
                    episodeStr = formatted
                    episodeIdx = num
                    cleanTitle = "第 $formatted 集"
                } else {
                    // Check EP01, E01, 第01话, 第1集
                    val epPrefixRegex = Regex("(?i)(?:EP|E|第)\\s*([0-9]{1,3})(?:集|话)?")
                    val epPrefixMatch = epPrefixRegex.find(nameWithoutExt)

                    if (epPrefixMatch != null) {
                        val num = epPrefixMatch.groupValues[1].toIntOrNull() ?: 1
                        val formatted = String.format("%02d", num)
                        episodeStr = formatted
                        episodeIdx = num
                        cleanTitle = "第 $formatted 集"
                    } else {
                        // Check Delimited number: "Silent Witch 01 [Ma10p..." or "- 01", "_01_", ".01."
                        val delimitedRegex = Regex("(?:-\\s*|_|\\s)([0-9]{1,3})(?:\\s|_|\\.|\\[)")
                        val delimitedMatch = delimitedRegex.find(nameWithoutExt)

                        if (delimitedMatch != null) {
                            val num = delimitedMatch.groupValues[1].toIntOrNull() ?: 1
                            val formatted = String.format("%02d", num)
                            episodeStr = formatted
                            episodeIdx = num
                            cleanTitle = "第 $formatted 集"
                        }
                    }
                }
            }
        }

        return ParsedEpisodeInfo(
            animeTitle = cleanAnimeTitle,
            seasonName = detectedSeason,
            episodeNumber = episodeStr,
            episodeIndex = episodeIdx,
            cleanTitle = cleanTitle,
            resolution = resolution,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            releaseGroup = releaseGroup
        )
    }
}
