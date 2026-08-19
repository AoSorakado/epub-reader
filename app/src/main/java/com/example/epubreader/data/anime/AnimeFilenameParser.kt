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

        // Quick keyword check (Menu, Extras, Bonus, Trailers, etc.)
        if (name.contains("ncop") || name.contains("nced") || name.contains("menu") ||
            name.contains("trailer") || name.contains("preview") || name.contains("bonus") ||
            name.contains("creditless") || name.contains("textless") || name.contains("sample")) {
            return true
        }

        // Bracketed patterns e.g. [NCOP], [NCED], [NCOP_EP08], [OP], [ED], [SP], [SP01], [PV], [CM], [Menu]
        val bracketExtraRegex = Regex("(?i)\\[(NCOP|NCED|OP|ED|SP|PV|CM|MENU|EXTRA|BONUS|TRAILER|SAMPLE)[^\\]]*\\]")
        if (bracketExtraRegex.containsMatchIn(filename)) {
            return true
        }

        // Word boundary patterns e.g. NCOP01, NCED_EP01, SP01, PV1, Menu.mkv
        val wordExtraRegex = Regex("(?i)(?:^|[\\s._\\-])(NCOP|NCED|MENU|TRAILER|BONUS|EXTRA|PREVIEW|SAMPLE)(?:[0-9._\\-]|$)")
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

    // Clean prefixes and disc/release tags like "Your.Name.2016.JAPANESE.2160p.BluRay.HEVC.DTS-HD.MA.5.1-TASTED", "B 4k ", "[DBD-Raws]"
    fun cleanAnimeFolderName(folderName: String): String {
        var clean = folderName
        clean = clean.replace(Regex("(?i)^[A-Z]\\s*4k\\s*"), "") // e.g. "B 4k ", "A 4k "
        clean = clean.replace(Regex("\\[[^\\]]*\\]"), " ")       // e.g. "[DBD-Raws]", "[BDMV]"
        clean = clean.replace(Regex("\\([0-9]+\\)"), " ")        // e.g. "(1)"
        clean = clean.replace(Regex("-[a-zA-Z0-9_]+$"), " ")     // Strip trailing -TASTED before removing dots
        
        // Remove known technical spec compound tags (both dot-separated and space-separated)
        clean = clean.replace(Regex("(?i)\\b(DTS[-.]HD([-. ]MA)?|Dolby[-. ]Digital|TrueHD([-. ]Atmos)?)\\b"), " ")
        clean = clean.replace(Regex("(?i)\\b(5\\.1|7\\.1|2\\.0)\\b"), " ")

        // Convert dot and underscore separators in scene releases: Your.Name.2016... -> Your Name 2016...
        if (clean.contains(".") || clean.contains("_")) {
            clean = clean.replace(Regex("[._]+"), " ")
        }
        
        // Remove technical specs, audio, video codecs, release groups
        clean = clean.replace(Regex("(?i)\\b(2160p|4k|1080p|720p|480p|60fps|sdr|hdr10\\+?|hdr|dovi|dv|bdremux|remux|bluray|blu-ray|uhd|hevc|h265|x265|x264|h264|avc|av1|ma10p|10bit|8bit|dts|hd|ma|atmos|truehd|flac|aac|ac3|eac3|pcm|5\\s*1|7\\s*1|2\\s*0|japanese|jpn|chs|cht|gb|big5|tasted|raws?|bdrip|web-dl|webrip|mkv|mp4|m2ts)\\b"), " ")
        
        // Strip release year (1980-2039)
        clean = clean.replace(Regex("(?i)\\b(19[89][0-9]|20[0-3][0-9])\\b"), " ")
        
        // Strip trailing punctuation / hyphens
        clean = clean.replace(Regex("[\\-_]+"), " ")
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
        val ext = filename.substringAfterLast(".").lowercase()

        // Combine context strings for spec extraction (e.g. parent folder might contain "2160p.BluRay.HEVC.DTS-HD.MA.5.1-TASTED")
        val contextString = "$filename $parentFolderName ${grandParentFolderName ?: ""}"

        // 1. Extract Release Group
        val releaseGroupMatch = RELEASE_GROUP_REGEX.find(nameWithoutExt)
        val releaseGroup = releaseGroupMatch?.groupValues?.getOrNull(1) ?: run {
            // Check trailing -GROUP in parent folder or filename
            val trailingGroup = Regex("-([A-Za-z0-9_]+)$").find(parentFolderName)?.groupValues?.getOrNull(1)
            trailingGroup
        }

        // 2. Extract Resolution & Codecs (from filename or parent folder context)
        val resolution = RESOLUTION_REGEX.find(contextString)?.value?.uppercase() ?: "1080P"
        val videoCodec = VIDEO_CODEC_REGEX.find(contextString)?.value?.uppercase() ?: "HEVC"
        val audioCodec = when {
            contextString.contains("DTS-HD", ignoreCase = true) || contextString.contains("DTS-HD MA", ignoreCase = true) -> "DTS-HD MA"
            contextString.contains("TrueHD", ignoreCase = true) || contextString.contains("Atmos", ignoreCase = true) -> "TrueHD"
            else -> AUDIO_CODEC_REGEX.find(contextString)?.value?.uppercase() ?: "AAC"
        }

        // 3. Determine Anime Base Title
        val cleanAnimeTitle = if (!grandParentFolderName.isNullOrBlank() && (SEASON_REGEX.containsMatchIn(parentFolderName) || parentFolderName.contains("2160p", ignoreCase = true) || parentFolderName.contains("BluRay", ignoreCase = true))) {
            cleanAnimeFolderName(grandParentFolderName)
        } else {
            cleanAnimeFolderName(parentFolderName)
        }

        // 4. Handle Blu-ray / M2TS disc main feature (e.g. 00000.m2ts, 00001.m2ts)
        val isM2tsDiscFeature = ext == "m2ts" || nameWithoutExt.matches(Regex("^[0-9]{5}$"))
        if (isM2tsDiscFeature && (nameWithoutExt == "00000" || nameWithoutExt == "00001")) {
            val detectedSeason = if (parentFolderName.contains("剧场版", ignoreCase = true) || cleanAnimeTitle.contains("剧场版") || parentFolderName.contains("2160p", ignoreCase = true) || parentFolderName.contains("BluRay", ignoreCase = true)) {
                "剧场版"
            } else {
                "正片"
            }
            return ParsedEpisodeInfo(
                animeTitle = cleanAnimeTitle,
                seasonName = detectedSeason,
                episodeNumber = "01",
                episodeIndex = 1,
                cleanTitle = "正片",
                resolution = resolution,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                releaseGroup = releaseGroup
            )
        }

        // 5. Check for Special / Extra Types: OP, ED, NCOP, NCED, SP, OVA, OAD
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
