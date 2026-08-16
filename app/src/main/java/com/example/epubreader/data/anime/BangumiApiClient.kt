package com.example.epubreader.data.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BangumiSubject(
    val id: Long,
    val name: String,
    val nameCn: String,
    val summary: String,
    val score: Float,
    val rank: Int,
    val airDate: String,
    val epsCount: Int,
    val coverLarge: String,
    val coverMedium: String,
    val tags: List<String>
)

data class BangumiEpisode(
    val id: Long,
    val ep: Int,
    val sort: Int,
    val name: String,
    val nameCn: String,
    val duration: String,
    val airDate: String,
    val desc: String
)

object BangumiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "EpubReaderAnime/1.0 (Android; BangumiScraper)"

    suspend fun searchSubject(keyword: String): BangumiSubject? = withContext(Dispatchers.IO) {
        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://api.bgm.tv/v0/search/subjects?keyword=$encodedKeyword&filter[type]=2&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@withContext null
            if (dataArray.length() == 0) return@withContext null

            val firstItem = dataArray.getJSONObject(0)
            val id = firstItem.optLong("id")
            if (id <= 0) return@withContext null

            // Fetch full details for the first matching subject
            getSubjectDetails(id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getSubjectDetails(subjectId: Long): BangumiSubject? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bgm.tv/v0/subjects/$subjectId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val name = json.optString("name")
            val nameCn = json.optString("name_cn").ifBlank { name }
            val summary = json.optString("summary")
            val rating = json.optJSONObject("rating")
            val score = rating?.optDouble("score", 0.0)?.toFloat() ?: 0f
            val rank = rating?.optInt("rank", 0) ?: 0
            val date = json.optString("date")
            val eps = json.optInt("eps", 0)

            val images = json.optJSONObject("images")
            val coverLarge = images?.optString("large") ?: ""
            val coverMedium = images?.optString("common") ?: coverLarge

            val tagsList = mutableListOf<String>()
            val tagsArr = json.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until minOf(tagsArr.length(), 6)) {
                    val tagObj = tagsArr.getJSONObject(i)
                    tagsList.add(tagObj.optString("name"))
                }
            }

            BangumiSubject(
                id = subjectId,
                name = name,
                nameCn = nameCn,
                summary = summary,
                score = score,
                rank = rank,
                airDate = date,
                epsCount = eps,
                coverLarge = coverLarge,
                coverMedium = coverMedium,
                tags = tagsList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getEpisodes(subjectId: Long): List<BangumiEpisode> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bgm.tv/v0/episodes?subject_id=$subjectId&limit=100"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@withContext emptyList()

            val result = mutableListOf<BangumiEpisode>()
            for (i in 0 until dataArray.length()) {
                val epObj = dataArray.getJSONObject(i)
                val type = epObj.optInt("type", 0)
                if (type == 0 || type == 1) { // 0 = main, 1 = SP
                    result.add(
                        BangumiEpisode(
                            id = epObj.optLong("id"),
                            ep = epObj.optInt("ep", i + 1),
                            sort = epObj.optInt("sort", i + 1),
                            name = epObj.optString("name"),
                            nameCn = epObj.optString("name_cn").ifBlank { epObj.optString("name") },
                            duration = epObj.optString("duration"),
                            airDate = epObj.optString("airdate"),
                            desc = epObj.optString("desc")
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
