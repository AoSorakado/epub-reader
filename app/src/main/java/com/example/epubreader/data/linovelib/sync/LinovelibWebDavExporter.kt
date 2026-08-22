package com.example.epubreader.data.linovelib.sync

import android.content.Context
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.epub.builder.EpubBuilder
import com.example.epubreader.data.linovelib.LinovelibApiClient
import com.example.epubreader.data.linovelib.LinovelibNovel
import com.example.epubreader.data.linovelib.LinovelibVolume
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.data.novelia.NoveliaChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LinovelibWebDavExporter(
    private val context: Context,
    private val apiClient: LinovelibApiClient
) {

    private val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
    private val bookDao = AppDatabase.getDatabase(context).bookDao()

    fun getWebDavClient(): WebDavClient? {
        val url = prefs.getString("webdav_url", "") ?: ""
        val user = prefs.getString("webdav_user", "") ?: ""
        val pass = prefs.getString("webdav_pass", "") ?: ""
        if (url.isBlank()) return null
        return WebDavClient(url, user, pass)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "未命名" }
    }

    private fun truncateUtf8(str: String, maxBytes: Int): String {
        var s = str
        while (s.toByteArray(Charsets.UTF_8).size > maxBytes && s.isNotEmpty()) {
            s = s.substring(0, s.length - 1)
        }
        return s.trim().ifEmpty { "未命名" }
    }

    private suspend fun ensureRemoteDirectory(client: WebDavClient, dirPath: String): Boolean {
        val segments = dirPath.trim('/').split("/").filter { it.isNotEmpty() }
        var current = ""
        for (seg in segments) {
            current = if (current.isEmpty()) seg else "$current/$seg"
            client.createDirectory(current)
        }
        return true
    }

    /**
     * Scrapes all chapters of a volume across all paginated subpages, builds an EPUB with volume-specific cover,
     * uploads to WebDAV, and adds to local Bookshelf.
     */
    suspend fun exportLinovelibVolume(
        novel: LinovelibNovel,
        volume: LinovelibVolume,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val webDavClient = getWebDavClient()
            ?: return@withContext Result.failure(Exception("请先在「配置」页面配置 WebDAV 服务器"))

        try {
            val authorPart = if (novel.author.isNotBlank() && novel.author != "未知作者") {
                "[${truncateUtf8(sanitizeFilename(novel.author), 60)}] "
            } else ""
            val cleanSeriesTitle = truncateUtf8(sanitizeFilename(novel.title), 120)
            val seriesFolder = "Novels/哔哩轻小说/$authorPart$cleanSeriesTitle"

            val volName = volume.volumeName.trim().ifEmpty { "第${volume.volumeIndex}卷" }
            val cleanVolName = truncateUtf8(sanitizeFilename(volName), 80)

            // Deduplicate series title if volName already contains or starts with it
            val fullBookTitle = if (cleanVolName.startsWith(cleanSeriesTitle, ignoreCase = true) ||
                cleanVolName.contains(cleanSeriesTitle, ignoreCase = true)) {
                cleanVolName
            } else {
                "$cleanSeriesTitle $cleanVolName"
            }

            val remoteFileName = "$fullBookTitle.epub"
            val remoteFilePath = "$seriesFolder/$remoteFileName"

            onProgress(0.05f, "正在连接 WebDAV 目录...")
            ensureRemoteDirectory(webDavClient, seriesFolder)

            // Scrape chapters and all subpages
            val totalChapters = volume.chapters.size.coerceAtLeast(1)
            val noveliaChapters = mutableListOf<NoveliaChapter>()
            val allImageUrls = mutableSetOf<String>()
            var volumeFirstImageUrl: String? = null

            var failedCount = 0

            for ((idx, ch) in volume.chapters.withIndex()) {
                val pPercent = 0.08f + (idx.toFloat() / totalChapters.toFloat()) * 0.55f
                onProgress(pPercent, "正在抓取章节 (${idx + 1}/$totalChapters): ${ch.title}")

                if (idx > 0) {
                    kotlinx.coroutines.delay(400)
                }

                // Scrape using pure HTTP with BiliNovelRestore (never triggers CF, 100% deobfuscated)
                var chResult: Result<com.example.epubreader.data.linovelib.LinovelibChapterContent>? = null
                var attempt = 0
                while (attempt < 3) {
                    attempt++
                    val okResult = apiClient.getChapterContent(ch.url)
                    if (okResult.isSuccess) {
                        val okContent = okResult.getOrNull()
                        if (okContent != null && (okContent.paragraphs.isNotEmpty() || okContent.imageUrls.isNotEmpty())) {
                            chResult = okResult
                            break
                        }
                    }

                    if (attempt < 3) {
                        onProgress(pPercent, "章节【${ch.title}】正在重试 ($attempt/3)...")
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }

                if (chResult == null || chResult.isFailure) {
                    val err = chResult?.exceptionOrNull()
                    val msg = if (err?.message == "CLOUDFLARE_CHALLENGE") {
                        "抓取章节【${ch.title}】时触发了人机验证。为保证全书完整无遗漏，已停止生成。请在右上角打开网页完成验证后再试。"
                    } else {
                        "抓取章节【${ch.title}】失败: ${err?.message ?: "内容为空"}。为保证全书完整无遗漏，已停止生成。"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val content = chResult.getOrThrow()
                val paragraphs = content.paragraphs.ifEmpty { listOf("章节内容获取为空") }
                val imageUrls = content.imageUrls

                // If this is an illustration chapter or first chapter with images, record first image as volume cover
                if (imageUrls.isNotEmpty()) {
                    if (volumeFirstImageUrl == null || ch.title.contains("插图") || ch.title.contains("插圖") || ch.title.contains("彩页")) {
                        volumeFirstImageUrl = imageUrls.first()
                    }
                }

                allImageUrls.addAll(imageUrls)

                // Join paragraphs cleanly
                val body = paragraphs.joinToString("\n")

                noveliaChapters.add(
                    NoveliaChapter(
                        id = ch.id,
                        chapterIndex = idx + 1,
                        title = ch.title.ifEmpty { "第 ${idx + 1} 章" },
                        content = body
                    )
                )
            }

            // Download in-chapter illustrations
            val illustrationsMap = mutableMapOf<String, ByteArray>()
            var volumeCoverBytes: ByteArray? = null

            if (allImageUrls.isNotEmpty()) {
                val totalImages = allImageUrls.size
                for ((imgIdx, imgUrl) in allImageUrls.withIndex()) {
                    val pPercent = 0.65f + (imgIdx.toFloat() / totalImages.toFloat()) * 0.15f
                    val imgFilename = imgUrl.substringAfterLast("/").trim()
                    onProgress(pPercent, "正在下载分卷插图 (${imgIdx + 1}/$totalImages)...")

                    val bytes = apiClient.downloadImage(imgUrl)
                    if (bytes != null && bytes.isNotEmpty() && imgFilename.isNotBlank()) {
                        illustrationsMap[imgFilename] = bytes
                        if (imgUrl == volumeFirstImageUrl && volumeCoverBytes == null) {
                            volumeCoverBytes = bytes
                        }
                    }
                }
            }

            // If volume cover image was not found from illustrations, fallback to series cover
            if (volumeCoverBytes == null || volumeCoverBytes.isEmpty()) {
                onProgress(0.80f, "正在准备分卷封面图...")
                if (novel.coverUrl.isNotBlank() && !novel.coverUrl.endsWith(".svg", ignoreCase = true)) {
                    volumeCoverBytes = apiClient.downloadImage(novel.coverUrl)
                }
                if (volumeCoverBytes == null || volumeCoverBytes.isEmpty()) {
                    val prefix = novel.id.toIntOrNull()?.let { it / 1000 } ?: 0
                    val fallbackUrl = "${LinovelibApiClient.BASE_URL}/files/article/image/$prefix/${novel.id}/${novel.id}s.jpg"
                    volumeCoverBytes = apiClient.downloadImage(fallbackUrl)
                }
            }

            // Save volume-specific cover image to local app storage for bookshelf display
            var localCoverPath: String? = null
            if (volumeCoverBytes != null && volumeCoverBytes.isNotEmpty()) {
                try {
                    val coverDir = File(context.filesDir, "covers")
                    if (!coverDir.exists()) coverDir.mkdirs()
                    val coverFile = File(coverDir, "cover_${novel.id}_vol_${volume.volumeIndex}_${UUID.randomUUID().toString().take(6)}.jpg")
                    coverFile.writeBytes(volumeCoverBytes)
                    localCoverPath = coverFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Build EPUB file
            onProgress(0.82f, "正在打包生成 EPUB 电子书...")
            val tempEpubFile = File(context.cacheDir, "linovelib_${UUID.randomUUID()}.epub")

            val metadata = EpubBuilder.EpubMetadata(
                title = fullBookTitle,
                author = novel.author,
                description = novel.description,
                coverImageBytes = volumeCoverBytes,
                illustrations = illustrationsMap,
                publisher = "哔哩轻小说 (tw.linovelib.com)",
                tags = listOf("哔哩轻小说", novel.category)
            )

            val buildSuccess = EpubBuilder.buildEpub(
                metadata = metadata,
                chapters = noveliaChapters,
                outputFile = tempEpubFile,
                onProgress = { p ->
                    onProgress(0.82f + p * 0.10f, "正在封装 EPUB: ${(p * 100).toInt()}%")
                }
            )

            if (!buildSuccess || !tempEpubFile.exists() || tempEpubFile.length() == 0L) {
                tempEpubFile.delete()
                return@withContext Result.failure(Exception("EPUB 生成失败"))
            }

            // Upload to WebDAV
            onProgress(0.93f, "正在上传至 WebDAV 云端...")
            val uploadSuccess = webDavClient.uploadFile(tempEpubFile, remoteFilePath)
            tempEpubFile.delete()

            if (!uploadSuccess) {
                return@withContext Result.failure(Exception("上传至 WebDAV 失败，请检查网络或存储空间"))
            }

            // Register to local Bookshelf (BookEntity)
            onProgress(0.98f, "正在录入书架...")
            val existingBook = bookDao.getAllBooksList().firstOrNull {
                it.filePath == remoteFilePath
            }

            if (existingBook != null) {
                bookDao.updateBook(
                    existingBook.copy(
                        title = fullBookTitle,
                        author = novel.author,
                        seriesName = cleanSeriesTitle,
                        coverImage = localCoverPath ?: existingBook.coverImage,
                        isWebDav = true,
                        lastReadTime = System.currentTimeMillis()
                    )
                )
            } else {
                val newBook = BookEntity(
                    id = 0,
                    title = fullBookTitle,
                    author = novel.author,
                    coverImage = localCoverPath,
                    filePath = remoteFilePath,
                    isWebDav = true,
                    seriesName = cleanSeriesTitle,
                    addedTime = System.currentTimeMillis(),
                    lastReadTime = System.currentTimeMillis()
                )
                bookDao.insertBook(newBook)
            }

            onProgress(1.0f, "完成！已成功打包并上传至 WebDAV")
            Result.success(remoteFilePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
