package com.example.epubreader.ui.reader

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.BookDao
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.data.parser.EpubBook
import com.example.epubreader.data.parser.EpubParser
import com.example.epubreader.data.parser.HtmlToAnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Calendar
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.ReadingStatEntity

sealed class ChapterNode {
    data class TextNode(val text: AnnotatedString) : ChapterNode()
    data class ImageNode(val imageData: ByteArray) : ChapterNode() {
        private var cachedBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
        val bitmap: androidx.compose.ui.graphics.ImageBitmap?
            get() {
                if (cachedBitmap != null) return cachedBitmap
                val bmp = try {
                    android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                cachedBitmap = bmp
                return bmp
            }
    }
}

data class ParsedChapter(
    val title: String,
    val nodes: List<ChapterNode>
)

sealed class FlatReaderItem {
    abstract val chapterIndex: Int
    data class Title(override val chapterIndex: Int, val title: String) : FlatReaderItem()
    data class Node(override val chapterIndex: Int, val nodeIndex: Int, val node: ChapterNode) : FlatReaderItem()
}

class ReaderViewModel(
    private val bookId: Long,
    private val dao: BookDao,
    application: Application
) : AndroidViewModel(application) {
    
    private val prefs: SharedPreferences = application.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _bookEntity = MutableStateFlow<BookEntity?>(null)
    val bookEntity: StateFlow<BookEntity?> = _bookEntity.asStateFlow()

    private val _epubBook = MutableStateFlow<EpubBook?>(null)
    val epubBook: StateFlow<EpubBook?> = _epubBook.asStateFlow()

    private val _toc = MutableStateFlow<List<com.example.epubreader.data.parser.EpubTocItem>>(emptyList())
    val toc: StateFlow<List<com.example.epubreader.data.parser.EpubTocItem>> = _toc.asStateFlow()

    private val _parsedChapters = MutableStateFlow<List<ParsedChapter>>(emptyList())
    val parsedChapters: StateFlow<List<ParsedChapter>> = _parsedChapters.asStateFlow()

    private val _flatItems = MutableStateFlow<List<FlatReaderItem>>(emptyList())
    val flatItems: StateFlow<List<FlatReaderItem>> = _flatItems.asStateFlow()

    private val _cumulativeCharCounts = MutableStateFlow<List<Int>>(emptyList())
    val cumulativeCharCounts: StateFlow<List<Int>> = _cumulativeCharCounts.asStateFlow()

    private val _totalCharCount = MutableStateFlow(0)
    val totalCharCount: StateFlow<Int> = _totalCharCount.asStateFlow()

    // Dynamic Reading Speed Tracking (CPM = Characters Per Minute, default: 450 CPM)
    private var sessionStartTime = System.currentTimeMillis()
    private var sessionStartCharIndex = 0
    private var isSessionTracking = false

    private val _readingSpeedCpm = MutableStateFlow(prefs.getFloat("readingSpeedCpm", 450f))
    val readingSpeedCpm: StateFlow<Float> = _readingSpeedCpm.asStateFlow()

    fun updateReadingPosition(itemIndex: Int) {
        val cumulative = _cumulativeCharCounts.value
        if (cumulative.isEmpty()) return
        val clampedIndex = itemIndex.coerceIn(0, cumulative.size - 1)
        val currentCharPos = cumulative[clampedIndex]

        val now = System.currentTimeMillis()
        if (!isSessionTracking) {
            sessionStartTime = now
            sessionStartCharIndex = currentCharPos
            isSessionTracking = true
            return
        }

        val elapsedMs = now - sessionStartTime
        if (elapsedMs >= 30_000L) {
            val charsRead = currentCharPos - sessionStartCharIndex
            if (charsRead in 50..10_000) {
                val currentSessionCpm = (charsRead.toFloat() / (elapsedMs.toFloat() / 60_000f)).coerceIn(150f, 1500f)
                val newCpm = (_readingSpeedCpm.value * 0.80f) + (currentSessionCpm * 0.20f)
                _readingSpeedCpm.value = newCpm
                prefs.edit().putFloat("readingSpeedCpm", newCpm).apply()
                
                sessionStartTime = now
                sessionStartCharIndex = currentCharPos
            }
        }
    }

    fun getEstimatedRemainingTimeText(currentIndex: Int): String {
        val cumulative = _cumulativeCharCounts.value
        val total = _totalCharCount.value
        if (cumulative.isEmpty() || total <= 0) return "预计计算中..."

        val clampedIndex = currentIndex.coerceIn(0, cumulative.size - 1)
        val currentCharPos = cumulative[clampedIndex]
        val remainingChars = (total - currentCharPos).coerceAtLeast(0)

        val cpm = _readingSpeedCpm.value.coerceIn(150f, 1500f)
        val remainingMinutes = kotlin.math.ceil(remainingChars.toFloat() / cpm).toInt()

        return when {
            remainingChars <= 50 || remainingMinutes <= 0 -> "预计剩余不到 1 分钟"
            remainingMinutes < 60 -> "预计剩余 $remainingMinutes 分钟"
            else -> {
                val hours = remainingMinutes / 60
                val mins = remainingMinutes % 60
                if (mins > 0) "预计剩余 ${hours}小时${mins}分" else "预计剩余 ${hours}小时"
            }
        }
    }

    private val _chapterCharCounts = MutableStateFlow<List<Int>>(emptyList())
    val chapterCharCounts: StateFlow<List<Int>> = _chapterCharCounts.asStateFlow()

    private val _chapterCumulativeCharCounts = MutableStateFlow<List<Int>>(emptyList())
    val chapterCumulativeCharCounts: StateFlow<List<Int>> = _chapterCumulativeCharCounts.asStateFlow()

    private fun recomputeCharacterCounts(items: List<FlatReaderItem>, chapters: List<ParsedChapter>) {
        val cumulative = ArrayList<Int>(items.size)
        var sum = 0
        for (item in items) {
            cumulative.add(sum)
            val count = when (item) {
                is FlatReaderItem.Title -> item.title.length
                is FlatReaderItem.Node -> {
                    when (val node = item.node) {
                        is ChapterNode.TextNode -> node.text.text.length
                        is ChapterNode.ImageNode -> 50
                    }
                }
            }
            sum += count
        }
        _cumulativeCharCounts.value = cumulative
        _totalCharCount.value = sum

        val chCounts = chapters.map { ch ->
            var c = ch.title.length
            for (node in ch.nodes) {
                c += when (node) {
                    is ChapterNode.TextNode -> node.text.text.length
                    is ChapterNode.ImageNode -> 50
                }
            }
            c.coerceAtLeast(1)
        }
        val chCum = ArrayList<Int>(chCounts.size)
        var chSum = 0
        for (cnt in chCounts) {
            chCum.add(chSum)
            chSum += cnt
        }
        _chapterCharCounts.value = chCounts
        _chapterCumulativeCharCounts.value = chCum
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val liquidPrefs: SharedPreferences = application.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)

    private fun computeEffectiveThemeIndex(): Int {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNightTime = (currentHour >= 19 || currentHour < 7)
        val autoNight = liquidPrefs.getBoolean("autoNightMode", false)
        val appThemeStr = liquidPrefs.getString("appTheme", "OCEAN_WAVE") ?: "OCEAN_WAVE"
        val isMidnight = appThemeStr == "MIDNIGHT_GLASS" || (autoNight && isNightTime)
        
        return if (isMidnight) {
            2 // 暗夜黑曜
        } else {
            prefs.getInt("userPreferredDayReadingTheme", prefs.getInt("themeIndex", 0))
        }
    }

    // Preferences for themes (0 = White, 1 = Sepia, 2 = Dark)
    private val _themeIndex = MutableStateFlow(computeEffectiveThemeIndex())
    val themeIndex: StateFlow<Int> = _themeIndex.asStateFlow()

    private val liquidPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "appTheme" || key == "autoNightMode") {
            val newTheme = computeEffectiveThemeIndex()
            _themeIndex.value = newTheme
            prefs.edit().putInt("themeIndex", newTheme).apply()
        }
    }

    init {
        liquidPrefs.registerOnSharedPreferenceChangeListener(liquidPrefsListener)
    }

    private val _textSize = MutableStateFlow(prefs.getFloat("textSize", 18f))
    val textSize: StateFlow<Float> = _textSize.asStateFlow()

    private val _lineHeightMult = MutableStateFlow(prefs.getFloat("lineHeightMult", 1.6f))
    val lineHeightMult: StateFlow<Float> = _lineHeightMult.asStateFlow()

    private val _paragraphSpacing = MutableStateFlow(prefs.getFloat("paragraphSpacing", 16f))
    val paragraphSpacing: StateFlow<Float> = _paragraphSpacing.asStateFlow()

    private val _chineseMode = MutableStateFlow(prefs.getInt("chineseMode", 0)) // 0: 原文, 1: 简体, 2: 繁体
    val chineseMode: StateFlow<Int> = _chineseMode.asStateFlow()

    private val _customFontUri = MutableStateFlow(prefs.getString("customFontUri", ""))
    val customFontUri: StateFlow<String?> = _customFontUri.asStateFlow()

    // 0: 连续滚动, 1: 左右翻页
    private val _pageTurnMode = MutableStateFlow(prefs.getInt("pageTurnMode", 0))
    val pageTurnMode: StateFlow<Int> = _pageTurnMode.asStateFlow()

    // 0: 仿真, 1: 平移, 2: 覆盖, 3: 淡入, 4: 无动画
    private val _pageAnimStyle = MutableStateFlow(prefs.getInt("pageAnimStyle", 0))
    val pageAnimStyle: StateFlow<Int> = _pageAnimStyle.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    fun setPageTurnMode(mode: Int) {
        _pageTurnMode.value = mode
        prefs.edit().putInt("pageTurnMode", mode).apply()
    }

    fun setPageAnimStyle(style: Int) {
        _pageAnimStyle.value = style
        prefs.edit().putInt("pageAnimStyle", style).apply()
    }

    fun setCurrentPageIndex(index: Int) {
        _currentPageIndex.value = index
    }

    fun setTheme(index: Int) {
        _themeIndex.value = index
        prefs.edit().putInt("themeIndex", index).apply()
        if (index != 2) {
            prefs.edit().putInt("userPreferredDayReadingTheme", index).apply()
        }
    }

    fun setTextSize(size: Float) {
        val rounded = kotlin.math.round(size * 2f) / 2f
        if (_textSize.value != rounded) {
            _textSize.value = rounded
            prefs.edit().putFloat("textSize", rounded).apply()
        }
    }

    fun setLineHeightMult(mult: Float) {
        val rounded = kotlin.math.round(mult * 20f) / 20f
        if (_lineHeightMult.value != rounded) {
            _lineHeightMult.value = rounded
            prefs.edit().putFloat("lineHeightMult", rounded).apply()
        }
    }

    fun setParagraphSpacing(spacing: Float) {
        val rounded = kotlin.math.round(spacing)
        if (_paragraphSpacing.value != rounded) {
            _paragraphSpacing.value = rounded
            prefs.edit().putFloat("paragraphSpacing", rounded).apply()
        }
    }

    fun setChineseMode(mode: Int) {
        _chineseMode.value = mode
        prefs.edit().putInt("chineseMode", mode).apply()
    }

    fun setCustomFontUri(uri: String?) {
        _customFontUri.value = uri
        prefs.edit().putString("customFontUri", uri).apply()
    }
    
    private fun resolvePath(basePath: String, relativePath: String): String {
        val decodedRelative = android.net.Uri.decode(relativePath)
        if (decodedRelative.startsWith("/")) return decodedRelative.substring(1)
        
        val baseDir = if (basePath.contains("/")) basePath.substringBeforeLast("/") else ""
        if (baseDir.isEmpty()) return decodedRelative
        
        val baseParts = baseDir.split("/").toMutableList()
        val relParts = decodedRelative.split("/")
        
        for (part in relParts) {
            if (part == "..") {
                if (baseParts.isNotEmpty()) baseParts.removeLast()
            } else if (part != ".") {
                baseParts.add(part)
            }
        }
        return baseParts.joinToString("/")
    }

    fun loadBook(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            
            var book = dao.getBookById(bookId)
            _bookEntity.value = book
            
            if (book == null) {
                _isLoading.value = false
                return@launch
            }

            // 1. Asynchronously check and sync progress in background without blocking book open
            if (book.isWebDav) {
                viewModelScope.launch(Dispatchers.IO) {
                    val synced = syncProgressFromCloud(context, book)
                    _bookEntity.value = synced
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    val isTxt = book.filePath.endsWith(".txt", ignoreCase = true)
                    val parsedBook: EpubBook = if (book.isWebDav) {
                        val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
                        val url = prefs.getString("webdav_url", "") ?: ""
                        val user = prefs.getString("webdav_user", "") ?: ""
                        val pass = prefs.getString("webdav_pass", "") ?: ""
                        val client = WebDavClient(url, user, pass)
                        
                        val cacheFile = File(context.cacheDir, "webdav_${book.id}.${if (isTxt) "txt" else "epub"}")
                        if (!cacheFile.exists()) {
                            client.downloadFile(book.filePath, cacheFile)
                        }
                        if (isTxt) com.example.epubreader.data.parser.TxtParser.parse(cacheFile) else EpubParser.parse(cacheFile)
                    } else if (book.filePath.startsWith("content://")) {
                        val cacheFile = File(context.cacheDir, "content_${book.id}.${if (isTxt) "txt" else "epub"}")
                        if (!cacheFile.exists() || cacheFile.length() == 0L) {
                            context.contentResolver.openInputStream(Uri.parse(book.filePath))?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        if (isTxt) com.example.epubreader.data.parser.TxtParser.parse(cacheFile) else EpubParser.parse(cacheFile)
                    } else {
                        val file = File(book.filePath)
                        if (file.exists()) {
                            if (isTxt) com.example.epubreader.data.parser.TxtParser.parse(file) else EpubParser.parse(file)
                        } else {
                            context.contentResolver.openInputStream(Uri.parse(book.filePath))?.use { input ->
                                val tempFile = File(context.cacheDir, "temp_${book.id}.${if (isTxt) "txt" else "epub"}")
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                                if (isTxt) com.example.epubreader.data.parser.TxtParser.parse(tempFile) else EpubParser.parse(tempFile)
                            } ?: throw Exception("Cannot open book file: ${book.filePath}")
                        }
                    }

                    _epubBook.value = parsedBook
                    _toc.value = parsedBook.toc

                    // Multi-core parallel chapter parsing
                    val imageRegex = Regex("<(?:img|image|object)[^>]*?(?:src|href|data)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>", RegexOption.IGNORE_CASE)
                    
                    val parsedChapters = withContext(Dispatchers.Default) {
                        parsedBook.chapters.mapIndexed { index, chapter ->
                            async {
                                val nodes = mutableListOf<ChapterNode>()
                                var lastIndex = 0
                                
                                val matches = imageRegex.findAll(chapter.content)
                                for (match in matches) {
                                    // Extract text before image
                                    val textBefore = chapter.content.substring(lastIndex, match.range.first)
                                    if (textBefore.isNotBlank()) {
                                        val parsed = HtmlToAnnotatedString.parse(textBefore)
                                        splitAndAddTextNodes(parsed, nodes)
                                    }
                                    
                                    // Extract image
                                    val src = match.groupValues[1]
                                    val absoluteImagePath = resolvePath(chapter.href, src)
                                    val imageData = parsedBook.images[absoluteImagePath.lowercase()]
                                    if (imageData != null) {
                                        nodes.add(ChapterNode.ImageNode(imageData))
                                    } else {
                                        val available = parsedBook.images.keys.take(10).joinToString(", ")
                                        nodes.add(ChapterNode.TextNode(HtmlToAnnotatedString.parse("[Image missing: $absoluteImagePath] (Keys: $available)")))
                                    }
                                    
                                    lastIndex = match.range.last + 1
                                }
                                
                                // Extract remaining text
                                if (lastIndex < chapter.content.length) {
                                    val remainingText = chapter.content.substring(lastIndex)
                                    if (remainingText.isNotBlank()) {
                                        val parsed = HtmlToAnnotatedString.parse(remainingText)
                                        splitAndAddTextNodes(parsed, nodes)
                                    }
                                }
                                
                                // Fallback debug information if the chapter is completely empty but has HTML content
                                if (nodes.isEmpty() && chapter.content.isNotBlank()) {
                                    val debugContent = if (chapter.content.length > 200) chapter.content.take(200) + "..." else chapter.content
                                    nodes.add(ChapterNode.TextNode(androidx.compose.ui.text.AnnotatedString("[Unrecognized Content]\n$debugContent")))
                                }
                                
                                ParsedChapter(chapter.title, nodes.toList())
                            }
                        }.awaitAll()
                    }

                    _parsedChapters.value = parsedChapters
                    val flattened = parsedChapters.flatMapIndexed { cIdx, ch ->
                        val list = mutableListOf<FlatReaderItem>()
                        if (ch.title.isNotBlank() && ch.title != "Chapter") {
                            list.add(FlatReaderItem.Title(cIdx, ch.title))
                        }
                        ch.nodes.forEachIndexed { nIdx, n ->
                            list.add(FlatReaderItem.Node(cIdx, nIdx, n))
                        }
                        list
                    }
                    _flatItems.value = flattened
                    recomputeCharacterCounts(flattened, parsedChapters)
                    _loadingProgress.value = 1f
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun splitAndAddTextNodes(parsed: AnnotatedString, nodes: MutableList<ChapterNode>) {
        val textStr = parsed.text
        var startIndex = 0
        while (startIndex < textStr.length) {
            var endIndex = textStr.indexOf('\n', startIndex)
            if (endIndex == -1) endIndex = textStr.length
            
            val subText = parsed.subSequence(startIndex, endIndex)
            if (subText.text.isNotBlank()) {
                nodes.add(ChapterNode.TextNode(subText))
            }
            startIndex = endIndex + 1
        }
    }

    private var lastRecordedTime = System.currentTimeMillis()

    fun flushReadingDuration() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRecordedTime
        if (elapsed >= 1000L) {
            lastRecordedTime = now
            val statDao = AppDatabase.getDatabase(getApplication()).statDao()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = calendar.timeInMillis

            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    val existing = statDao.getStatForDay(todayStart)
                    if (existing != null) {
                        statDao.insertOrUpdate(existing.copy(readDurationMs = existing.readDurationMs + elapsed))
                    } else {
                        statDao.insertOrUpdate(
                            ReadingStatEntity(
                                date = todayStart,
                                readDurationMs = elapsed,
                                wordsRead = 0
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liquidPrefs.unregisterOnSharedPreferenceChangeListener(liquidPrefsListener)
        flushReadingDuration()
    }

    fun saveProgress(chapterIndex: Int, offset: Int, nodeIndex: Int = 0, progressOverride: Float? = null) {
        flushReadingDuration()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val book = _bookEntity.value ?: return@launch
            val newPosition = "${chapterIndex}_${offset}_${nodeIndex}"
            
            // Calculate total progress (accurate chapter + node calculation or continuous override)
            val totalChapters = _parsedChapters.value.size.coerceAtLeast(1)
            val totalNodes = _parsedChapters.value.sumOf { it.nodes.size }.coerceAtLeast(1)
            val currentNodes = _parsedChapters.value.take(chapterIndex).sumOf { it.nodes.size } + nodeIndex
            val defaultProgress = (currentNodes.toFloat() / totalNodes.toFloat()).coerceIn(0f, 1f)
            val progress = progressOverride?.coerceIn(0f, 1f) ?: defaultProgress

            val isFirstTimeFinished = (progress >= 0.999f && (book.totalProgress < 0.999f))
            val updatedBook = book.copy(
                lastReadPosition = newPosition,
                lastReadTime = System.currentTimeMillis(),
                totalProgress = progress
            )
            dao.updateBook(updatedBook)
            _bookEntity.value = updatedBook

            if (isFirstTimeFinished) {
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = "🎉 恭喜！您已读完《${book.title}》！",
                    type = com.example.epubreader.ui.components.toast.ToastType.Success,
                    durationMs = 4000L
                )
            }
        }
    }

    private suspend fun syncProgressFromCloud(context: Context, currentBook: BookEntity): BookEntity {
        val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
        val url = prefs.getString("webdav_url", "") ?: ""
        val user = prefs.getString("webdav_user", "") ?: ""
        val pass = prefs.getString("webdav_pass", "") ?: ""

        if (url.isBlank()) return currentBook

        withContext(Dispatchers.Main) {
            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                text = "☁️ 正在从云端同步最新阅读进度...",
                type = com.example.epubreader.ui.components.toast.ToastType.Info,
                durationMs = 2500L
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = WebDavClient(url, user, pass)
                val remoteJsonStr = client.getTextFile("/.epub_reader/progress_sync.json")
                    ?: client.getTextFile("/epub_reader_progress.json")

                if (!remoteJsonStr.isNullOrBlank()) {
                    val rootJson = org.json.JSONObject(remoteJsonStr)
                    val itemsArray = rootJson.optJSONArray("items")
                    if (itemsArray != null) {
                        for (i in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(i)
                            val filePath = itemObj.optString("filePath", "")
                            val title = itemObj.optString("title", "")
                            
                            val matches = (filePath.isNotBlank() && filePath == currentBook.filePath) ||
                                          (title.isNotBlank() && title == currentBook.title)
                            
                            if (matches) {
                                val remoteLastReadTime = itemObj.optLong("lastReadTime", 0L)
                                val remotePos = itemObj.optString("lastReadPosition", "")
                                val remoteProgress = itemObj.optDouble("totalProgress", 0.0).toFloat()

                                if (remoteLastReadTime > currentBook.lastReadTime && remotePos.isNotBlank()) {
                                    val updatedBook = currentBook.copy(
                                        lastReadPosition = remotePos,
                                        totalProgress = remoteProgress,
                                        lastReadTime = remoteLastReadTime
                                    )
                                    dao.updateBook(updatedBook)
                                    withContext(Dispatchers.Main) {
                                        com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                                            text = "☁️ 已从云端同步最新阅读进度 (${(remoteProgress * 100).toInt()}%)",
                                            type = com.example.epubreader.ui.components.toast.ToastType.Success,
                                            durationMs = 3000L
                                        )
                                    }
                                    return@withContext updatedBook
                                } else {
                                    withContext(Dispatchers.Main) {
                                        com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                                            text = "☁️ 云端进度已同步，当前为最新进度",
                                            type = com.example.epubreader.ui.components.toast.ToastType.Success,
                                            durationMs = 2500L
                                        )
                                    }
                                    return@withContext currentBook
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "☁️ 云端进度检查完毕，当前为最新进度",
                        type = com.example.epubreader.ui.components.toast.ToastType.Success,
                        durationMs = 2500L
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "☁️ 云端进度同步失败: ${e.localizedMessage ?: "连接超时"}",
                        type = com.example.epubreader.ui.components.toast.ToastType.Error,
                        durationMs = 3000L
                    )
                }
            }
            currentBook
        }
    }

    private var hasUploadedOnClose = false

    fun uploadProgressToCloud(context: Context) {
        if (hasUploadedOnClose) return
        hasUploadedOnClose = true

        val book = _bookEntity.value ?: return
        val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
        val url = prefs.getString("webdav_url", "") ?: ""
        val user = prefs.getString("webdav_user", "") ?: ""
        val pass = prefs.getString("webdav_pass", "") ?: ""

        if (url.isBlank()) return

        com.example.epubreader.ui.components.toast.GlobalToastManager.show(
            text = "☁️ 正在上传最新阅读进度至云端...",
            type = com.example.epubreader.ui.components.toast.ToastType.Info,
            durationMs = 2500L
        )

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val client = WebDavClient(url, user, pass)
                val bookDao = AppDatabase.getDatabase(context).bookDao()
                val latestBook = bookDao.getBookById(book.id) ?: book

                if (latestBook.lastReadPosition.isNullOrBlank()) return@launch

                // Fetch remote json to merge
                try {
                    client.createDirectory("/.epub_reader")
                } catch (ignored: Exception) {}

                val remoteJsonStr = client.getTextFile("/.epub_reader/progress_sync.json")
                    ?: client.getTextFile("/epub_reader_progress.json")

                val remoteItemsMap = mutableMapOf<String, org.json.JSONObject>()
                if (!remoteJsonStr.isNullOrBlank()) {
                    try {
                        val rootJson = org.json.JSONObject(remoteJsonStr)
                        val itemsArray = rootJson.optJSONArray("items")
                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                val itemObj = itemsArray.getJSONObject(i)
                                val filePath = itemObj.optString("filePath", "")
                                val title = itemObj.optString("title", "")
                                val key = if (filePath.isNotBlank()) filePath else title
                                if (key.isNotBlank()) {
                                    remoteItemsMap[key] = itemObj
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Update current book in remote items
                val currentBookObj = org.json.JSONObject().apply {
                    put("title", latestBook.title)
                    put("author", latestBook.author)
                    put("filePath", latestBook.filePath)
                    put("fileName", latestBook.filePath.substringAfterLast("/"))
                    put("lastReadPosition", latestBook.lastReadPosition ?: "")
                    put("totalProgress", latestBook.totalProgress.toDouble())
                    put("lastReadTime", latestBook.lastReadTime)
                }

                val keyByPath = latestBook.filePath
                val keyByTitle = latestBook.title
                val finalKey = if (keyByPath.isNotBlank()) keyByPath else keyByTitle
                remoteItemsMap[finalKey] = currentBookObj

                val mergedArray = org.json.JSONArray()
                for ((_, obj) in remoteItemsMap) {
                    mergedArray.put(obj)
                }

                val outputJson = org.json.JSONObject().apply {
                    put("version", 1)
                    put("lastSyncTime", System.currentTimeMillis())
                    put("items", mergedArray)
                }

                val jsonContent = outputJson.toString(2)
                var (uploadSuccess, uploadError) = client.uploadTextFile("/epub_reader_progress.json", jsonContent)
                if (!uploadSuccess) {
                    val res2 = client.uploadTextFile("/.epub_reader/progress_sync.json", jsonContent)
                    uploadSuccess = res2.first
                    uploadError = res2.second
                }

                withContext(Dispatchers.Main) {
                    if (uploadSuccess) {
                        com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                            text = "☁️ 阅读进度已成功上传至云端",
                            type = com.example.epubreader.ui.components.toast.ToastType.Success,
                            durationMs = 3000L
                        )
                    } else {
                        com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                            text = "☁️ 上传云端进度失败: ${uploadError ?: "网络异常"}",
                            type = com.example.epubreader.ui.components.toast.ToastType.Error,
                            durationMs = 3500L
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "☁️ 上传云端进度失败: ${e.localizedMessage ?: "连接失败"}",
                        type = com.example.epubreader.ui.components.toast.ToastType.Error,
                        durationMs = 3500L
                    )
                }
            }
        }
    }
}

class ReaderViewModelFactory(
    private val bookId: Long,
    private val dao: BookDao,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(bookId, dao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
