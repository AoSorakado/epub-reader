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
    private val SEASON_REGEX = Regex("(?i)(第[0-9一二三四五六七八九十]+季|S[0-9]+|Season\\s*[0-9]+|剧场版|总集篇|圣王国篇|篇|OVA|OAD|SP)")

    // Clean prefixes like "B 4k ", "A 4k ", "[4K_60FPS_SDR]", "(1)", "(2)"
    fun cleanAnimeFolderName(folderName: String): String {
        var clean = folderName
        clean = clean.replace(Regex("(?i)^[A-Z]\\s*4k\\s*"), "") // e.g. "B 4k ", "A 4k "
        clean = clean.replace(Regex("\\[[^\\]]*\\]"), " ")       // e.g. "[DBD-Raws]"
        clean = clean.replace(Regex("\\([0-9]+\\)"), " ")        // e.g. "(1)"
        clean = clean.replace(Regex("(?i)(2160p|4k|1080p|60fps|sdr|hdr|bdremux|简繁外挂|pcm|mkv)"), " ")
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

        // 4. Determine Season Name
        val seasonName = if (SEASON_REGEX.containsMatchIn(parentFolderName)) {
            parentFolderName.trim()
        } else {
            "正片"
        }

        // 5. Extract Episode Number (e.g., [01], EP01, E01, - 01, 第01话)
        val epRegexes = listOf(
            Regex("\\[([0-9]{1,3})\\]"),                     // [01]
            Regex("(?i)(?:EP|E|第)\\s*([0-9]{1,3})(?:集|话)?"), // EP01, E01, 第1集
            Regex("(?:-\\s*|_|\\s)([0-9]{2,3})(?:\\s|_|\\.)"), // - 01, _01_
            Regex("(?:NCOP|NCED|OVA|SP)[0-9]*", RegexOption.IGNORE_CASE) // NCOP, NCED, OVA
        )

        var episodeStr = "01"
        var episodeIdx = 1

        for (regex in epRegexes) {
            val match = regex.find(nameWithoutExt)
            if (match != null) {
                val groupVal = match.groupValues.getOrNull(1) ?: match.value
                episodeStr = groupVal
                val num = groupVal.toIntOrNull()
                if (num != null) {
                    episodeIdx = num
                    episodeStr = String.format("%02d", num)
                }
                break
            }
        }

        val cleanTitle = "第 $episodeStr 集"

        return ParsedEpisodeInfo(
            animeTitle = cleanAnimeTitle,
            seasonName = seasonName,
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
