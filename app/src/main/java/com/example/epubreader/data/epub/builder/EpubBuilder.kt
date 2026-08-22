package com.example.epubreader.data.epub.builder

import com.example.epubreader.data.novelia.NoveliaChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * High-performance, zero-OOM EPUB 3 / EPUB 2 standard package generator.
 * Produces compliant .epub containers compatible with all major e-readers and the app's internal SimulationPageView.
 */
object EpubBuilder {

    data class EpubMetadata(
        val title: String,
        val author: String = "未知作者",
        val description: String = "",
        val coverImageBytes: ByteArray? = null,
        val illustrations: Map<String, ByteArray> = emptyMap(), // filename -> bytes
        val language: String = "zh-CN",
        val identifier: String = "urn:uuid:" + UUID.randomUUID().toString(),
        val publisher: String = "Novelia",
        val tags: List<String> = emptyList()
    )

    suspend fun buildEpub(
        metadata: EpubMetadata,
        chapters: List<NoveliaChapter>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            FileOutputStream(outputFile).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    // 1. mimetype MUST be the very first file and STORED uncompressed
                    writeMimetype(zip)

                    // 2. META-INF/container.xml
                    writeContainerXml(zip)

                    // 3. OEBPS/Styles/style.css
                    writeStylesheet(zip)

                    // 4. Cover Image (if available)
                    val hasCover = metadata.coverImageBytes != null && metadata.coverImageBytes.isNotEmpty()
                    if (hasCover) {
                        writeZipEntry(zip, "OEBPS/Images/cover.jpg", metadata.coverImageBytes!!)
                    }

                    // 4.5 In-chapter Illustrations
                    for ((imgName, imgBytes) in metadata.illustrations) {
                        if (imgBytes.isNotEmpty()) {
                            writeZipEntry(zip, "OEBPS/Images/$imgName", imgBytes)
                        }
                    }

                    // 5. Chapter XHTML files
                    val total = chapters.size.coerceAtLeast(1)
                    chapters.forEachIndexed { index, chapter ->
                        val chapterFilename = "OEBPS/Text/chapter_${String.format(Locale.US, "%04d", index + 1)}.xhtml"
                        val chapterContent = buildChapterXhtml(chapter, index + 1)
                        writeZipEntry(zip, chapterFilename, chapterContent.toByteArray(StandardCharsets.UTF_8))
                        onProgress((index + 1).toFloat() / total.toFloat() * 0.7f)
                    }

                    // 6. OEBPS/content.opf
                    val opfContent = buildContentOpf(metadata, chapters, hasCover)
                    writeZipEntry(zip, "OEBPS/content.opf", opfContent.toByteArray(StandardCharsets.UTF_8))

                    // 7. OEBPS/toc.ncx (EPUB 2 compatibility)
                    val ncxContent = buildTocNcx(metadata, chapters)
                    writeZipEntry(zip, "OEBPS/toc.ncx", ncxContent.toByteArray(StandardCharsets.UTF_8))

                    // 8. OEBPS/nav.xhtml (EPUB 3 compatibility)
                    val navContent = buildNavXhtml(metadata, chapters)
                    writeZipEntry(zip, "OEBPS/nav.xhtml", navContent.toByteArray(StandardCharsets.UTF_8))

                    onProgress(1.0f)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeMimetype(zip: ZipOutputStream) {
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            val bytes = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            val crc = CRC32()
            crc.update(bytes)
            setCrc(crc.value)
        }
        zip.putNextEntry(entry)
        zip.write("application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
        zip.closeEntry()
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        val entry = ZipEntry(path).apply {
            method = ZipEntry.DEFLATED
            size = bytes.size.toLong()
            val crc = CRC32()
            crc.update(bytes)
            setCrc(crc.value)
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeContainerXml(zip: ZipOutputStream) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>"""
        writeZipEntry(zip, "META-INF/container.xml", xml.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeStylesheet(zip: ZipOutputStream) {
        val css = """
@charset "UTF-8";
body {
    margin: 5% 4%;
    padding: 0;
    font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif;
    font-size: 1.05em;
    line-height: 1.8;
    text-align: justify;
    word-break: break-all;
}
h1, h2, h3, h4 {
    font-weight: bold;
    text-align: center;
    margin: 1.5em 0 1em 0;
    line-height: 1.4;
}
h1 { font-size: 1.6em; }
h2 { font-size: 1.35em; color: #3b82f6; }
h3 { font-size: 1.2em; }
p {
    text-indent: 2em;
    margin: 0.6em 0;
}
.cover-container, .illust-container {
    text-align: center;
    padding: 0;
    margin: 1em 0;
}
.cover-img, .illust-img {
    max-width: 100%;
    max-height: 100vh;
    height: auto;
    object-fit: contain;
}
.jp-text {
    font-size: 0.88em;
    color: #6b7280;
    text-indent: 2em;
    margin-top: -0.3em;
    margin-bottom: 0.8em;
}
"""
        writeZipEntry(zip, "OEBPS/Styles/style.css", css.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeCoverXhtml(zip: ZipOutputStream, title: String) {
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head>
    <title>${escapeXml(title)} - 封面</title>
    <link href="../Styles/style.css" rel="stylesheet" type="text/css"/>
</head>
<body>
    <div class="cover-container">
        <img src="../Images/cover.jpg" alt="Cover" class="cover-img"/>
    </div>
</body>
</html>"""
        writeZipEntry(zip, "OEBPS/Text/cover.xhtml", xhtml.toByteArray(StandardCharsets.UTF_8))
    }

    private fun buildChapterXhtml(chapter: NoveliaChapter, index: Int): String {
        val sb = StringBuilder()
        val chTitle = chapter.title.trim()
        sb.append("""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN">
<head>
    <title>${escapeXml(chTitle)}</title>
    <link href="../Styles/style.css" rel="stylesheet" type="text/css"/>
</head>
<body>
    <h2>${escapeXml(chTitle)}</h2>
""")

        if (chapter.japaneseTitle.isNotBlank() && chapter.japaneseTitle != chTitle) {
            sb.append("    <p class=\"jp-text\">${escapeXml(chapter.japaneseTitle)}</p>\n")
        }

        val lines = chapter.content.split("\n")
        var skippedDuplicateTitle = false

        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue

            // Check if line is an image placeholder
            if (line.startsWith("<!--IMAGE:") && line.endsWith("-->")) {
                val imgTarget = line.removePrefix("<!--IMAGE:").removeSuffix("-->").trim()
                val filename = imgTarget.substringAfterLast("/")
                if (filename.isNotBlank()) {
                    sb.append("    <div class=\"illust-container\"><img src=\"../Images/$filename\" alt=\"插图\" class=\"illust-img\"/></div>\n")
                }
                continue
            }

            // Strip any raw HTML tags (like <p>, </p>, <img.../>) that may have leaked
            line = line.replace(Regex("<[^>]*>"), "").trim()
            if (line.isEmpty()) continue

            // Deduplicate if first paragraph is identical to or matches chapter title
            if (!skippedDuplicateTitle) {
                val normLine = normalizeTitle(line)
                val normTitle = normalizeTitle(chTitle)
                val normJpTitle = normalizeTitle(chapter.japaneseTitle)

                val isTitleDup = normLine.equals(normTitle, ignoreCase = true) ||
                        (normJpTitle.isNotBlank() && normLine.equals(normJpTitle, ignoreCase = true)) ||
                        (normTitle.startsWith(normLine) && normLine.length >= 2) ||
                        (normLine.startsWith(normTitle) && normTitle.length >= 2) ||
                        ((normLine.startsWith("第") || normLine.startsWith("序") || normLine.startsWith("终") || normLine.startsWith("後") || normLine.startsWith("后")) &&
                                (normLine.contains("章") || normLine.contains("话") || normLine.contains("話") || normLine.contains("序") || normLine.contains("后记") || normLine.contains("後記")) &&
                                normLine.length <= normTitle.length + 6) ||
                        ((normTitle.contains("插图") || normTitle.contains("插圖") || normTitle.contains("特典")) && (normLine.contains("插图") || normLine.contains("插圖") || normLine.contains("特典")))

                if (isTitleDup) {
                    skippedDuplicateTitle = true
                    continue
                }
                skippedDuplicateTitle = true
            }

            sb.append("    <p>").append(escapeXml(line)).append("</p>\n")
        }

        sb.append("""</body>
</html>""")
        return sb.toString()
    }

    private fun buildContentOpf(
        metadata: EpubMetadata,
        chapters: List<NoveliaChapter>,
        hasCover: Boolean
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val currentTime = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
        <dc:identifier id="pub-id">${escapeXml(metadata.identifier)}</dc:identifier>
        <dc:title>${escapeXml(metadata.title)}</dc:title>
        <dc:creator>${escapeXml(metadata.author)}</dc:creator>
        <dc:language>${escapeXml(metadata.language)}</dc:language>
        <dc:publisher>${escapeXml(metadata.publisher)}</dc:publisher>
        <dc:description>${escapeXml(metadata.description)}</dc:description>
        <meta property="dcterms:modified">$currentTime</meta>
""")
        if (hasCover) {
            sb.append("        <meta name=\"cover\" content=\"cover-image\"/>\n")
        }
        for (tag in metadata.tags) {
            sb.append("        <dc:subject>${escapeXml(tag)}</dc:subject>\n")
        }
        sb.append("""    </metadata>
    <manifest>
        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="style" href="Styles/style.css" media-type="text/css"/>
""")
        if (hasCover) {
            sb.append("        <item id=\"cover-image\" href=\"Images/cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n")
        }

        // Register in-chapter illustrations in manifest
        metadata.illustrations.forEach { (imgName, _) ->
            val mediaType = if (imgName.endsWith(".png", true)) "image/png" else "image/jpeg"
            val safeId = "img-" + imgName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            sb.append("        <item id=\"$safeId\" href=\"Images/$imgName\" media-type=\"$mediaType\"/>\n")
        }

        chapters.forEachIndexed { index, _ ->
            val num = String.format(Locale.US, "%04d", index + 1)
            sb.append("        <item id=\"chapter-$num\" href=\"Text/chapter_$num.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        }
        sb.append("""    </manifest>
    <spine toc="ncx">
""")
        chapters.forEachIndexed { index, _ ->
            val num = String.format(Locale.US, "%04d", index + 1)
            sb.append("        <itemref idref=\"chapter-$num\"/>\n")
        }
        sb.append("""    </spine>
</package>""")
        return sb.toString()
    }

    private fun buildTocNcx(metadata: EpubMetadata, chapters: List<NoveliaChapter>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
    <head>
        <meta name="dtb:uid" content="${escapeXml(metadata.identifier)}"/>
        <meta name="dtb:depth" content="1"/>
        <meta name="dtb:totalPageCount" content="0"/>
        <meta name="dtb:maxPageNumber" content="0"/>
    </head>
    <docTitle>
        <text>${escapeXml(metadata.title)}</text>
    </docTitle>
    <navMap>
""")
        var playOrder = 1
        chapters.forEachIndexed { index, chapter ->
            val num = String.format(Locale.US, "%04d", index + 1)
            sb.append("""        <navPoint id="navPoint-$playOrder" playOrder="$playOrder">
            <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
            <content src="Text/chapter_$num.xhtml"/>
        </navPoint>
""")
            playOrder++
        }

        sb.append("""    </navMap>
</ncx>""")
        return sb.toString()
    }

    private fun buildNavXhtml(metadata: EpubMetadata, chapters: List<NoveliaChapter>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head>
    <title>目录</title>
    <link href="Styles/style.css" rel="stylesheet" type="text/css"/>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>目录</h1>
        <ol>
""")
        chapters.forEachIndexed { index, chapter ->
            val num = String.format(Locale.US, "%04d", index + 1)
            sb.append("            <li><a href=\"Text/chapter_$num.xhtml\">${escapeXml(chapter.title)}</a></li>\n")
        }
        sb.append("""        </ol>
    </nav>
</body>
</html>""")
        return sb.toString()
    }

    private fun normalizeTitle(text: String): String {
        return text
            .replace(Regex("[\\s\\p{Punct}（）()【】\\[\\]《》「」『』—\\-_:：·，,。.]"), "")
            .lowercase(Locale.ROOT)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
