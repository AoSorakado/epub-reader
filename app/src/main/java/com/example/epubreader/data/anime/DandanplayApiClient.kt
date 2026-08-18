package com.example.epubreader.data.anime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

data class DanmakuItem(
    val timeMs: Long,
    val mode: Int, // 1: scroll, 4: bottom, 5: top
    val color: Int, // e.g. 0xFFFFFF
    val senderId: String,
    val text: String
)

data class DandanplayEpisodeMatch(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeTitle: String
)

data class DandanAnimeResult(
    val animeId: Long,
    val animeTitle: String,
    val typeDescription: String,
    val episodes: List<DandanEpisodeResult>
)

data class DandanEpisodeResult(
    val episodeId: Long,
    val episodeTitle: String,
    val animeTitle: String
)

object DandanplayApiClient {

    private const val TAG = "DanmakuClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Primary Danmaku fetcher: queries Bilibili open danmaku without requiring auth keys,
     * falling back to dandanplay.
     */
    suspend fun fetchDanmaku(
        animeTitle: String,
        episodeNumber: String,
        seasonName: String = ""
    ): List<DanmakuItem> = withContext(Dispatchers.IO) {
        // 1. Try Dandanplay First (curated anime database with correct episode mapping)
        try {
            val match = matchEpisode(animeTitle, episodeNumber)
            if (match != null) {
                val dandanResult = getDanmakuComments(match.episodeId)
                if (dandanResult.isNotEmpty()) {
                    Log.d(TAG, "Fetched ${dandanResult.size} danmaku items from Dandanplay for $animeTitle $episodeNumber")
                    return@withContext dandanResult
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dandanplay danmaku fetch failed", e)
        }

        // 2. Try Bilibili Open Search & CID Fallback
        try {
            val biliResult = fetchBilibiliDanmaku(animeTitle, episodeNumber, seasonName)
            if (biliResult.isNotEmpty()) {
                Log.d(TAG, "Fetched ${biliResult.size} danmaku items from Bilibili for $animeTitle $episodeNumber")
                return@withContext biliResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili danmaku fetch failed", e)
        }

        emptyList()
    }

    private suspend fun fetchBilibiliDanmaku(
        animeTitle: String,
        episodeNumber: String,
        seasonName: String
    ): List<DanmakuItem> = withContext(Dispatchers.IO) {
        val cleanEp = episodeNumber.replace(Regex("(?i)^0+"), "").ifBlank { episodeNumber }
        val searchKeywords = mutableListOf<String>()
        
        if (seasonName.isNotBlank() && seasonName != "正片") {
            searchKeywords.add("$animeTitle $seasonName $cleanEp")
            searchKeywords.add("$animeTitle $seasonName 第$cleanEp")
        }
        searchKeywords.add("$animeTitle 第${cleanEp}集")
        searchKeywords.add("$animeTitle $cleanEp")
        searchKeywords.add("$animeTitle EP$cleanEp")
        searchKeywords.add(animeTitle)

        for (keyword in searchKeywords) {
            try {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val searchUrl = "https://api.bilibili.com/x/web-interface/search/all/v2?keyword=$encoded"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.bilibili.com")
                    .build()

                val searchResp = client.newCall(searchReq).execute()
                if (!searchResp.isSuccessful) continue

                val searchBody = searchResp.body?.string() ?: continue
                val json = JSONObject(searchBody)
                val data = json.optJSONObject("data") ?: continue
                val resultList = data.optJSONArray("result") ?: continue

                var targetBvid = ""
                for (i in 0 until resultList.length()) {
                    val rObj = resultList.optJSONObject(i) ?: continue
                    if (rObj.optString("result_type") == "video") {
                        val videoData = rObj.optJSONArray("data")
                        if (videoData != null && videoData.length() > 0) {
                            targetBvid = videoData.getJSONObject(0).optString("bvid")
                            break
                        }
                    } else if (rObj.has("bvid")) {
                        targetBvid = rObj.optString("bvid")
                        break
                    }
                }

                if (targetBvid.isBlank()) continue

                // Fetch CID from pagelist
                val pagelistUrl = "https://api.bilibili.com/x/player/pagelist?bvid=$targetBvid&jsonp=jsonp"
                val pageReq = Request.Builder()
                    .url(pagelistUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.bilibili.com")
                    .build()

                val pageResp = client.newCall(pageReq).execute()
                if (!pageResp.isSuccessful) continue
                val pageBody = pageResp.body?.string() ?: continue
                val pageJson = JSONObject(pageBody)
                val pages = pageJson.optJSONArray("data") ?: continue
                if (pages.length() == 0) continue

                // Find matching episode page or fallback to first
                var targetCid = 0L
                val cleanEpInt = cleanEp.toIntOrNull()
                if (cleanEpInt != null && pages.length() >= cleanEpInt) {
                    targetCid = pages.getJSONObject(cleanEpInt - 1).optLong("cid")
                }
                if (targetCid == 0L) {
                    targetCid = pages.getJSONObject(0).optLong("cid")
                }
                if (targetCid == 0L) continue

                // Fetch Danmaku XML from Bilibili
                val danmakuUrl = "https://comment.bilibili.com/$targetCid.xml"
                val dmReq = Request.Builder()
                    .url(danmakuUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.bilibili.com")
                    .build()

                val dmResp = client.newCall(dmReq).execute()
                if (!dmResp.isSuccessful) continue
                val bytes = dmResp.body?.bytes() ?: continue
                val xmlString = decompressBytes(bytes)
                val items = parseXmlDanmaku(xmlString)
                if (items.isNotEmpty()) {
                    return@withContext items
                }
            } catch (e: Exception) {
                // Continue to next keyword
            }
        }
        emptyList()
    }

    private fun decompressBytes(bytes: ByteArray): String {
        return try {
            val inflater = Inflater(true)
            inflater.setInput(bytes)
            val outputStream = ByteArrayOutputStream(bytes.size * 2)
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                outputStream.write(buffer, 0, count)
            }
            outputStream.close()
            inflater.end()
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            try {
                GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e2: Exception) {
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    suspend fun matchEpisode(animeTitle: String, episodeNumber: String): DandanplayEpisodeMatch? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = animeTitle.trim()
            val searchResults = searchAnimeEpisodes(cleanTitle)
            if (searchResults.isNotEmpty()) {
                val firstAnime = searchResults[0]
                val cleanEpNum = episodeNumber.toIntOrNull() ?: 1
                val matchedEp = firstAnime.episodes.getOrNull(cleanEpNum - 1)
                    ?: firstAnime.episodes.firstOrNull { it.episodeTitle.contains("$cleanEpNum") }
                    ?: firstAnime.episodes.firstOrNull()

                if (matchedEp != null) {
                    return@withContext DandanplayEpisodeMatch(
                        animeId = firstAnime.animeId,
                        animeTitle = firstAnime.animeTitle,
                        episodeId = matchedEp.episodeId,
                        episodeTitle = matchedEp.episodeTitle
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "matchEpisode error", e)
        }
        null
    }

    suspend fun searchAnimeEpisodes(keyword: String): List<DandanAnimeResult> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val resultList = mutableListOf<DandanAnimeResult>()
        try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val searchUrl = "https://danmaku-api.152468.xyz/api/v2/search/anime?keyword=$encoded"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "mpv_danmaku/1.0")
                .header("Accept", "application/json")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val searchBody = searchResp.body?.string() ?: ""
                val json = JSONObject(searchBody)
                val animes = json.optJSONArray("animes")
                if (animes != null) {
                    for (i in 0 until animes.length()) {
                        val animeObj = animes.optJSONObject(i) ?: continue
                        val animeId = animeObj.optLong("animeId")
                        val bangumiId = animeObj.optString("bangumiId", animeId.toString())
                        val animeTitle = animeObj.optString("animeTitle")
                        val typeDescription = animeObj.optString("typeDescription", "TV动画")

                        try {
                            val epUrl = "https://danmaku-api.152468.xyz/api/v2/bangumi/$bangumiId"
                            val epReq = Request.Builder()
                                .url(epUrl)
                                .header("User-Agent", "mpv_danmaku/1.0")
                                .header("Accept", "application/json")
                                .build()
                            val epResp = client.newCall(epReq).execute()
                            if (epResp.isSuccessful) {
                                val epBody = epResp.body?.string() ?: ""
                                val epJson = JSONObject(epBody)
                                val bangumiObj = epJson.optJSONObject("bangumi")
                                val episodes = bangumiObj?.optJSONArray("episodes")
                                if (episodes != null && episodes.length() > 0) {
                                    val epList = mutableListOf<DandanEpisodeResult>()
                                    for (j in 0 until episodes.length()) {
                                        val ep = episodes.optJSONObject(j) ?: continue
                                        val episodeId = ep.optLong("episodeId")
                                        val episodeTitle = ep.optString("episodeTitle")
                                        epList.add(
                                            DandanEpisodeResult(
                                                episodeId = episodeId,
                                                episodeTitle = episodeTitle,
                                                animeTitle = animeTitle
                                            )
                                        )
                                    }
                                    resultList.add(
                                        DandanAnimeResult(
                                            animeId = animeId,
                                            animeTitle = animeTitle,
                                            typeDescription = typeDescription,
                                            episodes = epList
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed fetching bangumi episodes for $bangumiId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchAnimeEpisodes error", e)
        }
        resultList
    }

    suspend fun getDanmakuComments(episodeId: Long): List<DanmakuItem> = withContext(Dispatchers.IO) {
        // 1. Dandanplay comments endpoint from MPV API server
        try {
            val url = "https://danmaku-api.152468.xyz/api/v2/comment/$episodeId?withRelated=true&chConvert=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "mpv_danmaku/1.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val comments = json.optJSONArray("comments")
                if (comments != null && comments.length() > 0) {
                    val result = ArrayList<DanmakuItem>(comments.length())
                    for (i in 0 until comments.length()) {
                        val item = comments.getJSONObject(i)
                        val p = item.optString("p") // "time,mode,color,sender"
                        val m = item.optString("m")

                        val parts = p.split(",")
                        val timeSec = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                        val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        val color = parts.getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF
                        val sender = parts.getOrNull(3) ?: ""

                        result.add(
                            DanmakuItem(
                                timeMs = (timeSec * 1000).toLong(),
                                mode = mode,
                                color = color,
                                senderId = sender,
                                text = m
                            )
                        )
                    }
                    result.sortBy { it.timeMs }
                    Log.d(TAG, "Loaded ${result.size} danmaku comments from Dandanplay episode $episodeId")
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading comments from episodeId $episodeId", e)
        }

        // 2. Fallback to Bilibili CID XML if episodeId was a CID
        try {
            val danmakuUrl = "https://comment.bilibili.com/$episodeId.xml"
            val dmReq = Request.Builder()
                .url(danmakuUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .build()

            val dmResp = client.newCall(dmReq).execute()
            if (dmResp.isSuccessful) {
                val bytes = dmResp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val xmlString = decompressBytes(bytes)
                    val items = parseXmlDanmaku(xmlString)
                    if (items.isNotEmpty()) {
                        return@withContext items
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        emptyList()
    }

    // Parse Bilibili / Dandanplay standard XML danmaku format: <d p="12.34,1,25,16777215,...">Text</d>
    fun parseXmlDanmaku(xmlContent: String): List<DanmakuItem> {
        val result = mutableListOf<DanmakuItem>()
        try {
            val dRegex = Regex("<d\\s+p=\"([^\"]+)\"[^>]*>([^<]*)</d>")
            val matches = dRegex.findAll(xmlContent)
            for (m in matches) {
                val p = m.groupValues[1]
                val text = m.groupValues[2]
                val parts = p.split(",")
                val timeSec = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1

                // In Bilibili XML (>= 5 fields): p[0]=time, p[1]=mode, p[2]=fontSize, p[3]=color(16777215)
                // In Dandanplay format: p[0]=time, p[1]=mode, p[2]=color, p[3]=sender
                val parsedColor = if (parts.size >= 5) {
                    parts.getOrNull(3)?.toIntOrNull() ?: 0xFFFFFF
                } else {
                    parts.getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF
                }
                val sender = if (parts.size >= 7) parts.getOrNull(6) ?: "" else parts.getOrNull(3) ?: ""

                // Ensure pure white default if 0, fontSize misparse, or standard white
                val cleanColor = if (parsedColor <= 0 || parsedColor < 256 || parsedColor == 16777215) 0xFFFFFF else parsedColor

                if (text.isNotBlank()) {
                    result.add(
                        DanmakuItem(
                            timeMs = (timeSec * 1000).toLong(),
                            mode = mode,
                            color = cleanColor,
                            senderId = sender,
                            text = text
                        )
                    )
                }
            }
            result.sortBy { it.timeMs }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
