package com.example.epubreader.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubChapter(
    val title: String,
    val href: String,
    val content: String // Raw HTML content of the chapter
)

data class EpubBook(
    val title: String,
    val author: String,
    val coverImage: ByteArray?,
    val chapters: List<EpubChapter>,
    val images: Map<String, ByteArray>
)

object EpubParser {

    /**
     * Parse an EPUB file from an InputStream.
     * Extracts title, author, cover, and ordered chapters.
     */
    fun parse(inputStream: InputStream): EpubBook {
        val files = mutableMapOf<String, ByteArray>()
        
        // 1. Unzip everything into memory (for lightweight reading, could be optimized for very large files)
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    var name = entry.name.replace("\\", "/")
                    if (name.startsWith("./")) name = name.substring(2)
                    if (name.startsWith("/")) name = name.substring(1)
                    files[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        // 2. Find container.xml to get OPF path
        val containerXml = files["META-INF/container.xml"] ?: throw Exception("Not a valid EPUB: Missing META-INF/container.xml")
        val opfPath = parseContainerForOpfPath(containerXml)
        val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 3. Parse OPF file
        val opfXml = files[opfPath] ?: throw Exception("OPF file not found at $opfPath")
        val opfData = parseOpf(opfXml)

        // 4. Get Cover Image
        var coverData: ByteArray? = null
        if (opfData.coverHref != null) {
            val coverPath = basePath + opfData.coverHref
            coverData = files[coverPath]
        }

        // 5. Read Chapters based on spine order
        val chapters = mutableListOf<EpubChapter>()
        for (item in opfData.spine) {
            val href = opfData.manifest[item]?.href ?: continue
            val decodedHref = android.net.Uri.decode(href)
            val withoutAnchor = decodedHref.substringBefore("#")
            val chapterPath = basePath + withoutAnchor
            val htmlContent = files[chapterPath]?.let { String(it, Charsets.UTF_8) } ?: "<h1>[File not found in ZIP: $chapterPath]</h1>"
            
            // Extract chapter title from NCX or fallback to href
            val title = "Chapter" // Simplification: in a full parser, we'd read the NCX or NAV file for real titles
            
            chapters.add(EpubChapter(title = title, href = chapterPath, content = htmlContent))
        }

        val images = mutableMapOf<String, ByteArray>()
        for ((key, value) in files) {
            val lowerKey = key.lowercase()
            if (lowerKey.endsWith(".jpg") || 
                lowerKey.endsWith(".jpeg") || 
                lowerKey.endsWith(".png") ||
                lowerKey.endsWith(".gif") ||
                lowerKey.endsWith(".webp")) {
                images[lowerKey] = value
            }
        }

        return EpubBook(
            title = opfData.title,
            author = opfData.author,
            coverImage = coverData,
            chapters = chapters,
            images = images
        )
    }

    /**
     * Highly optimized method to extract ONLY the cover image from an EPUB stream.
     * Prevents OOM by ignoring heavy HTML chapters.
     */
    fun extractCoverOnly(inputStream: InputStream): ByteArray? {
        val files = mutableMapOf<String, ByteArray>()
        
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        // Only cache metadata files and potential image files
                        // We skip heavy .xhtml, .html, .xml (except OPF/container) files
                        if (name == "META-INF/container.xml" || name.endsWith(".opf") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                            files[name] = zip.readBytes()
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            
            val containerXml = files["META-INF/container.xml"] ?: return null
            val opfPath = parseContainerForOpfPath(containerXml)
            val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            
            val opfXml = files[opfPath] ?: return null
            val opfData = parseOpf(opfXml)
            
            if (opfData.coverHref != null) {
                val coverPath = basePath + opfData.coverHref
                return files[coverPath]
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseContainerForOpfPath(xmlData: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setInput(xmlData.inputStream(), "UTF-8")
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val mediaType = parser.getAttributeValue(null, "media-type")
                if (mediaType == "application/oebps-package+xml") {
                    return parser.getAttributeValue(null, "full-path")
                }
            }
            eventType = parser.next()
        }
        throw Exception("Could not find OPF path in container.xml")
    }

    private data class OpfData(
        var title: String = "Unknown",
        var author: String = "Unknown",
        var coverHref: String? = null,
        val manifest: MutableMap<String, ManifestItem> = mutableMapOf(),
        val spine: MutableList<String> = mutableListOf()
    )

    private data class ManifestItem(val id: String, val href: String, val mediaType: String)

    private fun parseOpf(xmlData: ByteArray): OpfData {
        val data = OpfData()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlData.inputStream(), "UTF-8")

        var eventType = parser.eventType
        var coverImageId: String? = null
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (parser.name) {
                        "meta" -> {
                            val name = parser.getAttributeValue(null, "name")
                            if (name == "cover") {
                                coverImageId = parser.getAttributeValue(null, "content")
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type")
                            if (id != null && href != null && mediaType != null) {
                                data.manifest[id] = ManifestItem(id, href, mediaType)
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                data.spine.add(idref)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "dc:title" -> data.title = text
                            "dc:creator" -> data.author = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        // Find cover href
        if (coverImageId != null) {
            data.coverHref = data.manifest[coverImageId]?.href
        } else {
            // Fallback: look for an item with properties="cover-image" or id="cover"
            val fallbackCover = data.manifest.values.find { it.id.contains("cover", ignoreCase = true) && it.mediaType.startsWith("image/") }
            if (fallbackCover != null) {
                data.coverHref = fallbackCover.href
            }
        }

        return data
    }
}
