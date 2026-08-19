package com.example.epubreader.data.novelia.sync

import android.content.Context
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.epub.builder.EpubBuilder
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.data.novelia.NoveliaApiClient
import com.example.epubreader.data.novelia.NoveliaChapter
import com.example.epubreader.data.novelia.NoveliaVolume
import com.example.epubreader.data.novelia.NoveliaWebNovel
import com.example.epubreader.data.novelia.NoveliaWenkuNovel
import com.example.epubreader.data.novelia.TranslationEngine
import com.example.epubreader.data.parser.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NoveliaWebDavExporter(
    private val context: Context,
    private val apiClient: NoveliaApiClient
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
     * Download a Wenku volume into memory/temp, upload to WebDAV, clean up local cache, and add to Bookshelf as cloud book.
     */
    suspend fun exportWenkuVolume(
        novel: NoveliaWenkuNovel,
        volume: NoveliaVolume,
        engine: TranslationEngine = TranslationEngine.SAKURA,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val webDavClient = getWebDavClient()
            ?: return@withContext Result.failure(Exception("请先在「配置」页面配置 WebDAV 服务器"))

        try {
            val authorPart = if (novel.author.isNotBlank() && novel.author != "未知作者") "[${truncateUtf8(sanitizeFilename(novel.author), 30)}] " else ""
            val cleanSeriesTitle = truncateUtf8(sanitizeFilename(novel.title), 50)
            val seriesFolder = "Novels/文库小说/$authorPart$cleanSeriesTitle"
            val downloadUrl = volume.engineDownloadUrls[engine] 
                ?: volume.defaultDownloadUrl.ifEmpty { "/api/wenku/${novel.id}/file/${volume.id}?engine=${engine.code}" }

            val volIndexStr = "第${volume.volumeIndex}卷"
            val rawVolName = volume.volumeName.replace(".epub", "", ignoreCase = true).trim()
            val cleanVolName = truncateUtf8(sanitizeFilename(rawVolName), 40)
            // Ensure volume index is ALWAYS in the filename to prevent collision/overwriting
            val remoteFilename = if (cleanVolName.contains(volIndexStr) || cleanVolName.contains("${volume.volumeIndex}")) {
                "$cleanVolName [${engine.displayName}].epub"
            } else {
                "${volIndexStr}_$cleanVolName [${engine.displayName}].epub"
            }
            val remoteFilePath = "$seriesFolder/$remoteFilename"

            onProgress(0.05f, "正在连接 Novelia 下载 ${volIndexStr}...")

            // Download into temporary cache file
            val tempDir = File(context.cacheDir, "novelia_temp").apply { mkdirs() }
            val tempFile = File(tempDir, "temp_${novel.id}_vol${volume.volumeIndex}_${System.currentTimeMillis()}.epub")

            val downloadOk = apiClient.downloadWenkuEpub(downloadUrl, tempFile) { p ->
                onProgress(0.05f + p * 0.45f, "正在从 Novelia 下载: ${(p * 100).toInt()}%")
            }

            if (!downloadOk || !tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("从 Novelia 下载 EPUB 文件失败"))
            }

            // Extract Cover for Bookshelf UI
            onProgress(0.52f, "正在提取封面与元数据...")
            var coverImagePath: String? = null
            try {
                tempFile.inputStream().use { input ->
                    val coverBytes = EpubParser.extractCoverOnly(input)
                    if (coverBytes != null) {
                        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                        coverFile.writeBytes(coverBytes)
                        coverImagePath = coverFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback to online cover if extract returned null
            if (coverImagePath == null && novel.coverUrl.isNotBlank()) {
                val coverBytes = apiClient.fetchCoverImage(novel.coverUrl)
                if (coverBytes != null) {
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                    coverFile.writeBytes(coverBytes)
                    coverImagePath = coverFile.absolutePath
                }
            }

            // Upload directly to WebDAV series folder
            onProgress(0.60f, "正在创建 WebDAV 系列目录...")
            ensureRemoteDirectory(webDavClient, seriesFolder)

            onProgress(0.65f, "正在上传至 WebDAV 云端...")
            val uploadOk = webDavClient.uploadFile(tempFile, remoteFilePath) { p ->
                onProgress(0.65f + p * 0.33f, "正在上传到 WebDAV: ${(p * 100).toInt()}%")
            }

            // Delete temporary local file immediately after upload
            tempFile.delete()

            if (!uploadOk) {
                return@withContext Result.failure(Exception("上传至 WebDAV 失败，请检查网络或空间"))
            }

            // Register in BookDao as Cloud Book (isWebDav = true, filePath = remoteFilePath)
            val bookTitle = "${novel.title} $volIndexStr"
            val existingBook = bookDao.getAllBooksList().firstOrNull {
                it.filePath == remoteFilePath || it.title.trim().equals(bookTitle.trim(), ignoreCase = true)
            }

            if (existingBook != null) {
                bookDao.updateBook(
                    existingBook.copy(
                        title = bookTitle,
                        filePath = remoteFilePath,
                        coverImage = coverImagePath ?: existingBook.coverImage,
                        isWebDav = true,
                        seriesName = novel.title
                    )
                )
            } else {
                val bookEntity = BookEntity(
                    title = bookTitle,
                    author = novel.author.ifEmpty { "未知作者" },
                    coverImage = coverImagePath,
                    filePath = remoteFilePath,
                    isWebDav = true,
                    seriesName = novel.title,
                    addedTime = System.currentTimeMillis(),
                    lastReadTime = 0
                )
                bookDao.insertBook(bookEntity)
            }

            onProgress(1.0f, "已成功保存到 WebDAV 云端与书架！")
            Result.success(remoteFilePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download chapters for a Web Novel, build EPUB in temp cache, upload to WebDAV, clean local cache, add to Bookshelf.
     */
    suspend fun exportWebNovel(
        novel: NoveliaWebNovel,
        chapters: List<NoveliaChapter>,
        engine: TranslationEngine = TranslationEngine.SAKURA,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val webDavClient = getWebDavClient()
            ?: return@withContext Result.failure(Exception("请先在「配置」页面配置 WebDAV 服务器"))

        if (chapters.isEmpty()) {
            return@withContext Result.failure(Exception("没有可打包的章节"))
        }

        try {
            val sourcePrefix = if (novel.sourcePlatform.isNotBlank()) "[${truncateUtf8(sanitizeFilename(novel.sourcePlatform), 20)}] " else ""
            val cleanTitle = truncateUtf8(sanitizeFilename(novel.title), 50)
            val seriesFolder = "Novels/网络小说/$sourcePrefix$cleanTitle"
            val remoteFilename = "$cleanTitle [${engine.displayName}].epub"
            val remoteFilePath = "$seriesFolder/$remoteFilename"

            onProgress(0.05f, "正在获取封面与元数据...")
            val coverBytes = if (novel.coverUrl.isNotBlank()) {
                apiClient.fetchCoverImage(novel.coverUrl)
            } else null

            var coverImagePath: String? = null
            if (coverBytes != null) {
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                coverFile.writeBytes(coverBytes)
                coverImagePath = coverFile.absolutePath
            }

            onProgress(0.15f, "正在打包生成标准 EPUB (${chapters.size} 章)...")

            val tempDir = File(context.cacheDir, "novelia_temp").apply { mkdirs() }
            val tempFile = File(tempDir, "temp_web_${novel.id}_${System.currentTimeMillis()}.epub")

            val metadata = EpubBuilder.EpubMetadata(
                title = novel.title,
                author = novel.author.ifEmpty { "未知作者" },
                description = novel.description,
                coverImageBytes = coverBytes,
                tags = novel.tags
            )

            val buildOk = EpubBuilder.buildEpub(metadata, chapters, tempFile) { p ->
                onProgress(0.15f + p * 0.40f, "正在生成 EPUB 文件: ${(p * 100).toInt()}%")
            }

            if (!buildOk || !tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("生成 EPUB 电子书失败"))
            }

            // Upload directly to WebDAV series folder
            onProgress(0.60f, "正在创建 WebDAV 系列目录...")
            ensureRemoteDirectory(webDavClient, seriesFolder)

            onProgress(0.65f, "正在同步上传至 WebDAV 云端...")
            val uploadOk = webDavClient.uploadFile(tempFile, remoteFilePath) { p ->
                onProgress(0.65f + p * 0.33f, "正在上传到 WebDAV: ${(p * 100).toInt()}%")
            }

            // Delete temporary local file immediately after upload
            tempFile.delete()

            if (!uploadOk) {
                return@withContext Result.failure(Exception("上传至 WebDAV 失败，请检查网络或空间"))
            }

            // Register in BookDao as Cloud Book (isWebDav = true, filePath = remoteFilePath)
            val existingBook = bookDao.getAllBooksList().firstOrNull {
                it.filePath == remoteFilePath || it.title.trim().equals(novel.title.trim(), ignoreCase = true)
            }

            if (existingBook != null) {
                bookDao.updateBook(
                    existingBook.copy(
                        title = novel.title,
                        filePath = remoteFilePath,
                        coverImage = coverImagePath ?: existingBook.coverImage,
                        isWebDav = true,
                        seriesName = novel.title
                    )
                )
            } else {
                val bookEntity = BookEntity(
                    title = novel.title,
                    author = novel.author.ifEmpty { "未知作者" },
                    coverImage = coverImagePath,
                    filePath = remoteFilePath,
                    isWebDav = true,
                    seriesName = novel.title,
                    addedTime = System.currentTimeMillis(),
                    lastReadTime = 0
                )
                bookDao.insertBook(bookEntity)
            }

            onProgress(1.0f, "打包完成，已归档到 WebDAV 云端与书架！")
            Result.success(remoteFilePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
