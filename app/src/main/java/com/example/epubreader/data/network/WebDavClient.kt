package com.example.epubreader.data.network

import android.util.Xml
import com.example.epubreader.data.model.network.WebDavResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .authenticator { _, response ->
            val credential = Credentials.basic(username, password)
            response.request.newBuilder()
                .header("Authorization", credential)
                .build()
        }
        .build()

    suspend fun listFiles(path: String = ""): List<WebDavResource> = withContext(Dispatchers.IO) {
        val url = if (path.isEmpty()) {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        } else if (path.startsWith("http")) {
            path
        } else {
            val startIndex = baseUrl.indexOf("://") + 3
            val slashIndex = baseUrl.indexOf("/", startIndex)
            val serverHost = if (slashIndex != -1) baseUrl.substring(0, slashIndex) else baseUrl
            if (path.startsWith("/")) serverHost + path else "$baseUrl/$path"
        }
        
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "1")
            .build()

        val xmlResponse = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("PROPFIND failed: ${response.code}")
            response.body?.string() ?: ""
        }
        
        parsePropfindXml(xmlResponse)
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
                        resources.add(WebDavResource(name = displayName, isDirectory = isDirectory, path = href, size = contentLength))
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        
        // Remove the parent directory itself from the list (usually the first item or matches path)
        return resources.drop(1)
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

    suspend fun streamFile(path: String, block: (java.io.InputStream) -> Unit): Boolean = withContext(Dispatchers.IO) {
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
            
            body.byteStream().use { input ->
                block(input)
            }
        }
        true
    }
}
