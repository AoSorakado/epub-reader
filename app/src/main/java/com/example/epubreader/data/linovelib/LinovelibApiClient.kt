package com.example.epubreader.data.linovelib

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LinovelibApiClient(private val context: Context) {

    companion object {
        const val BASE_URL = "https://tw.linovelib.com"
        private const val PREFS_NAME = "linovelib_prefs"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_COOKIES = "saved_cookies"
        private const val KEY_USERNAME = "linovelib_username"

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val chapterLogResolver = com.example.epubreader.data.linovelib.restore.BiliChapterLogResolver()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val orig = chain.request()
            val cookieManager = CookieManager.getInstance()
            val dynamicCookies = cookieManager.getCookie(BASE_URL) ?: prefs.getString(KEY_COOKIES, "") ?: ""
            val ua = prefs.getString(KEY_USER_AGENT, null) ?: DEFAULT_USER_AGENT

            val reqBuilder = orig.newBuilder()
                .header("User-Agent", ua)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,zh-TW;q=0.8,en;q=0.7")

            if (dynamicCookies.isNotBlank()) {
                reqBuilder.header("Cookie", dynamicCookies)
            }

            val resp = chain.proceed(reqBuilder.build())
            val setCookies = resp.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                for (sc in setCookies) {
                    cookieManager.setCookie(BASE_URL, sc)
                }
            }
            resp
        }
        .build()

    fun saveUserAgent(ua: String) {
        if (ua.isNotBlank()) {
            prefs.edit().putString(KEY_USER_AGENT, ua).apply()
        }
    }

    fun saveUsername(name: String) {
        if (name.isNotBlank()) {
            prefs.edit().putString(KEY_USERNAME, name.trim()).apply()
        }
    }

    fun getSavedUsername(): String {
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun syncCookiesFromCookieManager(): String {
        val cookies = CookieManager.getInstance().getCookie(BASE_URL) ?: ""
        if (cookies.isNotBlank()) {
            prefs.edit().putString(KEY_COOKIES, cookies).apply()
        }
        return cookies
    }

    /**
     * Automatically extract username from cookies or fetch userdetail.php
     */
    suspend fun fetchUsernameFromSession(): String = withContext(Dispatchers.IO) {
        val cookies = CookieManager.getInstance().getCookie(BASE_URL) ?: prefs.getString(KEY_COOKIES, "") ?: ""
        
        // 1. Direct cookie inspection
        var name = extractUsernameFromCookieString(cookies)
        if (name.isNotBlank()) {
            saveUsername(name)
            return@withContext name
        }

        // 2. Fetch userdetail.php with session cookies
        if (cookies.contains("jieqiUserId") || cookies.contains("PHPSESSID") || cookies.contains("jieqiUserInfo")) {
            try {
                val req = Request.Builder().url("$BASE_URL/userdetail.php").get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html, BASE_URL)
                    val userEl = doc.select(".user-name, #username, .user_name, .profile-name, .nav-user, a[href*='userdetail'], a[href*='logout']").firstOrNull()
                    val parsed = userEl?.text()?.replace("退出", "")?.replace("登出", "")?.trim() ?: ""
                    if (parsed.isNotBlank() && parsed != "登入" && parsed != "登录") {
                        saveUsername(parsed)
                        return@withContext parsed
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Return saved username or default
        val saved = getSavedUsername()
        if (saved.isNotBlank()) return@withContext saved
        if (cookies.contains("jieqiUserId")) {
            val fallback = "哔哩轻小说用户"
            saveUsername(fallback)
            return@withContext fallback
        }

        ""
    }

    private fun decodeCookieValue(raw: String): String {
        return try {
            URLDecoder.decode(raw, "UTF-8").trim()
        } catch (_: Exception) {
            try {
                URLDecoder.decode(raw, "GBK").trim()
            } catch (_: Exception) {
                raw.trim()
            }
        }
    }

    private fun extractUsernameFromCookieString(cookies: String): String {
        if (cookies.isBlank()) return ""

        val m1 = Pattern.compile("jieqiUserName=([^;]+)").matcher(cookies)
        if (m1.find()) {
            val decoded = decodeCookieValue(m1.group(1) ?: "")
            if (decoded.isNotBlank()) return decoded
        }

        val m2 = Pattern.compile("jieqiUserUname=([^;]+)").matcher(cookies)
        if (m2.find()) {
            val decoded = decodeCookieValue(m2.group(1) ?: "")
            if (decoded.isNotBlank()) return decoded
        }

        val m3 = Pattern.compile("jieqiUserInfo=([^;]+)").matcher(cookies)
        if (m3.find()) {
            val decoded = decodeCookieValue(m3.group(1) ?: "")
            val subM = Pattern.compile("jieqiUserName[=:]\\s*([^,;]+)", Pattern.CASE_INSENSITIVE).matcher(decoded)
            if (subM.find()) {
                val subName = decodeCookieValue(subM.group(1) ?: "")
                if (subName.isNotBlank()) return subName
            }
        }

        val m4 = Pattern.compile("(?<=^|[;\\s])(?:user_name|username|uname|nickname)=([^;]+)", Pattern.CASE_INSENSITIVE).matcher(cookies)
        if (m4.find()) {
            val decoded = decodeCookieValue(m4.group(1) ?: "")
            if (decoded.isNotBlank()) return decoded
        }

        return ""
    }

    /**
     * Search novels or fetch categorized novels
     */
    suspend fun searchNovels(
        keyword: String = "",
        subCategory: Int = 0,
        page: Int = 1
    ): Result<List<LinovelibNovel>> = withContext(Dispatchers.IO) {
        try {
            val cleanKw = keyword.trim()

            // If user enters numeric ID or direct URL (e.g. 4586, /novel/4586.html, https://tw.linovelib.com/novel/4586/catalog)
            if (cleanKw.isNotBlank()) {
                val directId = when {
                    cleanKw.all { it.isDigit() } -> cleanKw
                    else -> {
                        val m = Pattern.compile("/novel/(\\d+)").matcher(cleanKw)
                        if (m.find()) m.group(1) else null
                    }
                }

                if (!directId.isNullOrBlank()) {
                    val detailRes = getNovelDetail(directId)
                    if (detailRes.isSuccess) {
                        return@withContext Result.success(listOf(detailRes.getOrThrow()))
                    }
                }
            }

            // If keyword is specified (and not a direct ID), search across multiple pages & top charts concurrently
            if (cleanKw.isNotBlank()) {
                val matchedList = mutableListOf<LinovelibNovel>()
                val searchUrls = listOf(
                    "$BASE_URL/wenku/",
                    "$BASE_URL/wenku/lastupdate_0_0_0_0_0_0_0_2_0.html",
                    "$BASE_URL/wenku/lastupdate_0_0_0_0_0_0_0_3_0.html",
                    "$BASE_URL/wenku/lastupdate_0_0_0_0_0_0_0_4_0.html",
                    "$BASE_URL/wenku/lastupdate_0_0_0_0_0_0_0_5_0.html",
                    "$BASE_URL/top/monthvisit/1.html",
                    "$BASE_URL/top/goodnum/1.html",
                    "$BASE_URL/top/monthvote/1.html",
                    "$BASE_URL/topfull/postdate/1.html"
                )

                coroutineScope {
                    val deferreds = searchUrls.map { u ->
                        async {
                            try {
                                val req = Request.Builder().url(u).get().build()
                                val resp = client.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    parseLinovelibHtml(resp.body?.string() ?: "")
                                } else emptyList<LinovelibNovel>()
                            } catch (e: Exception) {
                                emptyList<LinovelibNovel>()
                            }
                        }
                    }
                    val allNovels = deferreds.awaitAll().flatten()
                    val seenIds = mutableSetOf<String>()
                    for (n in allNovels) {
                        if (seenIds.add(n.id)) {
                            if (n.title.contains(cleanKw, ignoreCase = true) ||
                                n.author.contains(cleanKw, ignoreCase = true) ||
                                n.description.contains(cleanKw, ignoreCase = true)
                            ) {
                                matchedList.add(n)
                            }
                        }
                    }
                }
                return@withContext Result.success(matchedList)
            }

            // Normal paginated browsing by subCategory
            val url = when (subCategory) {
                0 -> if (page <= 1) "$BASE_URL/wenku/" else "$BASE_URL/wenku/lastupdate_0_0_0_0_0_0_0_${page}_0.html"
                1 -> "$BASE_URL/top/monthvisit/$page.html"
                2 -> "$BASE_URL/top/goodnum/$page.html"
                3 -> "$BASE_URL/top/monthvote/$page.html"
                4 -> "$BASE_URL/topfull/postdate/$page.html"
                else -> "$BASE_URL/"
            }

            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()

            if (resp.code in listOf(403, 503)) {
                return@withContext Result.failure(Exception("CLOUDFLARE_CHALLENGE"))
            }

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }

            val html = resp.body?.string() ?: ""
            val novels = parseLinovelibHtml(html)

            Result.success(novels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get novel details and catalog
     */
    suspend fun getNovelDetail(novelId: String): Result<LinovelibNovel> = withContext(Dispatchers.IO) {
        try {
            val cleanId = novelId.replace(Regex("[^0-9]"), "")
            val detailUrl = "$BASE_URL/novel/$cleanId.html"
            val req = Request.Builder().url(detailUrl).get().build()
            val resp = client.newCall(req).execute()

            if (resp.code in listOf(403, 503)) {
                return@withContext Result.failure(Exception("CLOUDFLARE_CHALLENGE"))
            }

            val detailHtml = resp.body?.string() ?: ""
            val doc = Jsoup.parse(detailHtml, BASE_URL)

            // OpenGraph tags
            val ogTitle = doc.select("meta[property=og:novel:book_name], meta[property=og:title]").attr("content").trim()
            val ogAuthor = doc.select("meta[property=og:novel:author]").attr("content").trim()
            val ogImage = doc.select("meta[property=og:image]").attr("content").trim()
            val ogDesc = doc.select("meta[property=og:description]").attr("content").trim()
            val ogStatus = doc.select("meta[property=og:novel:status]").attr("content").trim()
            val ogCategory = doc.select("meta[property=og:novel:category]").attr("content").trim()

            val title = if (ogTitle.isNotBlank()) ogTitle else {
                doc.select("h1.book-title, h1.novel-title, h1").firstOrNull()?.text()?.trim() ?: "未知标题"
            }

            val author = if (ogAuthor.isNotBlank()) ogAuthor else {
                doc.select(".book-author span, .author, span:contains(作者)").firstOrNull()?.text()
                    ?.replace("作者：", "")?.replace("作者:", "")?.trim() ?: "未知作者"
            }

            val prefix = cleanId.toIntOrNull()?.let { it / 1000 } ?: 0
            val coverUrl = when {
                ogImage.isNotBlank() && !ogImage.contains("book-cover-no") && !ogImage.endsWith(".svg") -> ogImage
                else -> "$BASE_URL/files/article/image/$prefix/$cleanId/${cleanId}s.jpg"
            }

            val desc = if (ogDesc.isNotBlank()) ogDesc else {
                doc.select(".book-dec, .book-intro, #bookSummary, .intro").text().trim()
            }

            val category = if (ogCategory.isNotBlank()) ogCategory else "轻小说"
            val status = if (ogStatus.isNotBlank()) ogStatus else (if (detailHtml.contains("完结")) "已完结" else "连载中")

            // Fetch catalog
            val catalogUrl = "$BASE_URL/novel/$cleanId/catalog"
            val catReq = Request.Builder().url(catalogUrl).get().build()
            val catResp = client.newCall(catReq).execute()
            val catHtml = if (catResp.isSuccessful) catResp.body?.string() ?: "" else detailHtml

            val volumes = parseCatalogHtml(cleanId, catHtml)

            val novel = LinovelibNovel(
                id = cleanId,
                title = title,
                author = author,
                coverUrl = coverUrl,
                category = category,
                status = status,
                description = desc,
                volumes = volumes
            )
            Result.success(novel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download and parse full chapter content across ALL paginated subpages (using ReadParams.url_next)
     */
    suspend fun getChapterContent(chapterUrl: String): Result<LinovelibChapterContent> = withContext(Dispatchers.IO) {
        try {
            val allParagraphs = mutableListOf<String>()
            val allImages = mutableListOf<String>()
            var curUrl = fixUrl(chapterUrl)
            var chapterTitle = ""
            var chapterId = ""

            val visitedUrls = mutableSetOf<String>()
            var pageIndex = 1

            while (curUrl.isNotBlank() && !visitedUrls.contains(curUrl) && pageIndex <= 50) {
                visitedUrls.add(curUrl)

                if (pageIndex > 1) {
                    kotlinx.coroutines.delay(250)
                }

                var html = ""
                var attempt = 0
                while (attempt < 3) {
                    attempt++
                    try {
                        val req = Request.Builder().url(curUrl).get().build()
                        val resp = client.newCall(req).execute()

                        if (resp.code in listOf(403, 503)) {
                            return@withContext Result.failure(Exception("CLOUDFLARE_CHALLENGE"))
                        }
                        if (resp.code == 429) {
                            kotlinx.coroutines.delay(1500L * attempt)
                            continue
                        }
                        if (resp.isSuccessful) {
                            html = resp.body?.string() ?: ""
                            break
                        } else {
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    } catch (e: Exception) {
                        if (attempt >= 3) throw e
                        kotlinx.coroutines.delay(1200L * attempt)
                    }
                }

                val doc = Jsoup.parse(html, BASE_URL)

                if (chapterTitle.isBlank()) {
                    chapterTitle = doc.select("#atitle, h1.read-title, h1.chapter-title, h1").text().trim()
                }
                if (chapterId.isBlank()) {
                    val m = Pattern.compile("/novel/\\d+/(\\d+)(?:_\\d+)?\\.html").matcher(curUrl)
                    if (m.find()) {
                        chapterId = m.group(1) ?: ""
                    }
                }

                val contentElem = doc.select("#acontent, .read-content, #chaptercontent, #read-content").firstOrNull()
                if (contentElem != null) {
                    val noteText = contentElem.select(".center-note").text()
                    if (noteText.contains("沒有可閱讀的章節") || noteText.contains("没有可阅读的章节") ||
                        noteText.contains("需要足夠的權限") || noteText.contains("需要足够的权限") ||
                        noteText.contains("審核未通過") || noteText.contains("审核未通过")) {
                        return@withContext Result.failure(Exception("该章节为限制级内容，需要登录账号方可获取。请先在右上角「网页/登录」中登录您的哔哩轻小说账号后再试！"))
                    }

                    // Remove anti-crawler dummy elements
                    contentElem.select("div.cgo, ins, figure, fig, br, script, center, .tp, .bd").remove()

                    // Extract chapter ID as Long
                    val chIdLong = chapterId.toLongOrNull() ?: 0L

                    // Check if chapterlog.js exists in doc
                    val scriptSrc = doc.select("script[src*=chapterlog.js]").firstOrNull()?.attr("src")
                    if (!scriptSrc.isNullOrBlank()) {
                        try {
                            val fullScriptUrl = fixUrl(scriptSrc)
                            val sReq = Request.Builder().url(fullScriptUrl).get().build()
                            val sResp = client.newCall(sReq).execute()
                            if (sResp.isSuccessful) {
                                val jsCode = sResp.body?.string() ?: ""
                                chapterLogResolver.parseTemplate(jsCode)
                            }
                        } catch (e: Exception) {
                            // Ignored, resolver will use cached/fallback template
                        }
                    }

                    // Native LCG de-scramble
                    val shuffleParams = chapterLogResolver.resolve(chIdLong)
                    com.example.epubreader.data.linovelib.restore.BiliNovelRestore.restore(contentElem, shuffleParams)

                    // Traverse children in sequential document order to keep exact typesetting
                    for (node in contentElem.children()) {
                        val style = node.attr("style")
                        if (style.contains("position: absolute", ignoreCase = true) ||
                            style.contains("display: none", ignoreCase = true) ||
                            style.contains("visibility: hidden", ignoreCase = true)) {
                            continue
                        }

                        // Check for images inside node
                        val imgs = if (node.tagName() == "img") listOf(node) else node.select("img")
                        if (imgs.isNotEmpty()) {
                            for (img in imgs) {
                                val realSrc = img.attr("data-src").ifEmpty {
                                    img.attr("data-original").ifEmpty {
                                        img.attr("data-url").ifEmpty {
                                            img.attr("data-lazy-src").ifEmpty {
                                                img.attr("src")
                                            }
                                        }
                                    }
                                }

                                if (realSrc.isNotBlank() && !realSrc.contains("sloading.svg") && !realSrc.contains("book-cover-no")) {
                                    val fullImgUrl = fixUrl(realSrc)
                                    if (!allImages.contains(fullImgUrl)) {
                                        allImages.add(fullImgUrl)
                                    }
                                    allParagraphs.add("<!--IMAGE:${fullImgUrl}-->")
                                }
                            }
                        }

                        // Check for text inside node (if not only an image)
                        if (node.tagName() in listOf("p", "h1", "h2", "h3", "h4", "div") && imgs.isEmpty()) {
                            val t = cleanParagraphText(node.text())
                            if (t.isNotBlank() &&
                                !t.contains("内容加载失败") &&
                                !t.contains("內容加載失敗") &&
                                !t.contains("手机版页面由于相容性问题") &&
                                !t.contains("手機版頁面由於相容性問題") &&
                                !t.contains("tw.linovelib.com", ignoreCase = true) &&
                                !t.contains("www.linovelib.com", ignoreCase = true) &&
                                !t.contains("嗶哩輕小說", ignoreCase = true) &&
                                !t.contains("哔哩轻小说", ignoreCase = true)) {
                                allParagraphs.add(t)
                            }
                        }
                    }

                    // Fallback if no children were processed
                    if (allParagraphs.isEmpty()) {
                        val text = cleanParagraphText(contentElem.wholeText())
                        for (line in text.split("\n")) {
                            val t = line.trim()
                            if (t.isNotBlank() && !t.contains("內容加載失敗") && !t.contains("内容加载失败")) {
                                allParagraphs.add(t)
                            }
                        }
                    }
                }

                // CRITICAL: Extract next page in current chapter from ReadParams.url_next (e.g. /novel/3095/154933_2.html)
                val nextMatch = Pattern.compile("url_next\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html)
                val nextUrlInJs = if (nextMatch.find()) nextMatch.group(1) ?: "" else ""

                val subpageMatch = Pattern.compile("/novel/\\d+/${chapterId}_(\\d+)\\.html").matcher(nextUrlInJs)
                if (nextUrlInJs.isNotBlank() && subpageMatch.find()) {
                    curUrl = fixUrl(nextUrlInJs)
                    pageIndex++
                } else {
                    // Check HTML pager links as fallback
                    val htmlPagerMatch = Pattern.compile("href=['\"](/novel/\\d+/${chapterId}_\\d+\\.html)['\"]").matcher(html)
                    var foundNext = false
                    while (htmlPagerMatch.find()) {
                        val candidate = fixUrl(htmlPagerMatch.group(1) ?: "")
                        if (!visitedUrls.contains(candidate)) {
                            curUrl = candidate
                            pageIndex++
                            foundNext = true
                            break
                        }
                    }
                    if (!foundNext) {
                        break
                    }
                }
            }

            Result.success(
                LinovelibChapterContent(
                    chapterId = chapterId,
                    title = chapterTitle,
                    paragraphs = allParagraphs,
                    imageUrls = allImages
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download binary image bytes with proper Referer
     */
    suspend fun downloadImage(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val fixed = fixUrl(imageUrl)
            if (fixed.isBlank() || fixed.endsWith(".svg", ignoreCase = true)) return@withContext null
            val req = Request.Builder()
                .url(fixed)
                .header("Referer", "$BASE_URL/")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                resp.body?.bytes()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLinovelibHtml(html: String): List<LinovelibNovel> {
        val list = mutableListOf<LinovelibNovel>()
        val seenIds = mutableSetOf<String>()
        val doc = Jsoup.parse(html, BASE_URL)

        // 1. Standard book items
        val bookElements = doc.select(".book-li, .book-item, .rank-item, .novels-item, .search-item, li.item")
        for (el in bookElements) {
            val link = el.select("a[href*=/novel/]").firstOrNull() ?: continue
            val href = link.attr("href")
            val idMatch = Pattern.compile("/novel/(\\d+)\\.html").matcher(href)
            if (!idMatch.find()) continue
            val id = idMatch.group(1) ?: continue
            if (!seenIds.add(id)) continue

            var title = el.select(".book-title, .title, h4, h3").firstOrNull()?.text()?.trim() ?: link.text().trim()
            if (title.isBlank()) {
                title = el.select("img").attr("alt").trim()
            }
            title = title.replace(Regex("^\\d+(\\.\\d+)?"), "").trim()
            title = title.replace(Regex("作者[：:].*$"), "").trim()

            var author = el.select(".book-author, .author, span:contains(作者)").text()
                .replace("作者：", "").replace("作者:", "").trim().ifEmpty { "未知作者" }

            val desc = el.select(".book-desc, .desc, .intro, .book-dec, p").text().trim()
            val category = el.select(".book-tag, .tag, .category").text().trim().ifEmpty { "轻小说" }

            val prefix = id.toIntOrNull()?.let { it / 1000 } ?: 0
            val coverUrl = "$BASE_URL/files/article/image/$prefix/$id/${id}s.jpg"

            if (title.isNotBlank()) {
                list.add(
                    LinovelibNovel(
                        id = id,
                        title = title,
                        author = author,
                        coverUrl = coverUrl,
                        category = category,
                        description = desc
                    )
                )
            }
        }

        // 2. Generic fallback novel links
        if (list.size < 5) {
            val links = doc.select("a[href*=/novel/]")
            for (a in links) {
                val href = a.attr("href")
                val m = Pattern.compile("/novel/(\\d+)\\.html").matcher(href)
                if (m.find()) {
                    val id = m.group(1) ?: continue
                    if (seenIds.add(id)) {
                        var title = a.text().trim()
                        if (title.isBlank()) {
                            title = a.select("img").attr("alt").trim()
                        }
                        title = title.replace(Regex("^\\d+(\\.\\d+)?"), "").trim()
                        title = title.replace(Regex("作者[：:].*$"), "").trim()

                        if (title.length >= 2 && !title.contains("阅读") && !title.contains("目录") && !title.contains("最新")) {
                            val prefix = id.toIntOrNull()?.let { it / 1000 } ?: 0
                            val coverUrl = "$BASE_URL/files/article/image/$prefix/$id/${id}s.jpg"
                            list.add(
                                LinovelibNovel(
                                    id = id,
                                    title = title,
                                    author = "未知作者",
                                    coverUrl = coverUrl
                                )
                            )
                        }
                    }
                }
            }
        }

        return list
    }

    private fun parseCatalogHtml(novelId: String, html: String): List<LinovelibVolume> {
        val doc = Jsoup.parse(html, BASE_URL)
        val volumes = mutableListOf<LinovelibVolume>()

        val allItems = doc.select(".chapter-bar, .volume-title, .v-line, h2, h3.chapter-title, .chapter-item, li[class*=chapter], .chapter-list li, ul.css-1")

        var curVolumeName = "第一卷"
        var curVolIndex = 1
        var curVolChapters = mutableListOf<LinovelibChapter>()
        var chapterGlobalIndex = 1

        for (el in allItems) {
            val isHeader = el.hasClass("chapter-bar") || el.hasClass("volume-title") || el.hasClass("v-line") ||
                    el.tagName() in listOf("h2", "h3") || el.select(".volume-name").isNotEmpty()

            if (isHeader) {
                if (curVolChapters.isNotEmpty()) {
                    volumes.add(
                        LinovelibVolume(
                            volumeId = "${novelId}_vol_$curVolIndex",
                            volumeIndex = curVolIndex,
                            volumeName = curVolumeName,
                            totalChapters = curVolChapters.size,
                            chapters = curVolChapters.toList()
                        )
                    )
                    curVolChapters = mutableListOf()
                    curVolIndex++
                }
                curVolumeName = el.text().trim().ifEmpty { "第${curVolIndex}卷" }
            } else {
                val a = el.select("a[href*=/novel/]").firstOrNull() ?: if (el.tagName() == "a") el else null
                if (a != null) {
                    val href = a.attr("href")
                    val chTitle = a.text().trim()
                    if (href.contains(".html") && chTitle.isNotBlank()) {
                        val chIdMatch = Pattern.compile("/novel/\\d+/(\\d+)\\.html").matcher(href)
                        val chId = if (chIdMatch.find()) chIdMatch.group(1) ?: "$chapterGlobalIndex" else "$chapterGlobalIndex"
                        curVolChapters.add(
                            LinovelibChapter(
                                id = chId,
                                title = chTitle,
                                url = fixUrl(href),
                                chapterIndex = chapterGlobalIndex++
                            )
                        )
                    }
                }
            }
        }

        // Add remaining volume
        if (curVolChapters.isNotEmpty() || volumes.isEmpty()) {
            volumes.add(
                LinovelibVolume(
                    volumeId = "${novelId}_vol_$curVolIndex",
                    volumeIndex = curVolIndex,
                    volumeName = curVolumeName,
                    totalChapters = curVolChapters.size,
                    chapters = curVolChapters.toList()
                )
            )
        }

        return volumes
    }

    private fun cleanParagraphText(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "")
            .replace("\u00A0", " ")
            .replace("\u3000", "  ")
            .replace(Regex("&nbsp;?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
            .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
            .trim()
    }

    private fun fixUrl(url: String): String {
        if (url.isBlank()) return ""
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$BASE_URL$url"
            else -> "$BASE_URL/$url"
        }
    }
}
