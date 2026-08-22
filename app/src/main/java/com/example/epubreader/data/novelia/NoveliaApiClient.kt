package com.example.epubreader.data.novelia

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NoveliaApiClient(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("novelia_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val BASE_URL = "https://n.novelia.cc"
        const val AUTH_URL = "https://auth.novelia.cc"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                list.removeAll { existing -> cookies.any { it.name == existing.name } }
                list.addAll(cookies)
                saveCookiesToPrefs()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = mutableListOf<Cookie>()
                cookieStore[url.host]?.let { list.addAll(it) }
                cookieStore["auth.novelia.cc"]?.let { list.addAll(it) }
                cookieStore["n.novelia.cc"]?.let { list.addAll(it) }
                return list
            }
        })
        .addInterceptor { chain ->
            val req = chain.request()
            val savedCookieStr = prefs.getString("saved_cookie_header", null)
            val token = prefs.getString("token", null)
            
            val builder = req.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("Accept", "application/json, text/plain, */*")
            
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            if (!savedCookieStr.isNullOrBlank() && req.header("Cookie") == null) {
                builder.header("Cookie", savedCookieStr)
            }
            chain.proceed(builder.build())
        }
        .build()

    init {
        loadCookiesFromPrefs()
    }

    private fun saveCookiesToPrefs() {
        val allCookies = cookieStore.values.flatten().distinctBy { it.name }
        val cookieString = allCookies.joinToString("; ") { "${it.name}=${it.value}" }
        prefs.edit().putString("saved_cookie_header", cookieString).apply()
    }

    private fun loadCookiesFromPrefs() {
        val saved = prefs.getString("saved_cookie_header", null) ?: return
        val baseUrl = BASE_URL.toHttpUrlOrNull() ?: return
        val authUrl = AUTH_URL.toHttpUrlOrNull() ?: return
        val list = mutableListOf<Cookie>()
        val authList = mutableListOf<Cookie>()
        for (part in saved.split(";")) {
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                list.add(
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(baseUrl.host)
                        .path("/")
                        .build()
                )
                authList.add(
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(authUrl.host)
                        .path("/")
                        .build()
                )
            }
        }
        cookieStore[baseUrl.host] = list
        cookieStore[authUrl.host] = authList
    }

    fun saveUserSession(session: NoveliaUserSession) {
        prefs.edit()
            .putBoolean("is_logged_in", session.isLoggedIn)
            .putString("username", session.username)
            .putString("email", session.email)
            .putString("saved_cookie_header", session.cookies)
            .putString("token", session.token)
            .putBoolean("is_vip", session.isVip)
            .putBoolean("has_nsfw_access", session.hasNsfwAccess)
            .apply()
        loadCookiesFromPrefs()
    }

    fun getUserSession(): NoveliaUserSession {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val username = prefs.getString("username", "") ?: ""
        val email = prefs.getString("email", "") ?: ""
        val cookies = prefs.getString("saved_cookie_header", "") ?: ""
        val token = prefs.getString("token", "") ?: ""
        val isVip = prefs.getBoolean("is_vip", false)
        val hasNsfwAccess = prefs.getBoolean("has_nsfw_access", false)
        return NoveliaUserSession(
            isLoggedIn = isLoggedIn,
            username = username,
            email = email,
            cookies = cookies,
            token = token,
            isVip = isVip,
            hasNsfwAccess = hasNsfwAccess
        )
    }

    fun logout() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    /**
     * Exchange session cookies for a live JWT Token via auth.novelia.cc SSO endpoint
     */
    suspend fun refreshAuthToken(): Result<NoveliaUserSession> = withContext(Dispatchers.IO) {
        try {
            val refreshUrl = "$AUTH_URL/api/v1/auth/refresh?app=n"
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(refreshUrl)
                .post(emptyBody)
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("刷新登录态失败 (HTTP ${response.code})"))
            }

            val token = response.body?.string()?.trim()?.replace("\"", "") ?: ""
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("返回 Token 为空"))
            }

            var username = "Novelia 用户"
            var hasNsfw = true
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), StandardCharsets.UTF_8)
                    val payload = JSONObject(payloadJson)
                    username = payload.optString("sub", username)
                    hasNsfw = payload.optBoolean("hasNsfwAccess", true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val session = NoveliaUserSession(
                isLoggedIn = true,
                username = username,
                token = token,
                hasNsfwAccess = hasNsfw
            )
            saveUserSession(session)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search / list Wenku novels (文库小说) using 0-indexed page
     */
    suspend fun searchWenku(filter: NoveliaSearchFilter): Result<List<NoveliaWenkuNovel>> = withContext(Dispatchers.IO) {
        try {
            val zeroIndexedPage = (filter.page - 1).coerceAtLeast(0)
            val encodedKeyword = URLEncoder.encode(filter.keyword.trim(), "UTF-8").replace("+", "%20")
            val urlBuilder = StringBuilder("$BASE_URL/api/wenku?page=$zeroIndexedPage&pageSize=24&level=${filter.wenkuLevel}")
            if (filter.keyword.isNotBlank()) {
                urlBuilder.append("&query=").append(encodedKeyword)
            }

            val request = Request.Builder().url(urlBuilder.toString()).get().build()
            val response = client.newCall(request).execute()
            
            if (response.code in listOf(401, 500)) {
                val refreshRes = refreshAuthToken()
                if (refreshRes.isSuccess) {
                    val retryReq = Request.Builder().url(urlBuilder.toString()).get().build()
                    val retryResp = client.newCall(retryReq).execute()
                    if (retryResp.isSuccessful) {
                        val jsonStr = retryResp.body?.string() ?: ""
                        return@withContext Result.success(parseWenkuListJson(jsonStr))
                    }
                }
                return@withContext Result.failure(Exception("此分级需要登录 Novelia 账号后访问"))
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novels = parseWenkuListJson(jsonStr)
            Result.success(novels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get detailed metadata and volume list for a Wenku novel
     */
    suspend fun getWenkuDetail(novelId: String): Result<NoveliaWenkuNovel> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/wenku/$novelId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novel = parseWenkuDetailJson(novelId, jsonStr)
            Result.success(novel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search / list Web novels (网络小说) using 0-indexed page
     */
    suspend fun searchWebNovels(filter: NoveliaSearchFilter): Result<List<NoveliaWebNovel>> = withContext(Dispatchers.IO) {
        try {
            val zeroIndexedPage = (filter.page - 1).coerceAtLeast(0)
            val encodedKeyword = URLEncoder.encode(filter.keyword.trim(), "UTF-8").replace("+", "%20")
            val provider = if (filter.webProvider == "all" || filter.webProvider.isBlank()) "kakuyomu,syosetu,novelup,hameln,pixiv,alphapolis" else filter.webProvider
            val urlBuilder = StringBuilder("$BASE_URL/api/novel?page=$zeroIndexedPage&pageSize=20&provider=$provider&type=${filter.webType}&level=${filter.webLevel}&translate=${filter.webTranslate}&sort=${filter.webSort}")
            if (filter.keyword.isNotBlank()) {
                urlBuilder.append("&query=").append(encodedKeyword)
            }

            val request = Request.Builder().url(urlBuilder.toString()).get().build()
            val response = client.newCall(request).execute()
            
            if (response.code in listOf(401, 500)) {
                val refreshRes = refreshAuthToken()
                if (refreshRes.isSuccess) {
                    val retryReq = Request.Builder().url(urlBuilder.toString()).get().build()
                    val retryResp = client.newCall(retryReq).execute()
                    if (retryResp.isSuccessful) {
                        val jsonStr = retryResp.body?.string() ?: ""
                        return@withContext Result.success(parseWebNovelListJson(jsonStr))
                    }
                }
                return@withContext Result.failure(Exception("需要登录 Novelia 账号后浏览网络小说"))
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novels = parseWebNovelListJson(jsonStr)
            Result.success(novels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get detailed metadata and chapter list for a Web novel
     */
    suspend fun getWebNovelDetail(novelId: String): Result<NoveliaWebNovel> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/novel/$novelId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novel = parseWebNovelDetailJson(novelId, jsonStr)
            Result.success(novel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all translated chapters for a Web novel
     */
    suspend fun getWebNovelChapters(
        novelId: String,
        engine: TranslationEngine = TranslationEngine.SAKURA,
        onProgress: (Float) -> Unit = {}
    ): Result<List<NoveliaChapter>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/novel/$novelId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val jsonStr = response.body?.string() ?: ""
            val chapters = parseChaptersJson(jsonStr)
            Result.success(chapters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ParsedVolumeMeta(
        val volumeNumber: Int?,
        val specialNumber: Int?,
        val subtitle: String,
        val cleanName: String,
        val isSpecial: Boolean
    )

    fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch == '\u3000' || ch == '\u00A0' -> sb.append(' ')
                ch in '\uFF10'..'\uFF19' -> sb.append((ch.code - 0xFEE0).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Extracts the core title from subtitles e.g. "やり直し公女の魔導革命～処刑された...～" -> "やり直し公女の魔導革命"
     */
    fun extractCoreSeriesTitle(title: String): String {
        if (title.isBlank()) return ""
        val t = normalizeText(title)
        val m = Regex("^(.*?)\\s*[～~:：\\-]\\s*(.*?)(?:\\s*[～~])?\\s*$").find(t)
        if (m != null && m.groupValues[1].trim().length >= 2) {
            return m.groupValues[1].trim()
        }
        return t
    }

    /**
     * Intelligently parses volume number and subtitle from raw volume names
     */
    fun parseVolumeMeta(
        rawVolId: String,
        seriesTitleZh: String,
        seriesTitleJp: String,
        publisher: String = "",
        imprint: String = "",
        zhSubtitlesMap: Map<Int, String> = emptyMap()
    ): ParsedVolumeMeta {
        var clean = rawVolId.replace(Regex("\\.epub$", RegexOption.IGNORE_CASE), "")
        clean = normalizeText(clean)

        // 1. Strip author brackets e.g. [二八乃端月], [天城ケイ]
        clean = clean.replace(Regex("^\\[[^\\]]+\\]\\s*\\+*"), "")
        clean = clean.replace(Regex("\\+*\\[[^\\]]+\\]$"), "")

        // 2. Strip author suffixes e.g. "- 二八乃端月", "- 賀東 招二"
        clean = clean.replace(Regex("\\s*-\\s*[^-_()（）\\[\\]【】]{2,25}(\\s*&\\s*[^-_()（）\\[\\]【】]{2,25})*$"), "")

        // 3. Strip publisher / imprint / label brackets
        if (publisher.isNotBlank()) {
            clean = clean.replace("($publisher)", "").replace("（$publisher）", "")
        }
        if (imprint.isNotBlank()) {
            clean = clean.replace("($imprint)", "").replace("（$imprint）", "")
        }
        clean = clean.replace(Regex("[\\(（][^\\)）]*(?:文庫|ノベル|ファンタジア|電撃|オーバーラップ|KADOKAWA|角川|MF|GA|小学館|集英社|限定|特典|サーガ|フォレスト|一二三|新文芸|ドラゴン|スニーカー|ビーズログ|ダッシュエックス|HJ|アース・スター|ブレイブ|ツギクル|TO|Mノベルス)[^\\)）]*[\\)）]"), "")
        clean = clean.replace(Regex("【[^】]*】"), "")
        clean = clean.replace("+", " ").trim()
        clean = clean.replace(Regex("\\s+"), " ")

        // 4. Strip series prefix (prefer core prefix so volume subtitles with common prefix aren't stripped)
        val coreJp = extractCoreSeriesTitle(seriesTitleJp)
        val coreZh = extractCoreSeriesTitle(seriesTitleZh)
        val fullJp = normalizeText(seriesTitleJp)
        val fullZh = normalizeText(seriesTitleZh)

        var rest = clean
        for (p in listOf(coreJp, coreZh, fullJp, fullZh)) {
            if (p.isNotBlank() && rest.startsWith(p, ignoreCase = true)) {
                rest = rest.substring(p.length).trim()
                break
            }
        }
        rest = rest.trim(' ', ':', '：', '-', '_', '~', '～')

        val isSpecial = Regex("\\.txt$|第\\d+話|第\\d+话|Secret|SS|短篇|特典|外传|外傳|Bonus|Special|Side\\s*Story|Ex|ショートストーリー|限定書き下ろし|購入特典", RegexOption.IGNORE_CASE).containsMatchIn(rawVolId)
        var volNum: Int? = null
        var specialNum: Int? = null
        var sub: String

        if (isSpecial) {
            sub = rest.replace(Regex("([a-zA-Z\u4e00-\u9fa5\u3040-\u30ff])(\\d+)"), "$1 $2").trim()
            val sNumMatch = Regex("(\\d+)").find(sub)
            specialNum = sNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        } else {
            // Try leading number (e.g. "０ 王立学校編", "2 最強剣姫と新たな伝説をつくります", "16 世界欺きの偽神")
            val startMatch = Regex("^(?:第)?\\s*(\\d{1,3})\\s*(?:[卷巻话話]|\\b)?\\s*[:：\\-_\\s]*(.*)$").find(rest)
            if (startMatch != null) {
                volNum = startMatch.groupValues[1].toIntOrNull()
                rest = startMatch.groupValues[2].trim()
            } else {
                // Try trailing number
                val endMatch = Regex("(?:第)?\\s*(\\d{1,3})\\s*(?:[卷巻话話]|\\b)?\\s*$").find(rest)
                if (endMatch != null) {
                    volNum = endMatch.groupValues[1].toIntOrNull()
                    rest = rest.substring(0, endMatch.range.first).trim()
                } else {
                    // Try mid number
                    val midMatch = Regex("(?:第|\\b)\\s*(\\d{1,3})\\s*(?:[卷巻话話]|\\b)").find(rest)
                    if (midMatch != null) {
                        volNum = midMatch.groupValues[1].toIntOrNull()
                        rest = (rest.substring(0, midMatch.range.first) + " " + rest.substring(midMatch.range.last + 1)).replace(Regex("\\s+"), " ").trim()
                    }
                }
            }

            sub = rest.trim(' ', ':', '：', '-', '_', '~', '～')
            sub = sub.replace(Regex("^[~～]\\s*"), "").replace(Regex("\\s*[~～]$"), "").trim()

            // If it's a regular volume without a number in its title, it is Volume 1!
            if (volNum == null) {
                volNum = 1
            }

            if (zhSubtitlesMap.containsKey(volNum)) {
                val zhSub = zhSubtitlesMap[volNum]
                if (!zhSub.isNullOrBlank()) {
                    sub = zhSub
                }
            }
        }

        return ParsedVolumeMeta(
            volumeNumber = volNum,
            specialNumber = specialNum,
            subtitle = sub,
            cleanName = clean,
            isSpecial = isSpecial
        )
    }

    /**
     * Build precise Wenku volume download URL with complete translation fallback priority
     */
    fun buildWenkuDownloadUrl(novelId: String, volumeId: String, engine: TranslationEngine): String {
        val encodedVolId = URLEncoder.encode(volumeId, "UTF-8").replace("+", "%20")
        val priorityList = when (engine) {
            TranslationEngine.SAKURA -> listOf("sakura", "gpt", "youdao", "baidu")
            TranslationEngine.GPT -> listOf("gpt", "sakura", "youdao", "baidu")
            TranslationEngine.YOUDAO -> listOf("youdao", "sakura", "gpt", "baidu")
            TranslationEngine.ORIGINAL -> emptyList()
        }

        val mode = if (engine == TranslationEngine.ORIGINAL) "raw" else "zh"
        val tag = if (priorityList.isNotEmpty()) "Y" + priorityList.map { it.first() }.joinToString("") else ""
        val filenameStr = if (engine == TranslationEngine.ORIGINAL) "raw..$volumeId" else "zh.$tag.$volumeId"
        val safeFilename = URLEncoder.encode(filenameStr, "UTF-8").replace("+", "%20")

        val queryBuilder = StringBuilder("$BASE_URL/api/wenku/$novelId/file/$encodedVolId?mode=$mode&translationsMode=priority")
        for (trans in priorityList) {
            queryBuilder.append("&translations=").append(trans)
        }
        queryBuilder.append("&filename=").append(safeFilename)
        return queryBuilder.toString()
    }

    /**
     * Directly stream-download a Wenku EPUB file with explicit redirect handling
     */
    suspend fun downloadWenkuEpub(
        downloadUrl: String,
        destFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var currentUrl = if (downloadUrl.startsWith("http")) downloadUrl else "$BASE_URL$downloadUrl"
            var redirectCount = 0

            while (redirectCount < 5) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$BASE_URL/")
                    .header("Origin", BASE_URL)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isRedirect || response.code in 301..308) {
                    val location = response.header("Location")
                    response.close()
                    if (location.isNullOrBlank()) {
                        Log.e("Novelia", "Redirect location header missing")
                        return@withContext false
                    }
                    currentUrl = if (location.startsWith("http")) location else "$BASE_URL$location"
                    redirectCount++
                    continue
                }

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("Novelia", "Download failed: HTTP ${response.code} ${response.message}: $errorBody for $currentUrl")
                    response.close()
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val totalLength = body.contentLength()

                destFile.parentFile?.mkdirs()
                if (destFile.exists()) destFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalLength > 0) {
                                onProgress(downloaded.toFloat() / totalLength.toFloat())
                            }
                        }
                    }
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e("Novelia", "downloadWenkuEpub exception", e)
            false
        }
    }

    /**
     * Download cover image bytes
     */
    suspend fun fetchCoverImage(coverUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (coverUrl.isBlank()) return@withContext null
        try {
            val fullUrl = if (coverUrl.startsWith("http")) coverUrl else "$BASE_URL$coverUrl"
            val request = Request.Builder().url(fullUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- FAVORITES APIs ---

    /**
     * Get user favored folders
     */
    suspend fun getFavoredFolders(): Result<List<NoveliaFolder>> = withContext(Dispatchers.IO) {
        try {
            var token = prefs.getString("token", null)
            if (token.isNullOrBlank()) {
                val refRes = refreshAuthToken()
                token = refRes.getOrNull()?.token
            }

            val url = "$BASE_URL/api/user/favored"
            var request = Request.Builder().url(url).get().build()
            var response = client.newCall(request).execute()

            if (response.code in listOf(401, 500)) {
                response.close()
                val refRes = refreshAuthToken()
                if (refRes.isSuccess) {
                    request = Request.Builder().url(url).get().build()
                    response = client.newCall(request).execute()
                }
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("获取收藏夹失败 (HTTP ${response.code})"))
            }

            val jsonStr = response.body?.string() ?: ""
            val list = mutableListOf<NoveliaFolder>()
            list.add(NoveliaFolder("default", "默认收藏夹", "wenku"))

            if (jsonStr.trim().startsWith("{")) {
                val root = JSONObject(jsonStr)
                val wenkuArr = root.optJSONArray("favoredWenku")
                if (wenkuArr != null) {
                    for (i in 0 until wenkuArr.length()) {
                        val obj = wenkuArr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").ifEmpty { obj.optString("_id") }
                        val name = obj.optString("title").ifEmpty { obj.optString("name") }
                        if (id.isNotBlank() && id != "default") {
                            list.add(NoveliaFolder(id, name, "wenku"))
                        }
                    }
                }
                val webArr = root.optJSONArray("favoredWeb")
                if (webArr != null) {
                    for (i in 0 until webArr.length()) {
                        val obj = webArr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").ifEmpty { obj.optString("_id") }
                        val name = obj.optString("title").ifEmpty { obj.optString("name") }
                        if (id.isNotBlank() && id != "default") {
                            list.add(NoveliaFolder(id, name, "web"))
                        }
                    }
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user favored Wenku novels
     */
    suspend fun getFavoredWenku(folderId: String = "default", page: Int = 0, sort: Int = 0): Result<List<NoveliaWenkuNovel>> = withContext(Dispatchers.IO) {
        try {
            var token = prefs.getString("token", null)
            if (token.isNullOrBlank()) {
                val refRes = refreshAuthToken()
                token = refRes.getOrNull()?.token
            }

            val zeroIndexedPage = page.coerceAtLeast(0)
            val sortStr = if (sort == 1) "create" else "update"
            val folderParam = if (folderId.isBlank()) "default" else folderId
            val url = "$BASE_URL/api/user/favored-wenku/$folderParam?page=$zeroIndexedPage&pageSize=24&sort=$sortStr"

            var request = Request.Builder().url(url).get().build()
            var response = client.newCall(request).execute()

            if (response.code in listOf(401, 500)) {
                response.close()
                val refRes = refreshAuthToken()
                if (refRes.isSuccess) {
                    request = Request.Builder().url(url).get().build()
                    response = client.newCall(request).execute()
                }
            }

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("获取文库收藏失败 (HTTP ${response.code}): $errBody"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novels = parseWenkuListJson(jsonStr).map { it.copy(isFavorited = true) }
            Result.success(novels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user favored Web novels
     */
    suspend fun getFavoredWeb(folderId: String = "default", page: Int = 0, sort: Int = 0): Result<List<NoveliaWebNovel>> = withContext(Dispatchers.IO) {
        try {
            var token = prefs.getString("token", null)
            if (token.isNullOrBlank()) {
                val refRes = refreshAuthToken()
                token = refRes.getOrNull()?.token
            }

            val zeroIndexedPage = page.coerceAtLeast(0)
            val sortStr = if (sort == 1) "create" else "update"
            val folderParam = if (folderId.isBlank()) "default" else folderId
            val provider = "kakuyomu,syosetu,novelup,hameln,pixiv,alphapolis"
            val url = "$BASE_URL/api/user/favored-web/$folderParam?page=$zeroIndexedPage&pageSize=20&provider=$provider&type=0&level=0&translate=0&sort=$sortStr"

            var request = Request.Builder().url(url).get().build()
            var response = client.newCall(request).execute()

            if (response.code in listOf(401, 500)) {
                response.close()
                val refRes = refreshAuthToken()
                if (refRes.isSuccess) {
                    request = Request.Builder().url(url).get().build()
                    response = client.newCall(request).execute()
                }
            }

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("获取网络小说收藏失败 (HTTP ${response.code}): $errBody"))
            }

            val jsonStr = response.body?.string() ?: ""
            val novels = parseWebNovelListJson(jsonStr).map { it.copy(isFavorited = true) }
            Result.success(novels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Favorite Wenku novel
     */
    suspend fun favoriteWenku(novelId: String, folderId: String = "default"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/user/favored-wenku/$folderId/$novelId"
            val request = Request.Builder().url(url).put("{}".toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unfavorite Wenku novel
     */
    suspend fun unfavoriteWenku(novelId: String, folderId: String = "default"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/user/favored-wenku/$folderId/$novelId"
            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Favorite Web novel
     */
    suspend fun favoriteWebNovel(provider: String, novelId: String, folderId: String = "default"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/user/favored-web/$folderId/$provider/$novelId"
            val request = Request.Builder().url(url).put("{}".toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unfavorite Web novel
     */
    suspend fun unfavoriteWebNovel(provider: String, novelId: String, folderId: String = "default"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/user/favored-web/$folderId/$provider/$novelId"
            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // JSON Parsers
    private fun parseWenkuListJson(jsonStr: String): List<NoveliaWenkuNovel> {
        val list = mutableListOf<NoveliaWenkuNovel>()
        try {
            val trimmed = jsonStr.trim()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                when {
                    root.has("items") -> root.optJSONArray("items")
                    root.has("data") -> {
                        val data = root.opt("data")
                        if (data is JSONArray) data else (data as? JSONObject)?.optJSONArray("items")
                    }
                    root.has("novels") -> root.optJSONArray("novels")
                    else -> null
                } ?: JSONArray()
            } else {
                JSONArray()
            }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifEmpty { obj.optString("_id") }
                val titleJp = obj.optString("title")
                val titleZh = obj.optString("titleZh")
                val displayTitle = titleZh.ifEmpty { titleJp.ifEmpty { "未知书名" } }
                val cover = obj.optString("cover").ifEmpty { obj.optString("coverUrl") }
                val desc = obj.optString("introduction").ifEmpty { obj.optString("description") }
                val rating = obj.optString("level", "全年龄")

                val authorsList = mutableListOf<String>()
                val authorsArr = obj.optJSONArray("authors")
                if (authorsArr != null) {
                    for (a in 0 until authorsArr.length()) {
                        authorsList.add(authorsArr.optString(a))
                    }
                }
                val author = if (authorsList.isNotEmpty()) authorsList.joinToString(", ") else obj.optString("author")

                list.add(
                    NoveliaWenkuNovel(
                        id = id,
                        title = displayTitle,
                        japaneseTitle = titleJp,
                        author = author,
                        coverUrl = if (cover.startsWith("http")) cover else if (cover.isNotEmpty()) "$BASE_URL$cover" else "",
                        description = desc,
                        tags = emptyList(),
                        ratingCategory = rating,
                        updateTime = ""
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseWenkuDetailJson(novelId: String, jsonStr: String): NoveliaWenkuNovel {
        try {
            val root = JSONObject(jsonStr)
            val titleJp = root.optString("title")
            val titleZh = root.optString("titleZh")
            val displayTitle = titleZh.ifEmpty { titleJp.ifEmpty { "未知书名" } }
            val cover = root.optString("cover").ifEmpty { root.optString("coverUrl") }
            val desc = root.optString("introduction").ifEmpty { root.optString("description") }
            val rating = root.optString("level", "全年龄")
            val publisher = root.optString("publisher")
            val imprint = root.optString("imprint")

            val authorsList = mutableListOf<String>()
            val authorsArr = root.optJSONArray("authors")
            if (authorsArr != null) {
                for (a in 0 until authorsArr.length()) {
                    authorsList.add(authorsArr.optString(a))
                }
            }
            val author = if (authorsList.isNotEmpty()) authorsList.joinToString(", ") else root.optString("author")

            val artistsList = mutableListOf<String>()
            val artistsArr = root.optJSONArray("artists")
            if (artistsArr != null) {
                for (a in 0 until artistsArr.length()) {
                    artistsList.add(artistsArr.optString(a))
                }
            }
            val artists = artistsList.joinToString(", ")

            val tagsList = mutableListOf<String>()
            val keywordsArr = root.optJSONArray("keywords")
            if (keywordsArr != null) {
                for (k in 0 until keywordsArr.length()) {
                    tagsList.add(keywordsArr.optString(k))
                }
            }

            val zhSubtitlesMap = mutableMapOf<Int, String>()
            val volZhArray = root.optJSONArray("volumeZh")
            if (volZhArray != null) {
                for (i in 0 until volZhArray.length()) {
                    val zhStr = volZhArray.optString(i)
                    if (zhStr.isNotBlank()) {
                        var cleanZh = normalizeText(zhStr.replace(Regex("\\.epub$", RegexOption.IGNORE_CASE), ""))
                        cleanZh = cleanZh.replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
                        cleanZh = cleanZh.replace(Regex("^[^-]{2,20}-\\s*"), "")
                        for (p in listOf(displayTitle, titleJp)) {
                            if (p.isNotBlank() && cleanZh.startsWith(p, ignoreCase = true)) {
                                cleanZh = cleanZh.substring(p.length).trim()
                            }
                        }
                        cleanZh = cleanZh.trim(' ', '.', ':', '：', '-', '_')
                        val numMatch = Regex("^(?:第)?\\s*(\\d{1,3})\\s*(?:[卷巻话話]|\\b)?\\s*[:：\\-_\\s]*(.*)").find(cleanZh)
                        if (numMatch != null) {
                            val vNum = numMatch.groupValues[1].toIntOrNull()
                            val subText = numMatch.groupValues[2].trim(' ', '.', ':', '：', '-', '_')
                            if (vNum != null && subText.isNotBlank() && !zhSubtitlesMap.containsKey(vNum)) {
                                zhSubtitlesMap[vNum] = subText
                            }
                        }
                    }
                }
            }

            data class RawWenkuVol(
                val volId: String,
                val origIndex: Int,
                val meta: ParsedVolumeMeta,
                val totalChapters: Int,
                val youdao: Int,
                val gpt: Int,
                val sakura: Int
            )

            val rawList = mutableListOf<RawWenkuVol>()
            val volsArray = root.optJSONArray("volumeJp") ?: root.optJSONArray("volumeZh") ?: root.optJSONArray("volumes") ?: JSONArray()
            for (i in 0 until volsArray.length()) {
                val vObj = volsArray.optJSONObject(i)
                val volId = vObj?.optString("volumeId")?.ifEmpty { vObj.optString("id", "$i") } ?: volsArray.optString(i, "$i")
                if (volId.isBlank()) continue

                val meta = parseVolumeMeta(volId, displayTitle, titleJp, publisher, imprint, zhSubtitlesMap)
                val totalChapters = vObj?.optInt("total", 0) ?: 0
                val youdao = vObj?.optInt("youdao", 0) ?: 0
                val gpt = vObj?.optInt("gpt", 0) ?: 0
                val sakura = vObj?.optInt("sakura", 0) ?: 0

                rawList.add(RawWenkuVol(volId, i, meta, totalChapters, youdao, gpt, sakura))
            }

            // Natural Sort: 1..N regular numbered volumes, then specials in order
            rawList.sortWith(
                compareBy<RawWenkuVol> { if (it.meta.isSpecial) 1 else 0 }
                    .thenBy { if (it.meta.isSpecial) (it.meta.specialNumber ?: 1) else (it.meta.volumeNumber ?: (1000 + it.origIndex)) }
                    .thenBy { it.origIndex }
            )

            val volumes = rawList.mapIndexed { idx, raw ->
                val actualVolNum = if (raw.meta.isSpecial) (raw.meta.specialNumber ?: (idx + 1)) else (raw.meta.volumeNumber ?: (idx + 1))
                val volTag = if (raw.meta.isSpecial) "特别篇" else "第${actualVolNum}卷"
                val subPart = if (raw.meta.subtitle.isNotBlank()) " ${raw.meta.subtitle}" else ""
                val cleanFormattedName = "$volTag$subPart".trim()

                val engineMap = mutableMapOf<TranslationEngine, String>()
                engineMap[TranslationEngine.SAKURA] = buildWenkuDownloadUrl(novelId, raw.volId, TranslationEngine.SAKURA)
                engineMap[TranslationEngine.GPT] = buildWenkuDownloadUrl(novelId, raw.volId, TranslationEngine.GPT)
                engineMap[TranslationEngine.YOUDAO] = buildWenkuDownloadUrl(novelId, raw.volId, TranslationEngine.YOUDAO)

                NoveliaVolume(
                    id = raw.volId,
                    volumeIndex = actualVolNum,
                    volumeName = cleanFormattedName,
                    totalChapters = raw.totalChapters,
                    youdaoChapters = raw.youdao,
                    gptChapters = raw.gpt,
                    sakuraChapters = raw.sakura,
                    defaultDownloadUrl = engineMap[TranslationEngine.SAKURA] ?: "",
                    engineDownloadUrls = engineMap
                )
            }

            return NoveliaWenkuNovel(
                id = novelId,
                title = displayTitle,
                japaneseTitle = titleJp,
                author = author,
                artists = artists,
                publisher = publisher,
                imprint = imprint,
                coverUrl = if (cover.startsWith("http")) cover else if (cover.isNotEmpty()) "$BASE_URL$cover" else "",
                description = desc,
                tags = tagsList,
                ratingCategory = rating,
                volumes = volumes,
                updateTime = ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return NoveliaWenkuNovel(id = novelId, title = "解析详情失败")
        }
    }

    private fun parseWebNovelListJson(jsonStr: String): List<NoveliaWebNovel> {
        val list = mutableListOf<NoveliaWebNovel>()
        try {
            val trimmed = jsonStr.trim()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                when {
                    root.has("items") -> root.optJSONArray("items")
                    root.has("data") -> {
                        val data = root.opt("data")
                        if (data is JSONArray) data else (data as? JSONObject)?.optJSONArray("items")
                    }
                    root.has("novels") -> root.optJSONArray("novels")
                    else -> null
                } ?: JSONArray()
            } else {
                JSONArray()
            }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifEmpty { obj.optString("_id") }
                val titleJp = obj.optString("title")
                val titleZh = obj.optString("titleZh")
                val displayTitle = titleZh.ifEmpty { titleJp.ifEmpty { "未知书名" } }
                val author = obj.optString("author")
                val cover = obj.optString("cover").ifEmpty { obj.optString("coverUrl") }
                val desc = obj.optString("introduction").ifEmpty { obj.optString("description") }
                val provider = obj.optString("provider", "Kakuyomu")
                val status = obj.optString("type", "连载中")
                val rating = obj.optString("level", "一般向")
                val totalCh = obj.optInt("total", 0)
                val youdao = obj.optInt("youdao", 0)
                val gpt = obj.optInt("gpt", 0)
                val sakura = obj.optInt("sakura", 0)

                list.add(
                    NoveliaWebNovel(
                        id = id,
                        sourcePlatform = provider,
                        sourceNovelId = id,
                        title = displayTitle,
                        japaneseTitle = titleJp,
                        author = author,
                        coverUrl = if (cover.startsWith("http")) cover else if (cover.isNotEmpty()) "$BASE_URL$cover" else "",
                        description = desc,
                        status = status,
                        ratingCategory = rating,
                        tags = emptyList(),
                        totalChapters = totalCh,
                        youdaoChapters = youdao,
                        gptChapters = gpt,
                        sakuraChapters = sakura,
                        lastUpdated = ""
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseWebNovelDetailJson(novelId: String, jsonStr: String): NoveliaWebNovel {
        try {
            val root = JSONObject(jsonStr)
            val titleJp = root.optString("title")
            val titleZh = root.optString("titleZh")
            val displayTitle = titleZh.ifEmpty { titleJp.ifEmpty { "未知书名" } }
            val author = root.optString("author")
            val cover = root.optString("cover").ifEmpty { root.optString("coverUrl") }
            val desc = root.optString("introduction").ifEmpty { root.optString("description") }
            val provider = root.optString("provider", "Kakuyomu")
            val status = root.optString("type", "连载中")
            val rating = root.optString("level", "一般向")
            val totalCh = root.optInt("total", 0)
            val youdao = root.optInt("youdao", 0)
            val gpt = root.optInt("gpt", 0)
            val sakura = root.optInt("sakura", 0)

            return NoveliaWebNovel(
                id = novelId,
                sourcePlatform = provider,
                sourceNovelId = novelId,
                title = displayTitle,
                japaneseTitle = titleJp,
                author = author,
                coverUrl = if (cover.startsWith("http")) cover else if (cover.isNotEmpty()) "$BASE_URL$cover" else "",
                description = desc,
                status = status,
                ratingCategory = rating,
                tags = emptyList(),
                totalChapters = totalCh,
                youdaoChapters = youdao,
                gptChapters = gpt,
                sakuraChapters = sakura,
                lastUpdated = ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return NoveliaWebNovel(id = novelId, title = "解析详情失败")
        }
    }

    private fun parseChaptersJson(jsonStr: String): List<NoveliaChapter> {
        val list = mutableListOf<NoveliaChapter>()
        try {
            val root = JSONObject(jsonStr)
            val array = when {
                root.has("chapters") -> root.optJSONArray("chapters")
                root.has("toc") -> root.optJSONArray("toc")
                root.has("items") -> root.optJSONArray("items")
                else -> null
            } ?: JSONArray()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifEmpty { obj.optString("chapterId", "$i") }
                val title = obj.optString("titleZh")
                    .ifEmpty { obj.optString("titleSakura") }
                    .ifEmpty { obj.optString("titleGpt") }
                    .ifEmpty { obj.optString("titleYoudao") }
                    .ifEmpty { obj.optString("titleBaidu") }
                    .ifEmpty { obj.optString("title", "第${i + 1}章") }
                val jpTitle = obj.optString("title")
                val content = obj.optString("contentZh")
                    .ifEmpty { obj.optString("contentSakura") }
                    .ifEmpty { obj.optString("contentGpt") }
                    .ifEmpty { obj.optString("contentYoudao") }
                    .ifEmpty { obj.optString("contentBaidu") }
                    .ifEmpty { obj.optString("content", "") }

                list.add(
                    NoveliaChapter(
                        id = id,
                        chapterIndex = i + 1,
                        volumeName = "正文",
                        title = title,
                        japaneseTitle = jpTitle,
                        content = content
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
