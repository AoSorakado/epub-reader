package com.example.epubreader.data.hanime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HanimeApiClient(private val context: Context? = null) {

    companion object {
        private const val TAG = "HanimeApiClient"
        const val DEFAULT_BASE_URL = "https://hanime1.me/"
        val BACKUP_BASE_URLS = listOf("https://hanime1.me/", "https://hanime1.com/", "https://hanimeone.me/")

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"

        private val CLOUDFLARE_IPS = listOf(
            "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1"
        )
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val systemResult = Dns.SYSTEM.lookup(hostname)
                if (systemResult.isNotEmpty()) systemResult else getFallbackIps(hostname)
            } catch (e: Exception) {
                Log.w(TAG, "System DNS failed for $hostname, using fallback Cloudflare IPs", e)
                getFallbackIps(hostname)
            }
        }

        private fun getFallbackIps(hostname: String): List<InetAddress> {
            return if (hostname.contains("hanime", ignoreCase = true) || hostname.contains("javchu", ignoreCase = true)) {
                CLOUDFLARE_IPS.mapNotNull { ip ->
                    try {
                        InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(customDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                    list.removeAll { existing -> cookies.any { it.name == existing.name } }
                    list.addAll(cookies)
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", DEFAULT_BASE_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh-TW;q=0.9,zh;q=0.8,ja;q=0.7,en;q=0.6")

                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    suspend fun getHomePage(baseUrl: String = DEFAULT_BASE_URL): Result<HanimeHomePage> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val homePage = HanimeParser.parseHomePage(body)
            Result.success(homePage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get home page from $baseUrl", e)
            Result.failure(e)
        }
    }

    suspend fun getVideo(videoCode: String, baseUrl: String = DEFAULT_BASE_URL): Result<HanimeVideo> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/watch?v=$videoCode"
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty video page response"))
            val video = HanimeParser.parseVideo(body, videoCode)
            Result.success(video)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video info for $videoCode", e)
            Result.failure(e)
        }
    }

    suspend fun search(filter: HanimeSearchFilter, baseUrl: String = DEFAULT_BASE_URL): Result<List<HanimeInfo>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "${baseUrl.trimEnd('/')}/search".toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext Result.failure(Exception("Invalid search URL"))

            urlBuilder.addQueryParameter("page", filter.page.toString())
            val effectiveQuery = if (filter.query.isNullOrBlank() && (filter.genre == "裏番" || filter.genre == "里番" || filter.genre == "泡麵番" || filter.genre == "泡面番")) {
                filter.genre
            } else {
                filter.query
            }
            if (!effectiveQuery.isNullOrBlank()) {
                urlBuilder.addQueryParameter("query", effectiveQuery)
            }
            if (!filter.genre.isNullOrBlank() && filter.genre != "全部" && filter.genre != "裏番" && filter.genre != "里番" && filter.genre != "泡麵番" && filter.genre != "泡面番") {
                urlBuilder.addQueryParameter("genre", filter.genre)
            }
            if (!filter.sort.isNullOrBlank()) {
                urlBuilder.addQueryParameter("sort", filter.sort)
            }
            if (filter.broad) {
                urlBuilder.addQueryParameter("broad", "on")
            }
            if (!filter.date.isNullOrBlank()) {
                urlBuilder.addQueryParameter("date", filter.date)
            }
            if (!filter.duration.isNullOrBlank()) {
                urlBuilder.addQueryParameter("duration", filter.duration)
            }
            for (tag in filter.tags) {
                if (tag.isNotBlank()) urlBuilder.addQueryParameter("tags[]", tag)
            }
            for (brand in filter.brands) {
                if (brand.isNotBlank()) urlBuilder.addQueryParameter("brands[]", brand)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty search response"))
            val results = HanimeParser.parseSearchResults(body, baseUrl)
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform search with filter $filter", e)
            Result.failure(e)
        }
    }

    suspend fun getComments(videoCode: String, page: Int = 1, baseUrl: String = DEFAULT_BASE_URL): Result<List<HanimeComment>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/comments?type=video&id=$videoCode&page=$page"
            val request = Request.Builder()
                .url(url)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty comments response"))
            val comments = HanimeParser.parseComments(body)
            Result.success(comments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get comments for video $videoCode", e)
            Result.failure(e)
        }
    }
}
