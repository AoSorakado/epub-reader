package com.example.epubreader.data.anime

import android.content.Context
import com.example.epubreader.data.db.AnimeDao
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.network.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object AnimeWebDavScanner {

    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "ts", "m2ts")
    private val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")

    suspend fun scanAnimeDirectory(
        webDavClient: WebDavClient,
        animeRootPath: String,
        animeDao: AnimeDao,
        context: Context,
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var scannedCount = 0
        try {
            val rootResources = webDavClient.listFiles(animeRootPath)
                .filter { it.isDirectory && !it.name.equals("Menu", ignoreCase = true) }

            val total = rootResources.size

            rootResources.forEachIndexed { index, folder ->
                onProgress?.invoke(index + 1, total, folder.name)
                try {
                    val rawFolderName = folder.name.ifBlank {
                        folder.path.trimEnd('/').substringAfterLast('/')
                    }
                    val cleanTitle = AnimeFilenameParser.cleanAnimeFolderName(rawFolderName)

                    val childResources = webDavClient.listFiles(folder.path)
                    val subFolders = childResources.filter { it.isDirectory && !it.name.equals("Menu", ignoreCase = true) }
                    val videoFiles = childResources.filter { 
                        !it.isDirectory && it.name.substringAfterLast(".").lowercase() in VIDEO_EXTENSIONS 
                    }
                    val subtitleFiles = childResources.filter { 
                        !it.isDirectory && it.name.substringAfterLast(".").lowercase() in SUBTITLE_EXTENSIONS 
                    }

                    val isMultiSeason = subFolders.isNotEmpty() && videoFiles.isEmpty()

                    // Check if anime already exists in DB
                    val existingAnime = animeDao.getAnimeByWebdavPath(folder.path)

                    // Scrape Bangumi metadata if new or missing metadata
                    val bangumiSubject = if (existingAnime?.coverUrl.isNullOrBlank()) {
                        BangumiApiClient.searchSubject(cleanTitle)
                    } else null

                    val coverUrl = bangumiSubject?.coverLarge ?: existingAnime?.coverUrl ?: ""
                    val score = bangumiSubject?.score ?: existingAnime?.score ?: 0f
                    val summary = bangumiSubject?.summary ?: existingAnime?.summary ?: ""
                    val airDate = bangumiSubject?.airDate ?: existingAnime?.airDate ?: ""
                    val bangumiId = bangumiSubject?.id ?: existingAnime?.bangumiId

                    // Download cover locally for instant offline loading
                    var localCoverPath = existingAnime?.localCoverPath
                    if (localCoverPath.isNullOrBlank() && coverUrl.isNotBlank()) {
                        try {
                            val coversDir = File(context.filesDir, "anime_covers")
                            if (!coversDir.exists()) coversDir.mkdirs()
                            val coverFile = File(coversDir, "anime_${System.currentTimeMillis()}_${cleanTitle.hashCode().toString().replace("-", "")}.jpg")
                            val input = URL(coverUrl).openStream()
                            val output = FileOutputStream(coverFile)
                            input.use { inp -> output.use { out -> inp.copyTo(out) } }
                            localCoverPath = coverFile.absolutePath
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val animeEntity = AnimeEntity(
                        id = existingAnime?.id ?: 0,
                        title = cleanTitle,
                        originalTitle = bangumiSubject?.name ?: existingAnime?.originalTitle ?: rawFolderName,
                        coverUrl = coverUrl,
                        localCoverPath = localCoverPath,
                        bangumiId = bangumiId,
                        score = score,
                        summary = summary,
                        airDate = airDate,
                        totalEpisodes = bangumiSubject?.epsCount ?: existingAnime?.totalEpisodes ?: 0,
                        seasonCount = if (isMultiSeason) subFolders.size else 1,
                        webdavPath = folder.path,
                        isMultiSeason = isMultiSeason,
                        lastWatchEpisodeId = existingAnime?.lastWatchEpisodeId,
                        lastWatchEpisodeName = existingAnime?.lastWatchEpisodeName,
                        lastWatchTimeMs = existingAnime?.lastWatchTimeMs ?: 0L,
                        totalWatchDurationSeconds = existingAnime?.totalWatchDurationSeconds ?: 0L,
                        isFinished = existingAnime?.isFinished ?: false,
                        updatedAt = System.currentTimeMillis()
                    )

                    val animeId = animeDao.insertAnime(animeEntity)
                    val episodesList = mutableListOf<AnimeEpisodeEntity>()

                    if (isMultiSeason) {
                        // Multi-season: Scan each season folder
                        subFolders.forEach { seasonFolder ->
                            val sName = seasonFolder.name.ifBlank {
                                seasonFolder.path.trimEnd('/').substringAfterLast('/')
                            }
                            val sResources = webDavClient.listFiles(seasonFolder.path)
                            val sVideos = sResources.filter { 
                                !it.isDirectory && it.name.substringAfterLast(".").lowercase() in VIDEO_EXTENSIONS 
                            }
                            val sSubtitles = sResources.filter { 
                                !it.isDirectory && it.name.substringAfterLast(".").lowercase() in SUBTITLE_EXTENSIONS 
                            }

                            sVideos.forEach { vFile ->
                                val parsed = AnimeFilenameParser.parseEpisodeFilename(
                                    filename = vFile.name,
                                    parentFolderName = sName,
                                    grandParentFolderName = rawFolderName
                                )
                                // Match subtitle
                                val vBaseName = vFile.name.substringBeforeLast(".")
                                val matchedSub = sSubtitles.find { it.name.startsWith(vBaseName) }

                                val existingEp = animeDao.getEpisodeByVideoUrl(vFile.path)

                                episodesList.add(
                                    AnimeEpisodeEntity(
                                        id = existingEp?.id ?: 0,
                                        animeId = animeId,
                                        seasonName = sName,
                                        episodeIndex = parsed.episodeIndex,
                                        episodeNumber = parsed.episodeNumber,
                                        title = parsed.cleanTitle,
                                        videoUrl = vFile.path,
                                        subtitleUrl = matchedSub?.path,
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
                    } else {
                        // Single-season
                        videoFiles.forEach { vFile ->
                            val parsed = AnimeFilenameParser.parseEpisodeFilename(
                                filename = vFile.name,
                                parentFolderName = rawFolderName
                            )
                            val vBaseName = vFile.name.substringBeforeLast(".")
                            val matchedSub = subtitleFiles.find { it.name.startsWith(vBaseName) }

                            val existingEp = animeDao.getEpisodeByVideoUrl(vFile.path)

                            episodesList.add(
                                AnimeEpisodeEntity(
                                    id = existingEp?.id ?: 0,
                                    animeId = animeId,
                                    seasonName = "正片",
                                    episodeIndex = parsed.episodeIndex,
                                    episodeNumber = parsed.episodeNumber,
                                    title = parsed.cleanTitle,
                                    videoUrl = vFile.path,
                                    subtitleUrl = matchedSub?.path,
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

                    if (episodesList.isNotEmpty()) {
                        animeDao.insertEpisodes(episodesList)
                        scannedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scannedCount
    }
}
