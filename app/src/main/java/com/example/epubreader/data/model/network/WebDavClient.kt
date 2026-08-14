package com.example.epubreader.data.model.network

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader

class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient()

    private val authHeader = Credentials.basic(username, password)

    /**
     * Lists resources in the given directory path.
     */
    suspend fun listFiles(path: String = ""): List<WebDavResource> = withContext(Dispatchers.IO) {
        val url = if (baseUrl.endsWith("/") && path.startsWith("/")) {
            baseUrl + path.substring(1)
        } else if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            "$baseUrl/$path"
        } else {
            baseUrl + path
        }

        // Standard PROPFIND body to request minimal info
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength/>
                </D:prop>
            </D:propfind>
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("Depth", "1") // 1 means get this folder and its direct children
            .method("PROPFIND", propfindBody.toRequestBody(null))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("WebDAV Error: ${response.code}")
        }

        val xmlBody = response.body?.string() ?: return@withContext emptyList()
        parsePropFindXml(xmlBody)
    }

    /**
     * Download a file from WebDAV as a stream.
     */
    suspend fun downloadFile(path: String): InputStream = withContext(Dispatchers.IO) {
        val url = if (baseUrl.endsWith("/")) baseUrl + path.dropWhile { it == '/' } else "$baseUrl/$path"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to download file: ${response.code}")
        }
        
        response.body?.byteStream() ?: throw Exception("Empty response body")
    }

    private fun parsePropFindXml(xml: String): List<WebDavResource> {
        val resources = mutableListOf<WebDavResource>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        
        var currentHref = ""
        var isCollection = false
        var contentLength: Long = 0
        var inPropStat = false
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.replace(Regex("^.*:"), "") // Strip namespace prefix (e.g., D:href -> href)
            
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "response" -> {
                            currentHref = ""
                            isCollection = false
                            contentLength = 0
                        }
                        "propstat" -> inPropStat = true
                        "href" -> {
                            currentHref = parser.nextText()
                        }
                        "collection" -> {
                            if (inPropStat) isCollection = true
                        }
                        "getcontentlength" -> {
                            val lengthStr = parser.nextText()
                            contentLength = lengthStr.toLongOrNull() ?: 0
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "propstat" -> inPropStat = false
                        "response" -> {
                            // Extract just the name from the href
                            val decodedHref = java.net.URLDecoder.decode(currentHref, "UTF-8")
                            val cleanPath = decodedHref.trimEnd('/')
                            val resourceName = cleanPath.substringAfterLast("/")
                            
                            // Don't add the root directory itself (which comes back in Depth 1)
                            if (resourceName.isNotEmpty()) {
                                resources.add(
                                    WebDavResource(
                                        name = resourceName,
                                        isDirectory = isCollection,
                                        path = currentHref, // Keep original encoded href for future requests
                                        size = contentLength
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        
        // Remove the first item as it's always the queried directory itself
        if (resources.isNotEmpty()) {
            resources.removeAt(0)
        }
        
        return resources
    }
}
