package com.example.epubreader.data.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

object DandanplayApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "EpubReaderAnime/1.0 (Android; DanmakuClient)"

    suspend fun matchEpisode(animeTitle: String, episodeNumber: String): DandanplayEpisodeMatch? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(animeTitle, "UTF-8")
            val encodedEpisode = URLEncoder.encode(episodeNumber, "UTF-8")
            val url = "https://api.dandanplay.net/api/v2/search/episodes?anime=$encodedTitle&episode=$encodedEpisode"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val animes = json.optJSONArray("animes") ?: return@withContext null
            if (animes.length() == 0) return@withContext null

            val animeObj = animes.getJSONObject(0)
            val animeId = animeObj.optLong("animeId")
            val matchedAnimeTitle = animeObj.optString("animeTitle")
            val episodes = animeObj.optJSONArray("episodes") ?: return@withContext null
            if (episodes.length() == 0) return@withContext null

            val epObj = episodes.getJSONObject(0)
            val episodeId = epObj.optLong("episodeId")
            val episodeTitle = epObj.optString("episodeTitle")

            DandanplayEpisodeMatch(
                animeId = animeId,
                animeTitle = matchedAnimeTitle,
                episodeId = episodeId,
                episodeTitle = episodeTitle
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getDanmakuComments(episodeId: Long): List<DanmakuItem> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.dandanplay.net/api/v2/comment/$episodeId?withRelated=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val comments = json.optJSONArray("comments") ?: return@withContext emptyList()

            val result = ArrayList<DanmakuItem>(comments.length())
            for (i in 0 until comments.length()) {
                val item = comments.getJSONObject(i)
                val p = item.optString("p") // "time,mode,color,sender" e.g. "12.45,1,16777215,user123"
                val m = item.optString("m") // danmaku text content

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
            // Sort comments by timestamp
            result.sortBy { it.timeMs }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
