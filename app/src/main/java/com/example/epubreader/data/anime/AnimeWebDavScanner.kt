package com.example.epubreader.data.anime

import android.content.Context
import android.util.Log
import com.example.epubreader.data.db.AnimeDao
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.model.network.WebDavResource
import com.example.epubreader.data.network.WebDavClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

object AnimeWebDavScanner {

    private const val TAG = "AnimeWebDavScanner"
    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "ts")
    private val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")
    private val SEASON_REGEX = Regex("(?i)(第[0-9一二三四五六七八九十]+季|S[0-9]+|Season\\s*[0-9]+|剧场版|总集篇|圣王国篇|篇|OVA|OAD|SP|特典|Bonus|Menu)")

    /**
     * Filters candidate video files:
     * 1. Keeps all valid main episodes and decimal episodes (e.g. 01, 11.5, 12, 13, OVA, OAD).
     * 2. Cleans & discards junk files: NCOP, NCED, OP, ED clips, Menu, SP, PV, CM, Trailer, Bonus clips.
     */
    private fun filterCandidateVideoFiles(files: List<WebDavResource>): List<WebDavResource> {
        return files.filter { 
            !it.isDirectory && 
            it.name.substringAfterLast(".").lowercase() in VIDEO_EXTENSIONS &&
            !it.name.endsWith(".m2ts", ignoreCase = true) &&
            !AnimeFilenameParser.isIgnoredExtraFile(it.name)
        }
    }

    /**
     * Fast shallow subtitle collector for a specific folder and its immediate subtitle subfolders.
     */
    private suspend fun collectSubtitlesFast(
        webDavClient: WebDavClient,
        dirPath: String,
        currentDepth: Int = 0
    ): List<WebDavResource> {
        if (currentDepth > 2) return emptyList()
        val subs = mutableListOf<WebDavResource>()
        try {
            val list = webDavClient.listFiles(dirPath)
            for (res in list) {
                if (!res.isDirectory && res.name.substringAfterLast(".").lowercase() in SUBTITLE_EXTENSIONS) {
                    subs.add(res)
                } else if (res.isDirectory && (AnimeFilenameParser.isSubtitleFolder(res.name) || currentDepth < 1)) {
                    subs.addAll(collectSubtitlesFast(webDavClient, res.path, currentDepth + 1))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Subtitle scan ignored for $dirPath: ${e.message}")
        }
        return subs
    }

    /**
     * Scans a single directory for valid video files and subtitle subfolders.
     */
    private suspend fun scanDirectoryVideos(
        webDavClient: WebDavClient,
        childResources: List<WebDavResource>
    ): Pair<List<WebDavResource>, List<WebDavResource>> {
        val videoFiles = filterCandidateVideoFiles(childResources)
        val directSubtitleFiles = childResources.filter {
            !it.isDirectory && it.name.substringAfterLast(".").lowercase() in SUBTITLE_EXTENSIONS
        }

        val allSubtitleFiles = mutableListOf<WebDavResource>()
        allSubtitleFiles.addAll(directSubtitleFiles)

        // Check for subtitle subfolders: 字幕, 字幕包, 字幕备份, 备份字幕, Subs, Subtitles, 出包女王字幕 etc.
        val subFolders = childResources.filter {
            it.isDirectory && AnimeFilenameParser.isSubtitleFolder(it.name)
        }
        for (folder in subFolders) {
            allSubtitleFiles.addAll(collectSubtitlesFast(webDavClient, folder.path, 0))
        }

        return Pair(videoFiles, allSubtitleFiles)
    }

    private data class AnimeScanResult(
        val animeEntity: AnimeEntity,
        val episodes: List<AnimeEpisodeEntity>
    )

    /**
     * Helper to recursively collect videos and subtitles from an anime folder tree (up to 3 levels deep).
     * Handles:
     * - Direct videos (e.g. Silent Witch/01.mkv)
     * - Multi-season subfolders (e.g. 绯弹的亚里亚/绯弹的亚里亚/ and 绯弹的亚里亚/绯弹的亚里亚 AA/)
     * - BDMV / Disc edition subfolders (e.g. 你的名字/Your.Name.2016.../00000.m2ts)
     */
    private suspend fun parseSingleAnimeFolder(
        webDavClient: WebDavClient,
        animeDao: AnimeDao,
        folderPath: String,
        animeDisplayName: String,
        context: Context,
        preloadedChildren: List<WebDavResource>? = null
    ): AnimeScanResult? {
        val rawFolderName = animeDisplayName.ifBlank { folderPath.trimEnd('/').substringAfterLast('/') }
        val cleanTitle = AnimeFilenameParser.cleanAnimeFolderName(rawFolderName)

        val childResources = preloadedChildren ?: try {
            webDavClient.listFiles(folderPath)
                .filter { !it.name.equals("Menu", ignoreCase = true) && !it.name.startsWith(".") && !it.name.endsWith(".m2ts", ignoreCase = true) && !it.name.equals("BDMV", ignoreCase = true) }
        } catch (e: Exception) {
            return null
        }

        val subFolders = childResources.filter {
            it.isDirectory && !AnimeFilenameParser.isNonSeasonFolder(it.name) && !it.name.equals("BDMV", ignoreCase = true)
        }
        val (directVideos, directSubs) = scanDirectoryVideos(webDavClient, childResources)

        val episodesList = mutableListOf<AnimeEpisodeEntity>()
        val parentSubtitles = mutableListOf<WebDavResource>()
        parentSubtitles.addAll(directSubs)

        // 1. If direct videos exist in this anime folder:
        if (directVideos.isNotEmpty()) {
            directVideos.forEach { vFile ->
                val parsed = AnimeFilenameParser.parseEpisodeFilename(
                    filename = vFile.name,
                    parentFolderName = rawFolderName
                )
                val existingEp = animeDao.getEpisodeByVideoUrl(vFile.path)

                episodesList.add(
                    AnimeEpisodeEntity(
                        id = existingEp?.id ?: 0,
                        animeId = 0,
                        seasonName = parsed.seasonName,
                        episodeIndex = parsed.episodeIndex,
                        episodeNumber = parsed.episodeNumber,
                        title = parsed.cleanTitle,
                        videoUrl = vFile.path,
                        subtitleUrl = null,
                        durationMs = existingEp?.durationMs ?: 0L,
                        lastPlayedPositionMs = existingEp?.lastPlayedPositionMs ?: 0L,
                        isWatched = existingEp?.isWatched ?: false,
                        resolution = parsed.resolution,
                        videoCodec = parsed.videoCodec,
                        audioCodec = parsed.audioCodec,
                        releaseGroup = parsed.releaseGroup,
                        fileSize = vFile.size,
                        danmakuEpisodeId = existingEp?.danmakuEpisodeId,
                        danmakuTimeOffsetMs = existingEp?.danmakuTimeOffsetMs ?: 0L
                    )
                )
            }
        }

        // 2. Scan each subfolder (e.g. 绯弹的亚里亚, 绯弹的亚里亚 AA, 第一季, Your.Name.2016...)
        if (subFolders.isNotEmpty()) {
            for (sub in subFolders) {
                val subRawName = sub.name.ifBlank { sub.path.trimEnd('/').substringAfterLast('/') }
                val subChildren = try { 
                    webDavClient.listFiles(sub.path)
                        .filter { !it.name.equals("Menu", ignoreCase = true) && !it.name.startsWith(".") && !it.name.endsWith(".m2ts", ignoreCase = true) && !it.name.equals("BDMV", ignoreCase = true) }
                } catch (e: Exception) { emptyList() }
                val (subVideos, subSubs) = scanDirectoryVideos(webDavClient, subChildren)
                val combinedSubs = (subSubs + parentSubtitles).toMutableList()

                // Check if this subfolder itself has a nested disc/edition subfolder
                val subSubFolders = subChildren.filter { it.isDirectory && !AnimeFilenameParser.isNonSeasonFolder(it.name) && !it.name.equals("BDMV", ignoreCase = true) }
                val actualVideos = if (subVideos.isEmpty() && subSubFolders.isNotEmpty()) {
                    val collected = mutableListOf<WebDavResource>()
                    for (ss in subSubFolders) {
                        val ssChildren = try { 
                            webDavClient.listFiles(ss.path)
                                .filter { !it.name.equals("Menu", ignoreCase = true) && !it.name.startsWith(".") && !it.name.endsWith(".m2ts", ignoreCase = true) && !it.name.equals("BDMV", ignoreCase = true) }
                        } catch (e: Exception) { emptyList() }
                        val (ssVideos, ssSubs) = scanDirectoryVideos(webDavClient, ssChildren)
                        collected.addAll(ssVideos)
                        combinedSubs.addAll(ssSubs)
                    }
                    filterCandidateVideoFiles(collected)
                } else {
                    subVideos
                }

                if (actualVideos.isNotEmpty()) {
                    // Determine season name for this subfolder:
                    val determinedSeasonName = when {
                        SEASON_REGEX.containsMatchIn(subRawName) -> {
                            val parsed = AnimeFilenameParser.parseEpisodeFilename(actualVideos.first().name, subRawName, rawFolderName)
                            parsed.seasonName.ifBlank { subRawName }
                        }
                        subRawName.equals(cleanTitle, ignoreCase = true) || subRawName.equals(rawFolderName, ignoreCase = true) -> {
                            "第1季"
                        }
                        subRawName.startsWith("Your.Name", ignoreCase = true) || subRawName.contains("2160p", ignoreCase = true) || subRawName.contains("1080p", ignoreCase = true) -> {
                            "正片"
                        }
                        else -> {
                            // Use the subfolder's specific name (e.g. "绯弹的亚里亚 AA")
                            subRawName
                        }
                    }

                    actualVideos.forEach { vFile ->
                        val parsed = AnimeFilenameParser.parseEpisodeFilename(
                            filename = vFile.name,
                            parentFolderName = subRawName,
                            grandParentFolderName = rawFolderName
                        )
                        val existingEp = animeDao.getEpisodeByVideoUrl(vFile.path)

                        episodesList.add(
                            AnimeEpisodeEntity(
                                id = existingEp?.id ?: 0,
                                animeId = 0,
                                seasonName = if (determinedSeasonName != "正片") determinedSeasonName else parsed.seasonName,
                                episodeIndex = parsed.episodeIndex,
                                episodeNumber = parsed.episodeNumber,
                                title = parsed.cleanTitle,
                                videoUrl = vFile.path,
                                subtitleUrl = null,
                                durationMs = existingEp?.durationMs ?: 0L,
                                lastPlayedPositionMs = existingEp?.lastPlayedPositionMs ?: 0L,
                                isWatched = existingEp?.isWatched ?: false,
                                resolution = parsed.resolution,
                                videoCodec = parsed.videoCodec,
                                audioCodec = parsed.audioCodec,
                                releaseGroup = parsed.releaseGroup,
                                fileSize = vFile.size,
                                danmakuEpisodeId = existingEp?.danmakuEpisodeId,
                                danmakuTimeOffsetMs = existingEp?.danmakuTimeOffsetMs ?: 0L
                            )
                        )
                    }
                }
            }
        }

        val finalEpisodes = episodesList
        if (finalEpisodes.isEmpty()) return null

        val existingAnime = animeDao.getAnimeByWebdavPath(folderPath)
        val distinctSeasons = finalEpisodes.map { it.seasonName }.distinct()
        val now = System.currentTimeMillis()

        val animeEntity = AnimeEntity(
            id = existingAnime?.id ?: 0,
            title = cleanTitle,
            originalTitle = rawFolderName,
            webdavPath = folderPath,
            coverUrl = existingAnime?.coverUrl,
            localCoverPath = existingAnime?.localCoverPath,
            bangumiId = existingAnime?.bangumiId,
            score = existingAnime?.score ?: 0f,
            summary = existingAnime?.summary,
            airDate = existingAnime?.airDate,
            totalEpisodes = finalEpisodes.size,
            seasonCount = distinctSeasons.size,
            currentSeasonName = distinctSeasons.firstOrNull() ?: "正片",
            isMultiSeason = distinctSeasons.size > 1,
            lastWatchEpisodeId = existingAnime?.lastWatchEpisodeId,
            lastWatchEpisodeName = existingAnime?.lastWatchEpisodeName,
            lastWatchTimeMs = existingAnime?.lastWatchTimeMs ?: 0L,
            totalWatchDurationSeconds = existingAnime?.totalWatchDurationSeconds ?: 0,
            isFinished = existingAnime?.isFinished ?: false,
            sortOrder = existingAnime?.sortOrder ?: 0,
            createdAt = existingAnime?.createdAt ?: now,
            updatedAt = now
        )

        return AnimeScanResult(animeEntity, finalEpisodes)
    }

    suspend fun scanAnimeDirectory(
        webDavClient: WebDavClient,
        animeDao: AnimeDao,
        context: Context,
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting ultra-fast concurrent WebDAV scan...")
        val rootResources = webDavClient.listFiles("")
            .filter { !it.name.equals("Menu", ignoreCase = true) && !it.name.startsWith(".") }

        val rootFolders = rootResources.filter {
            it.isDirectory && !AnimeFilenameParser.isNonSeasonFolder(it.name) && !it.name.equals("BDMV", ignoreCase = true)
        }

        val candidateAnimeFolders = mutableListOf<WebDavResource>()
        val preloadedChildrenMap = java.util.concurrent.ConcurrentHashMap<String, List<WebDavResource>>()
        val discoverySemaphore = Semaphore(10)

        coroutineScope {
            rootFolders.map { folder ->
                async {
                    discoverySemaphore.withPermit {
                        val childResources = try { 
                            webDavClient.listFiles(folder.path)
                                .filter { !it.name.equals("Menu", ignoreCase = true) && !it.name.startsWith(".") && !it.name.endsWith(".m2ts", ignoreCase = true) && !it.name.equals("BDMV", ignoreCase = true) }
                        } catch (e: Exception) { emptyList() }
                        
                        preloadedChildrenMap[folder.path] = childResources

                        val directVideos = filterCandidateVideoFiles(childResources)
                        val subFolders = childResources.filter {
                            it.isDirectory && !AnimeFilenameParser.isNonSeasonFolder(it.name) && !it.name.equals("BDMV", ignoreCase = true)
                        }
                        val hasSeasonFolders = subFolders.any { SEASON_REGEX.containsMatchIn(it.name) }

                        val isCollection = folder.name.contains("合集") ||
                                folder.name.contains("全集") ||
                                folder.name.contains("Collection", ignoreCase = true)

                        val detected = mutableListOf<WebDavResource>()
                        if (directVideos.isNotEmpty() || hasSeasonFolders) {
                            detected.add(folder)
                        } else if (subFolders.isNotEmpty()) {
                            if (isCollection) {
                                for (sub in subFolders) {
                                    detected.add(sub)
                                }
                            } else {
                                detected.add(folder)
                            }
                        } else {
                            detected.add(folder)
                        }

                        synchronized(candidateAnimeFolders) {
                            candidateAnimeFolders.addAll(detected)
                        }
                    }
                }
            }.awaitAll()
        }

        val total = candidateAnimeFolders.size
        if (total == 0) return@withContext 0

        val progressCounter = AtomicInteger(0)
        val semaphore = Semaphore(12) // High parallel workers for ultra fast scanning
        val scannedCount = AtomicInteger(0)

        coroutineScope {
            candidateAnimeFolders.map { folder ->
                async {
                    semaphore.withPermit {
                        val currentIdx = progressCounter.incrementAndGet()
                        val rawFolderName = folder.name.ifBlank {
                            folder.path.trimEnd('/').substringAfterLast('/')
                        }
                        val cleanTitle = AnimeFilenameParser.cleanAnimeFolderName(rawFolderName)
                        onProgress?.invoke(currentIdx, total, cleanTitle)

                        try {
                            val scanResult = parseSingleAnimeFolder(
                                webDavClient = webDavClient,
                                animeDao = animeDao,
                                folderPath = folder.path,
                                animeDisplayName = rawFolderName,
                                context = context,
                                preloadedChildren = preloadedChildrenMap[folder.path]
                            )

                            if (scanResult != null && scanResult.episodes.isNotEmpty()) {
                                val animeEntity = scanResult.animeEntity
                                val episodes = scanResult.episodes

                                val animeId = animeDao.insertAnime(animeEntity)
                                animeDao.deleteEpisodesByAnimeId(animeId)
                                animeDao.insertEpisodes(episodes.map { it.copy(animeId = animeId) })

                                scannedCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scanning anime folder $rawFolderName", e)
                        }
                    }
                }
            }.awaitAll()
        }

        scannedCount.get()
    }

    suspend fun refreshSingleAnime(
        webDavClient: WebDavClient,
        animeDao: AnimeDao,
        anime: AnimeEntity,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val scanResult = parseSingleAnimeFolder(
                webDavClient = webDavClient,
                animeDao = animeDao,
                folderPath = anime.webdavPath,
                animeDisplayName = anime.originalTitle ?: anime.title,
                context = context
            )

            if (scanResult != null && scanResult.episodes.isNotEmpty()) {
                val episodes = scanResult.episodes
                animeDao.deleteEpisodesByAnimeId(anime.id)
                animeDao.insertEpisodes(episodes.map { it.copy(animeId = anime.id) })

                val distinctSeasons = episodes.map { it.seasonName }.distinct()
                val updatedAnime = anime.copy(
                    totalEpisodes = episodes.size,
                    seasonCount = distinctSeasons.size,
                    currentSeasonName = distinctSeasons.firstOrNull() ?: anime.currentSeasonName,
                    isMultiSeason = distinctSeasons.size > 1,
                    updatedAt = System.currentTimeMillis()
                )
                animeDao.updateAnime(updatedAnime)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh anime ${anime.title}", e)
            false
        }
    }

    private fun findBestSubtitle(
        subtitles: List<WebDavResource>,
        vBaseName: String,
        targetEpNumber: String? = null,
        targetSeasonName: String? = null
    ): WebDavResource? {
        if (subtitles.isEmpty()) return null

        // 1. Exact or prefix match (e.g. video.mkv -> video.sc.ass, video.chs.ass)
        val prefixCandidates = subtitles.filter { it.name.startsWith(vBaseName, ignoreCase = true) }
        val candidates = if (prefixCandidates.isNotEmpty()) {
            prefixCandidates
        } else {
            // 2. Fuzzy match by episode number or special tag
            val matchedByNumber = if (!targetEpNumber.isNullOrBlank()) {
                val cleanEp = targetEpNumber.trim()
                subtitles.filter { sub ->
                    val sName = sub.name.lowercase()
                    when {
                        cleanEp.contains(".") -> sName.contains(cleanEp)
                        cleanEp.startsWith("SP", ignoreCase = true) -> {
                            val spNum = cleanEp.substring(2).trim()
                            sName.contains("sp$spNum", ignoreCase = true) || sName.contains("sp $spNum", ignoreCase = true)
                        }
                        cleanEp.startsWith("OVA", ignoreCase = true) || cleanEp.startsWith("OAD", ignoreCase = true) -> {
                            sName.contains(cleanEp.lowercase()) || sName.contains(cleanEp.lowercase().replace(" ", ""))
                        }
                        cleanEp.equals("OP", ignoreCase = true) || cleanEp.equals("NCOP", ignoreCase = true) -> {
                            sName.contains("op") || sName.contains("ncop")
                        }
                        cleanEp.equals("ED", ignoreCase = true) || cleanEp.equals("NCED", ignoreCase = true) -> {
                            sName.contains("ed") || sName.contains("nced")
                        }
                        else -> {
                            val epNumInt = cleanEp.toIntOrNull()
                            if (epNumInt != null) {
                                val fmt2 = String.format("%02d", epNumInt)
                                sName.contains("[$fmt2]") || sName.contains(" $fmt2 ") || sName.contains("-$fmt2") || sName.contains("ep$fmt2") || sName.contains("e$fmt2") || sName.contains("_${fmt2}_") || sName.contains(".$fmt2.")
                            } else false
                        }
                    }
                }
            } else emptyList()

            if (matchedByNumber.isNotEmpty()) {
                if (!targetSeasonName.isNullOrBlank() && targetSeasonName != "正片") {
                    val sMatch = matchedByNumber.filter { it.path.contains(targetSeasonName, ignoreCase = true) || it.name.contains(targetSeasonName, ignoreCase = true) }
                    if (sMatch.isNotEmpty()) sMatch else matchedByNumber
                } else {
                    matchedByNumber
                }
            } else {
                emptyList()
            }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // Prioritize Chinese/Bilingual subtitles over Japanese/English
        return candidates.minByOrNull { sub ->
            val name = sub.name.lowercase()
            when {
                name.contains("双语") || name.contains("简日") || name.contains("繁日") || name.contains("chs&jpn") || name.contains("jap&chs") -> 1
                name.contains("chs") || name.contains(".sc.") || name.contains("gb") || name.contains("zh-hans") || name.contains("zh-cn") || name.contains("简") -> 2
                name.contains("cht") || name.contains(".tc.") || name.contains("big5") || name.contains("zh-hant") || name.contains("zh-tw") || name.contains("繁") -> 3
                name.contains("zh") || name.contains("chi") || name.contains("zho") || name.contains("cn") || name.contains("中文") -> 4
                !name.contains("ja") && !name.contains("jp") && !name.contains("en") -> 5
                name.contains("en") -> 6
                else -> 7
            }
        }
    }

    fun enrichAnimeMetadataInBackground(animeDao: AnimeDao, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val needCover = animeDao.getAnimesWithoutCover()
                for (anime in needCover) {
                    val scraped = AnimeMetadataScraper.scrape(anime.title)
                    if (scraped != null) {
                        val localCover = AnimeMetadataScraper.downloadCover(context, scraped.coverUrl, scraped.title)
                        val enriched = anime.copy(
                            title = scraped.title.ifBlank { anime.title },
                            originalTitle = scraped.originalTitle.ifBlank { anime.originalTitle },
                            coverUrl = scraped.coverUrl,
                            localCoverPath = localCover,
                            score = scraped.score.toFloat(),
                            summary = scraped.summary,
                            airDate = scraped.airDate,
                            totalEpisodes = if (scraped.totalEpisodes > 0) scraped.totalEpisodes else anime.totalEpisodes,
                            updatedAt = System.currentTimeMillis()
                        )
                        animeDao.updateAnime(enriched)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background metadata enrichment error", e)
            }
        }
    }

    suspend fun scanSingleAnime(
        webDavClient: WebDavClient,
        animeDao: AnimeDao,
        animeId: Long,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        val anime = animeDao.getAnimeById(animeId) ?: return@withContext false
        refreshSingleAnime(webDavClient, animeDao, anime, context)
    }
}
