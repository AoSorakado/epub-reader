package com.example.epubreader.data.network

import android.util.Xml
import com.example.epubreader.data.model.network.WebDavResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.net.URLDecoder

class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val credential = if (username.isNotBlank() || password.isNotBlank()) {
        Credentials.basic(username, password)
    } else null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val newReq = if (credential != null && req.header("Authorization") == null) {
                req.newBuilder().header("Authorization", credential).build()
            } else req
            chain.proceed(newReq)
        }
        .authenticator { _, response ->
            if (response.request.header("Authorization") != null) {
                null
            } else if (credential != null) {
                response.request.newBuilder()
                    .header("Authorization", credential)
                    .build()
            } else null
        }
        .build()

    private fun encodeUrlForHttp(rawUrl: String): String {
        return try {
            val uri = java.net.URI(rawUrl)
            uri.toASCIIString()
        } catch (e: Exception) {
            val schemeEnd = rawUrl.indexOf("://")
            if (schemeEnd != -1) {
                val scheme = rawUrl.substring(0, schemeEnd + 3)
                val rest = rawUrl.substring(schemeEnd + 3)
                val hostSlash = rest.indexOf('/')
                if (hostSlash != -1) {
                    val host = rest.substring(0, hostSlash)
                    val path = rest.substring(hostSlash)
                    val encodedPath = path.split("/").joinToString("/") { segment ->
                        android.net.Uri.encode(segment)
                    }
                    "$scheme$host$encodedPath"
                } else {
                    rawUrl
                }
            } else {
                rawUrl
            }
        }
    }

    suspend fun listFiles(path: String = ""): List<WebDavResource> = withContext(Dispatchers.IO) {
        val targetUrl = if (path.isEmpty()) {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            resolveUrl(path)
        }

        val httpUrl = encodeUrlForHttp(targetUrl)

        val request = Request.Builder()
            .url(httpUrl)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "1")
            .build()

        val xmlResponse = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("PROPFIND failed (${response.code} ${response.message})")
            response.body?.string() ?: ""
        }
        
        val allResources = parsePropfindXml(xmlResponse)

        // Filter out the requested directory itself
        val cleanTargetUrl = targetUrl.trimEnd('/')
        val targetPathOnly = try { java.net.URI(cleanTargetUrl).path?.trimEnd('/') ?: cleanTargetUrl } catch (e: Exception) { cleanTargetUrl }

        allResources.filter { res ->
            val resPath = res.path.trimEnd('/')
            val decodedResPath = try { java.net.URI(resPath).path?.trimEnd('/') ?: resPath } catch (e: Exception) { resPath }
            val decodedTargetPath = try { android.net.Uri.decode(targetPathOnly) } catch (e: Exception) { targetPathOnly }
            
            resPath != targetPathOnly && 
            decodedResPath != decodedTargetPath && 
            res.name.isNotBlank() &&
            res.name != "." &&
            res.name != ".." &&
            !res.name.startsWith("._")
        }
    }

    private fun parsePropfindXml(xmlData: String): List<WebDavResource> {
        val resources = mutableListOf<WebDavResource>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlData))

        var eventType = parser.eventType
        var currentTag = ""

        var href = ""
        var isDirectory = false
        var contentLength: Long = 0
        var displayName = ""

        var inResponse = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.contains("response", ignoreCase = true)) {
                        inResponse = true
                        href = ""
                        isDirectory = false
                        contentLength = 0
                        displayName = ""
                    }
                    if (currentTag.contains("collection", ignoreCase = true)) {
                        isDirectory = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        if (currentTag.contains("href", ignoreCase = true)) {
                            href = text
                        } else if (currentTag.contains("displayname", ignoreCase = true)) {
                            displayName = text
                        } else if (currentTag.contains("getcontentlength", ignoreCase = true)) {
                            contentLength = text.toLongOrNull() ?: 0
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.contains("response", ignoreCase = true)) {
                        inResponse = false
                        // If displayname is empty, extract from href
                        if (displayName.isEmpty()) {
                            val parts = href.trimEnd('/').split("/")
                            if (parts.isNotEmpty()) {
                                displayName = android.net.Uri.decode(parts.last())
                            }
                        }
                        val fullUrl = resolveUrl(href)
                        resources.add(WebDavResource(name = displayName, isDirectory = isDirectory, path = fullUrl, size = contentLength))
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return resources
    }

    suspend fun downloadFile(path: String, destination: File, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        // Construct correct URL (path from PROPFIND usually includes absolute path from server root)
        // If baseUrl is http://192.168.1.5:8080 and path is /Dav/Book.epub, we just use baseUrl host + path
        
        val url = if (path.startsWith("http")) path else {
            val startIndex = baseUrl.indexOf("://") + 3
            val slashIndex = baseUrl.indexOf("/", startIndex)
            val serverHost = if (slashIndex != -1) baseUrl.substring(0, slashIndex) else baseUrl
            if (path.startsWith("/")) serverHost + path else "$baseUrl/$path"
        }
        
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            onProgress(bytesCopied.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }
        }
        true
    }

    fun resolveUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val cleanBase = baseUrl.trimEnd('/')
        return try {
            val uri = java.net.URI(baseUrl)
            val hostOrigin = "${uri.scheme}://${uri.authority}"
            val basePath = uri.path?.trimEnd('/') ?: ""

            if (path.startsWith("/")) {
                if (basePath.isNotEmpty() && path.startsWith(basePath)) {
                    "$hostOrigin$path"
                } else if (basePath.isEmpty()) {
                    "$hostOrigin$path"
                } else {
                    "$hostOrigin$basePath$path"
                }
            } else {
                "$cleanBase/$path"
            }
        } catch (e: Exception) {
            val cleanPath = path.trimStart('/')
            "$cleanBase/$cleanPath"
        }
    }

    suspend fun uploadTextFile(path: String, content: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = content.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, null)
                } else {
                    val code = response.code
                    val msg = response.message
                    Pair(false, "HTTP $code ($msg)")
                }
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "连接失败")
        }
    }

    suspend fun getTextFile(path: String): String? = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", "".toRequestBody())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 405 // 405 means directory already exists
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun streamFile(path: String, block: (java.io.InputStream) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body ?: return@withContext false
            
            body.byteStream().use { input ->
                block(input)
            }
        }
        true
    }

    suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        // 1. Try HEAD request
        try {
            val headRequest = Request.Builder().url(url).head().build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val lengthHeader = response.header("Content-Length")?.toLongOrNull()
                    if (lengthHeader != null && lengthHeader > 0) {
                        return@withContext lengthHeader
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore and fallback to PROPFIND
        }

        // 2. Try PROPFIND Depth: 0
        try {
            val propfindRequest = Request.Builder()
                .url(url)
                .method("PROPFIND", "".toRequestBody())
                .header("Depth", "0")
                .build()

            client.newCall(propfindRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string() ?: ""
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(StringReader(xml))
                    var eventType = parser.eventType
                    var currentTag = ""
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            currentTag = parser.name
                        } else if (eventType == XmlPullParser.TEXT) {
                            if (currentTag.contains("getcontentlength", ignoreCase = true)) {
                                val len = parser.text.trim().toLongOrNull()
                                if (len != null && len > 0) {
                                    return@withContext len
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        0L
    }
}

