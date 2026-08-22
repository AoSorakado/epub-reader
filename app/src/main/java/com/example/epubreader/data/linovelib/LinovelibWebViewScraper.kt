package com.example.epubreader.data.linovelib

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Headless WebView scraper that executes Linovelib's JavaScript layer (chapterlog.js),
 * resolving anti-crawler paragraph scrambling, dynamic DOM reordering, and missing text decryption.
 */
class LinovelibWebViewScraper(private val context: Context) {

    data class ScrapedPage(
        val title: String,
        val paragraphs: List<String>,
        val images: List<String>,
        val nextUrl: String
    )

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun getOrCreateWebView(): WebView = withContext(Dispatchers.Main) {
        webView?.let { return@withContext it }
        val wv = WebView(context.applicationContext)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Block image downloading during WebView text parsing for blazing fast rendering
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true

        val prefs = context.getSharedPreferences("linovelib_prefs", Context.MODE_PRIVATE)
        val savedUa = prefs.getString("user_agent", "") ?: ""
        if (savedUa.isNotBlank()) {
            settings.userAgentString = savedUa
        }

        webView = wv
        wv
    }

    /**
     * Scrapes a single chapter subpage via headless WebView.
     */
    suspend fun scrapePage(url: String): Result<ScrapedPage> = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()

        val result = withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine<Result<ScrapedPage>> { cont ->
                var isResumed = false

                fun safeResume(res: Result<ScrapedPage>) {
                    if (!isResumed) {
                        isResumed = true
                        cont.resume(res)
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        // Block ad scripts and heavy media to speed up scraping
                        if (reqUrl.contains("google") ||
                            reqUrl.contains("adsbygoogle") ||
                            reqUrl.contains("cloudflareinsights") ||
                            reqUrl.endsWith(".jpg") ||
                            reqUrl.endsWith(".png") ||
                            reqUrl.endsWith(".gif") ||
                            reqUrl.endsWith(".webp") ||
                            reqUrl.endsWith(".mp4")) {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            val desc = error?.description?.toString() ?: "Network Error"
                            safeResume(Result.failure(Exception("WebView 加载失败: $desc")))
                        }
                    }

                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        // Function to poll DOM extraction up to 4 times (waiting for chapterlog.js or Turnstile)
                        fun attemptExtract(retriesLeft: Int) {
                            if (isResumed) return
                            val extractJs = """
                                (function() {
                                    try {
                                        var elem = document.getElementById('acontent') || document.querySelector('.read-content') || document.querySelector('#chaptercontent');
                                        if (!elem) {
                                            if (document.body && (document.body.innerText.indexOf('Cloudflare') >= 0 || document.body.innerText.indexOf('人机验证') >= 0 || document.body.innerText.indexOf('Just a moment') >= 0)) {
                                                return JSON.stringify({ error: 'CLOUDFLARE_CHALLENGE' });
                                            }
                                            return JSON.stringify({ error: 'NO_CONTENT' });
                                        }

                                        var title = '';
                                        var titleElem = document.getElementById('atitle') || document.querySelector('h1');
                                        if (titleElem) title = titleElem.innerText.trim();

                                        var items = [];
                                        var imgs = [];

                                        // Collect in DOM order
                                        var nodes = elem.querySelectorAll('p, div.imagecontent, img, h1, h2, h3');
                                        for (var i = 0; i < nodes.length; i++) {
                                            var node = nodes[i];
                                            var style = window.getComputedStyle(node);
                                            if (style.display === 'none' || style.visibility === 'hidden' || style.position === 'absolute') {
                                                continue;
                                            }

                                            var img = (node.tagName.toLowerCase() === 'img') ? node : node.querySelector('img');
                                            if (img) {
                                                var src = img.getAttribute('data-src') || img.getAttribute('data-original') || img.getAttribute('data-url') || img.getAttribute('data-lazy-src') || img.src;
                                                if (src && src.indexOf('sloading.svg') === -1 && src.indexOf('book-cover-no') === -1) {
                                                    if (imgs.indexOf(src) === -1) {
                                                        imgs.push(src);
                                                    }
                                                    items.push('<!--IMAGE:' + src + '-->');
                                                }
                                            } else if (node.innerText && node.innerText.trim()) {
                                                var t = node.innerText.trim();
                                                if (t && 
                                                    t.indexOf('内容加载失败') === -1 && 
                                                    t.indexOf('內容加載失敗') === -1 && 
                                                    t.indexOf('linovelib.com') === -1 && 
                                                    t.indexOf('手机版页面由于相容性问题') === -1 && 
                                                    t.indexOf('手機版頁面由於相容性問題') === -1) {
                                                    if (items.length === 0 || items[items.length - 1] !== t) {
                                                        items.push(t);
                                                    }
                                                }
                                            }
                                        }

                                        var nextUrl = '';
                                        if (typeof ReadParams !== 'undefined' && ReadParams.url_next) {
                                            nextUrl = ReadParams.url_next;
                                        }

                                        return JSON.stringify({
                                            title: title,
                                            paragraphs: items,
                                            images: imgs,
                                            nextUrl: nextUrl
                                        });
                                    } catch(e) {
                                        return JSON.stringify({ error: e.toString() });
                                    }
                                })()
                            """.trimIndent()

                            wv.evaluateJavascript(extractJs) { jsResult ->
                                if (isResumed) return@evaluateJavascript
                                try {
                                    if (jsResult == null || jsResult == "null" || jsResult.isBlank()) {
                                        if (retriesLeft > 0) {
                                            mainHandler.postDelayed({ attemptExtract(retriesLeft - 1) }, 600)
                                        } else {
                                            safeResume(Result.failure(Exception("JS 执行结果为空")))
                                        }
                                        return@evaluateJavascript
                                    }

                                    val unescaped = if (jsResult.startsWith("\"") && jsResult.endsWith("\"")) {
                                        JSONObject(mapOf("v" to jsResult)).getString("v")
                                    } else {
                                        jsResult
                                    }

                                    val json = JSONObject(unescaped)
                                    if (json.has("error")) {
                                        val err = json.getString("error")
                                        if (retriesLeft > 0 && (err == "NO_CONTENT" || err == "CLOUDFLARE_CHALLENGE")) {
                                            // Retry in 800ms to allow Turnstile or chapterlog.js to finish
                                            mainHandler.postDelayed({ attemptExtract(retriesLeft - 1) }, 800)
                                            return@evaluateJavascript
                                        }
                                        safeResume(Result.failure(Exception(err)))
                                        return@evaluateJavascript
                                    }

                                    val t = json.optString("title", "")
                                    val nextUrl = json.optString("nextUrl", "")
                                    val pArray = json.optJSONArray("paragraphs")
                                    val paragraphs = mutableListOf<String>()
                                    if (pArray != null) {
                                        for (idx in 0 until pArray.length()) {
                                            paragraphs.add(pArray.getString(idx))
                                        }
                                    }

                                    val iArray = json.optJSONArray("images")
                                    val images = mutableListOf<String>()
                                    if (iArray != null) {
                                        for (idx in 0 until iArray.length()) {
                                            images.add(iArray.getString(idx))
                                        }
                                    }

                                    safeResume(
                                        Result.success(
                                            ScrapedPage(
                                                title = t,
                                                paragraphs = paragraphs,
                                                images = images,
                                                nextUrl = nextUrl
                                            )
                                        )
                                    )
                                } catch (e: Exception) {
                                    if (retriesLeft > 0) {
                                        mainHandler.postDelayed({ attemptExtract(retriesLeft - 1) }, 600)
                                    } else {
                                        safeResume(Result.failure(e))
                                    }
                                }
                            }
                        }

                        // Initial check after 350ms
                        mainHandler.postDelayed({ attemptExtract(4) }, 350)
                    }
                }

                wv.loadUrl(url)
            }
        }

        result ?: Result.failure(Exception("WebView 抓取超时 (10s)"))
    }

    /**
     * Crawls an entire chapter across all paginated subpages using the Headless WebView,
     * ensuring 100% deobfuscated and sequential reading order.
     */
    suspend fun scrapeFullChapter(firstPageUrl: String): Result<LinovelibChapterContent> {
        val allParagraphs = mutableListOf<String>()
        val allImages = mutableListOf<String>()
        var chapterTitle = ""
        var chapterId = ""

        val m = Pattern.compile("/novel/\\d+/(\\d+)(?:_\\d+)?\\.html").matcher(firstPageUrl)
        if (m.find()) {
            chapterId = m.group(1) ?: ""
        }

        var curUrl = if (firstPageUrl.startsWith("http")) firstPageUrl else "${LinovelibApiClient.BASE_URL}$firstPageUrl"
        val visitedUrls = mutableSetOf<String>()
        var pageCount = 0

        while (curUrl.isNotBlank() && !visitedUrls.contains(curUrl) && pageCount < 50) {
            visitedUrls.add(curUrl)
            pageCount++

            val pageResult = scrapePage(curUrl)
            if (pageResult.isFailure) {
                return Result.failure(pageResult.exceptionOrNull() ?: Exception("抓取失败"))
            }

            val page = pageResult.getOrThrow()
            if (chapterTitle.isBlank() && page.title.isNotBlank()) {
                chapterTitle = page.title
            }

            allParagraphs.addAll(page.paragraphs)
            allImages.addAll(page.images)

            // Check if there is a next subpage in this chapter
            val nextUrl = page.nextUrl.trim()
            val subpageMatch = Pattern.compile("/novel/\\d+/${chapterId}_(\\d+)\\.html").matcher(nextUrl)
            if (nextUrl.isNotBlank() && subpageMatch.find()) {
                curUrl = if (nextUrl.startsWith("http")) nextUrl else "${LinovelibApiClient.BASE_URL}$nextUrl"
                delay(100)
            } else {
                break
            }
        }

        if (allParagraphs.isEmpty() && allImages.isEmpty()) {
            return Result.failure(Exception("章节内容为空"))
        }

        return Result.success(
            LinovelibChapterContent(
                chapterId = chapterId,
                title = chapterTitle,
                paragraphs = allParagraphs,
                imageUrls = allImages.distinct()
            )
        )
    }

    /**
     * Clean up WebView resources when done.
     */
    suspend fun release() = withContext(Dispatchers.Main) {
        try {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
