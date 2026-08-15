package com.example.epubreader.data.parser

import android.net.Uri
import android.util.Xml
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubTocItem(
    val title: String,
    val href: String,
    val level: Int = 0,
    val children: List<EpubTocItem> = emptyList()
)

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
    val images: Map<String, ByteArray>,
    val toc: List<EpubTocItem> = emptyList()
)

object EpubParser {

    /**
     * Parse an EPUB file from an InputStream.
     * Extracts title, author, cover, ordered chapters, images, and full TOC.
     */
    fun parse(inputStream: InputStream): EpubBook {
        val files = mutableMapOf<String, ByteArray>()
        
        // 1. Unzip everything into memory
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

        // 5. Parse TOC (from NCX or NAV or files)
        val (tocItems, tocMap) = extractToc(files, opfData, basePath)

        // 6. Read Chapters based on spine order
        val chapters = mutableListOf<EpubChapter>()
        for ((index, item) in opfData.spine.withIndex()) {
            val href = opfData.manifest[item]?.href ?: continue
            val decodedHref = Uri.decode(href)
            val withoutAnchor = decodedHref.substringBefore("#")
            val chapterPath = basePath + withoutAnchor
            val htmlContent = files[chapterPath]?.let { String(it, Charsets.UTF_8) } ?: "<h1>[File not found: $chapterPath]</h1>"
            
            // Resolve chapter title from TOC or HTML content
            val titleFromToc = tocMap[chapterPath] ?: tocMap[withoutAnchor] ?: tocMap[decodedHref]
            val title = if (!titleFromToc.isNullOrBlank()) {
                titleFromToc
            } else {
                extractTitleFromHtml(htmlContent, opfData.title, index + 1)
            }
            
            chapters.add(EpubChapter(title = title, href = chapterPath, content = htmlContent))
        }

        // 7. Collect Images
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
            images = images,
            toc = tocItems
        )
    }

    /**
     * Parse TOC from NCX (EPUB 2) or NAV (EPUB 3) documents.
     */
    private fun extractToc(
        files: Map<String, ByteArray>,
        opfData: OpfData,
        basePath: String
    ): Pair<List<EpubTocItem>, Map<String, String>> {
        val tocItems = mutableListOf<EpubTocItem>()
        val tocMap = mutableMapOf<String, String>()

        // 1. Try finding NCX file
        var ncxPath: String? = null
        if (opfData.tocNcxId != null) {
            opfData.manifest[opfData.tocNcxId]?.let { ncxPath = basePath + it.href }
        }
        if (ncxPath == null) {
            val ncxItem = opfData.manifest.values.find { it.mediaType == "application/x-dtbncx+xml" || it.href.endsWith(".ncx", ignoreCase = true) }
            if (ncxItem != null) {
                ncxPath = basePath + ncxItem.href
            }
        }
        if (ncxPath == null) {
            ncxPath = files.keys.find { it.endsWith(".ncx", ignoreCase = true) }
        }

        if (ncxPath != null && files.containsKey(ncxPath)) {
            val ncxBase = if (ncxPath!!.contains("/")) ncxPath!!.substringBeforeLast("/") + "/" else ""
            parseNcxXml(files[ncxPath!!]!!, ncxBase, tocItems, tocMap)
            if (tocItems.isNotEmpty()) {
                return Pair(tocItems, tocMap)
            }
        }

        // 2. Try finding EPUB 3 NAV document
        var navPath: String? = null
        val navItem = opfData.manifest.values.find { it.properties?.contains("nav") == true }
        if (navItem != null) {
            navPath = basePath + navItem.href
        } else {
            navPath = files.keys.find { it.endsWith("nav.xhtml", ignoreCase = true) || it.endsWith("toc.xhtml", ignoreCase = true) }
        }

        if (navPath != null && files.containsKey(navPath)) {
            val navBase = if (navPath!!.contains("/")) navPath!!.substringBeforeLast("/") + "/" else ""
            parseNavDoc(files[navPath!!]!!, navBase, tocItems, tocMap)
            if (tocItems.isNotEmpty()) {
                return Pair(tocItems, tocMap)
            }
        }

        return Pair(tocItems, tocMap)
    }

    private fun parseNcxXml(
        bytes: ByteArray,
        basePath: String,
        tocItems: MutableList<EpubTocItem>,
        tocMap: MutableMap<String, String>
    ) {
        try {
            val xmlString = String(bytes, Charsets.UTF_8)
            val doc = Jsoup.parse(xmlString, "", org.jsoup.parser.Parser.xmlParser())
            val navPoints = doc.select("navPoint")
            for (np in navPoints) {
                val title = np.selectFirst("> navLabel > text, navLabel > text")?.text()?.trim() ?: ""
                val src = np.selectFirst("> content, content")?.attr("src")?.trim() ?: ""
                if (title.isNotBlank() && src.isNotBlank()) {
                    val fullPath = basePath + Uri.decode(src)
                    var level = 0
                    var parent = np.parent()
                    while (parent != null) {
                        if (parent.tagName().equals("navPoint", ignoreCase = true)) level++
                        parent = parent.parent()
                    }
                    val item = EpubTocItem(title = title, href = fullPath, level = level)
                    tocItems.add(item)
                    
                    val withoutAnchor = fullPath.substringBefore("#")
                    val fileNameOnly = withoutAnchor.substringAfterLast("/")
                    val rawWithoutAnchor = src.substringBefore("#")
                    val rawDecoded = Uri.decode(src).substringBefore("#")

                    tocMap[fullPath] = title
                    tocMap[withoutAnchor] = title
                    tocMap[fileNameOnly] = title
                    tocMap[src] = title
                    tocMap[rawWithoutAnchor] = title
                    tocMap[rawDecoded] = title
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseNavDoc(
        bytes: ByteArray,
        basePath: String,
        tocItems: MutableList<EpubTocItem>,
        tocMap: MutableMap<String, String>
    ) {
        try {
            val html = String(bytes, Charsets.UTF_8)
            val doc = Jsoup.parse(html)
            val navToc = doc.selectFirst("nav[*|type=toc], nav#toc, nav.toc") ?: doc.selectFirst("nav")
            if (navToc != null) {
                val links = navToc.select("a[href]")
                for (a in links) {
                    val title = a.text().trim()
                    val rawHref = a.attr("href").trim()
                    if (title.isNotBlank() && rawHref.isNotBlank()) {
                        val fullPath = basePath + Uri.decode(rawHref)
                        var level = 0
                        var parent = a.parent()
                        while (parent != null && parent != navToc) {
                            if (parent.tagName().equals("li", ignoreCase = true)) level++
                            parent = parent.parent()
                        }
                        level = (level - 1).coerceAtLeast(0)
                        val item = EpubTocItem(title = title, href = fullPath, level = level)
                        tocItems.add(item)
                        
                        val withoutAnchor = fullPath.substringBefore("#")
                        val fileNameOnly = withoutAnchor.substringAfterLast("/")
                        val rawWithoutAnchor = rawHref.substringBefore("#")
                        val rawDecoded = Uri.decode(rawHref).substringBefore("#")

                        tocMap[fullPath] = title
                        tocMap[withoutAnchor] = title
                        tocMap[fileNameOnly] = title
                        tocMap[rawHref] = title
                        tocMap[rawWithoutAnchor] = title
                        tocMap[rawDecoded] = title
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractTitleFromHtml(html: String, bookTitle: String, fallbackChapterIndex: Int): String {
        try {
            val doc = Jsoup.parse(html)
            
            // 1. Look for explicit headers: h1, h2, h3, h4
            val headings = doc.select("h1, h2, h3, h4, .chapter-title, .title, .chapter_title, .chapterName, .chapterhead, #title")
            for (h in headings) {
                val text = h.text().trim()
                if (text.isNotBlank() && text.length in 2..60 && text != bookTitle && !text.contains("untitled", ignoreCase = true)) {
                    return text
                }
            }

            // 2. Look for title tag if not book title
            val htmlTitle = doc.title().trim()
            if (htmlTitle.isNotBlank() && htmlTitle.length in 2..60 && htmlTitle != bookTitle && !htmlTitle.contains("untitled", ignoreCase = true)) {
                return htmlTitle
            }

            // 3. Look for first short, prominent paragraph
            val pTags = doc.select("p, div, span, b, strong")
            for (p in pTags.take(15)) {
                val text = p.text().trim()
                if (text.length in 2..50 && text != bookTitle) {
                    if (text.startsWith("第") || 
                        text.startsWith("序") || 
                        text.startsWith("终") || 
                        text.startsWith("後") || 
                        text.startsWith("后") || 
                        text.startsWith("间") || 
                        text.startsWith("間") || 
                        text.startsWith("插") || 
                        text.startsWith("Chapter", ignoreCase = true) || 
                        text.contains("『") || 
                        text.contains("「") ||
                        text.contains("卷") ||
                        text.contains("章")) {
                        return text
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "第 $fallbackChapterIndex 章"
    }

    /**
     * Highly optimized method to extract ONLY the cover image from an EPUB stream.
     */
    fun extractCoverOnly(inputStream: InputStream): ByteArray? {
        val files = mutableMapOf<String, ByteArray>()
        
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
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
        var tocNcxId: String? = null,
        val manifest: MutableMap<String, ManifestItem> = mutableMapOf(),
        val spine: MutableList<String> = mutableListOf()
    )

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String? = null)

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
                        "spine" -> {
                            val toc = parser.getAttributeValue(null, "toc")
                            if (!toc.isNullOrBlank()) {
                                data.tocNcxId = toc
                            }
                        }
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
                            val properties = parser.getAttributeValue(null, "properties")
                            if (id != null && href != null && mediaType != null) {
                                data.manifest[id] = ManifestItem(id, href, mediaType, properties)
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
            val fallbackCover = data.manifest.values.find { it.id.contains("cover", ignoreCase = true) && it.mediaType.startsWith("image/") }
            if (fallbackCover != null) {
                data.coverHref = fallbackCover.href
            }
        }

        return data
    }
}
