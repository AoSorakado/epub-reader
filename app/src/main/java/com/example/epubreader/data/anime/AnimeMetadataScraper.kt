package com.example.epubreader.data.anime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ScrapedAnimeInfo(
    val title: String,
    val originalTitle: String,
    val coverUrl: String,
    val score: Float,
    val summary: String,
    val airDate: String,
    val totalEpisodes: Int,
    val source: String // "bangumi" or "douban"
)

object AnimeMetadataScraper {

    private const val TAG = "AnimeMetadataScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun scrape(cleanTitle: String, rawFolderName: String = ""): ScrapedAnimeInfo? = withContext(Dispatchers.IO) {
        val keywords = listOf(
            cleanTitle,
            cleanTitle.replace(Regex("[0-9]+$"), "").trim(),
            rawFolderName.replace(Regex("(?i)^[A-Z]\\s*4k\\s*"), "").trim()
        ).distinct().filter { it.isNotBlank() }

        for (kw in keywords) {
            // 1. Try Bangumi v0 POST API
            val bgmPostResult = searchBangumiV0(kw)
            if (bgmPostResult != null) {
                Log.d(TAG, "Scraped from Bangumi v0: ${bgmPostResult.title} ($kw)")
                return@withContext bgmPostResult
            }

            // 2. Try Bangumi Legacy Search API
            val bgmLegacyResult = searchBangumiLegacy(kw)
            if (bgmLegacyResult != null) {
                Log.d(TAG, "Scraped from Bangumi Legacy: ${bgmLegacyResult.title} ($kw)")
                return@withContext bgmLegacyResult
            }

            // 3. Try Douban (豆瓣) API
            val doubanResult = searchDouban(kw)
            if (doubanResult != null) {
                Log.d(TAG, "Scraped from Douban: ${doubanResult.title} ($kw)")
                return@withContext doubanResult
            }
        }

        null
    }

    suspend fun searchMultiple(keyword: String): List<ScrapedAnimeInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScrapedAnimeInfo>()
        if (keyword.isBlank()) return@withContext emptyList()

        // 1. Bangumi v0 Search
        try {
            val v0List = searchBangumiV0List(keyword)
            results.addAll(v0List)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Bangumi Legacy Search (if needed or to complement)
        if (results.size < 5) {
            try {
                val legacyList = searchBangumiLegacyList(keyword)
                for (item in legacyList) {
                    if (results.none { it.title == item.title || (it.originalTitle.isNotBlank() && it.originalTitle == item.originalTitle) }) {
                        results.add(item)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Douban Search
        try {
            val doubanList = searchDoubanList(keyword)
            for (item in doubanList) {
                if (results.none { it.title == item.title }) {
                    results.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    // --- 1. Bangumi v0 API (POST) ---
    private fun searchBangumiV0(keyword: String): ScrapedAnimeInfo? {
        return searchBangumiV0List(keyword).firstOrNull()
    }

    private fun searchBangumiV0List(keyword: String): List<ScrapedAnimeInfo> {
        val list = mutableListOf<ScrapedAnimeInfo>()
        try {
            val jsonBody = JSONObject().apply {
                put("keyword", keyword)
                put("filter", JSONObject().apply {
                    put("type", JSONArray().apply { put(2) }) // 2 = Anime
                })
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/search/subjects?limit=10")
                .post(requestBody)
                .header("User-Agent", "EpubReaderApp/1.0 (Android; anime-scraper)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return emptyList()

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optLong("id")
                val name = item.optString("name")
                val nameCn = item.optString("name_cn").ifBlank { name }
                val summary = item.optString("summary")
                val score = item.optJSONObject("rating")?.optDouble("score", 0.0)?.toFloat() ?: 0f
                val date = item.optString("date")
                val eps = item.optInt("eps", 0)

                val images = item.optJSONObject("images")
                val coverLarge = images?.optString("large")
                    ?: images?.optString("common")
                    ?: images?.optString("medium")
                    ?: ""

                list.add(
                    ScrapedAnimeInfo(
                        title = nameCn.ifBlank { name },
                        originalTitle = name,
                        coverUrl = coverLarge,
                        score = score,
                        summary = summary,
                        airDate = date,
                        totalEpisodes = eps,
                        source = "bangumi"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- 2. Bangumi Legacy Search API (GET) ---
    private fun searchBangumiLegacy(keyword: String): ScrapedAnimeInfo? {
        return searchBangumiLegacyList(keyword).firstOrNull()
    }

    private fun searchBangumiLegacyList(keyword: String): List<ScrapedAnimeInfo> {
        val results = mutableListOf<ScrapedAnimeInfo>()
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://api.bgm.tv/search/subject/$encoded?type=2&responseGroup=large&max_results=10"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "EpubReaderApp/1.0 (Android; anime-scraper)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val list = json.optJSONArray("list") ?: return emptyList()

            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val name = item.optString("name")
                val nameCn = item.optString("name_cn").ifBlank { name }
                val summary = item.optString("summary")
                val score = item.optJSONObject("rating")?.optDouble("score", 0.0)?.toFloat() ?: 0f
                val airDate = item.optString("air_date")
                val eps = item.optInt("eps", 0)

                val images = item.optJSONObject("images")
                val coverLarge = images?.optString("large")
                    ?: images?.optString("common")
                    ?: images?.optString("medium")
                    ?: ""

                results.add(
                    ScrapedAnimeInfo(
                        title = nameCn.ifBlank { name },
                        originalTitle = name,
                        coverUrl = coverLarge,
                        score = score,
                        summary = summary,
                        airDate = airDate,
                        totalEpisodes = eps,
                        source = "bangumi"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    // --- 3. Douban (豆瓣) API ---
    private fun searchDouban(keyword: String): ScrapedAnimeInfo? {
        return searchDoubanList(keyword).firstOrNull()
    }

    private fun searchDoubanList(keyword: String): List<ScrapedAnimeInfo> {
        val results = mutableListOf<ScrapedAnimeInfo>()
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://movie.douban.com/j/subject_suggest?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://movie.douban.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val array = JSONArray(body)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val title = item.optString("title")
                val subTitle = item.optString("sub_title")
                val imgUrl = item.optString("img")
                val year = item.optString("year")

                // Convert Douban thumbnail to high-res poster
                val highResCover = imgUrl.replace("/s_ratio_poster/", "/l_ratio_poster/")
                    .replace("/s_ratio_celebrity/", "/l_ratio_celebrity/")

                results.add(
                    ScrapedAnimeInfo(
                        title = title,
                        originalTitle = subTitle.ifBlank { title },
                        coverUrl = highResCover,
                        score = 0f,
                        summary = "",
                        airDate = year,
                        totalEpisodes = 0,
                        source = "douban"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    // Download image to local file
    fun downloadCover(context: Context, coverUrl: String, animeTitle: String): String? {
        if (coverUrl.isBlank()) return null
        return try {
            val coversDir = File(context.filesDir, "anime_covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val coverFile = File(coversDir, "anime_${animeTitle.hashCode().toString().replace("-", "")}.jpg")
            
            val request = Request.Builder()
                .url(coverUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", if (coverUrl.contains("douban")) "https://movie.douban.com/" else "https://bgm.tv/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body ?: return null

            val output = FileOutputStream(coverFile)
            body.byteStream().use { input ->
                output.use { out -> input.copyTo(out) }
            }
            coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
