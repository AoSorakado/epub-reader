package com.example.epubreader.ui.reader

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import androidx.compose.ui.text.AnnotatedString
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
    data class ImageNode(val imageData: ByteArray) : ChapterNode()
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

    private fun recomputeCharacterCounts(items: List<FlatReaderItem>) {
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
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    // Preferences for themes (0 = White, 1 = Sepia, 2 = Dark)
    private val _themeIndex = MutableStateFlow(prefs.getInt("themeIndex", 0))
    val themeIndex: StateFlow<Int> = _themeIndex.asStateFlow()

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
            
            val book = dao.getBookById(bookId)
            _bookEntity.value = book
            
            if (book == null) {
                _isLoading.value = false
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val inputStream: InputStream = if (book.isWebDav) {
                        val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
                        val url = prefs.getString("webdav_url", "") ?: ""
                        val user = prefs.getString("webdav_user", "") ?: ""
                        val pass = prefs.getString("webdav_pass", "") ?: ""
                        val client = WebDavClient(url, user, pass)
                        
                        val cacheFile = File(context.cacheDir, "webdav_${book.id}.epub")
                        if (!cacheFile.exists()) {
                            client.downloadFile(book.filePath, cacheFile)
                        }
                        FileInputStream(cacheFile)
                    } else {
                        if (book.filePath.startsWith("content://")) {
                            context.contentResolver.openInputStream(Uri.parse(book.filePath)) 
                                ?: throw Exception("Cannot open content URI")
                        } else {
                            FileInputStream(File(book.filePath))
                        }
                    }

                    // Parse EPUB Structure
                    val parsedBook = EpubParser.parse(inputStream)
                    _epubBook.value = parsedBook
                    _toc.value = parsedBook.toc

                    val totalChapters = parsedBook.chapters.size
                    val resultList = mutableListOf<ParsedChapter>()
                    
                    val imageRegex = Regex("<(?:img|image|object)[^>]*?(?:src|href|data)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>", RegexOption.IGNORE_CASE)
                    
                    for ((index, chapter) in parsedBook.chapters.withIndex()) {
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
                        
                        resultList.add(ParsedChapter(chapter.title, nodes.toList()))
                        
                        _loadingProgress.value = (index + 1).toFloat() / totalChapters
                        
                        // Emit partial results every 5 chapters to show UI quickly
                        if (index % 5 == 0 || index == totalChapters - 1) {
                            val parsedList = resultList.toList()
                            _parsedChapters.value = parsedList
                            val flattened = parsedList.flatMapIndexed { cIdx, ch ->
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
                            recomputeCharacterCounts(flattened)
                        }
                    }
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
        flushReadingDuration()
    }

    fun saveProgress(flatIndex: Int, offset: Int) {
        flushReadingDuration()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val book = _bookEntity.value ?: return@launch
            val flatItem = _flatItems.value.getOrNull(flatIndex) ?: return@launch
            val chapterIndex = flatItem.chapterIndex
            val nodeIndex = if (flatItem is FlatReaderItem.Node) flatItem.nodeIndex else 0
            
            val newPosition = "${chapterIndex}_${offset}_${nodeIndex}"
            
            // Calculate total progress (approximate)
            val totalNodes = _parsedChapters.value.sumOf { it.nodes.size }
            val currentNodes = _parsedChapters.value.take(chapterIndex).sumOf { it.nodes.size } + nodeIndex
            val progress = if (totalNodes > 0) currentNodes.toFloat() / totalNodes else 0f

            val isFirstTimeFinished = (progress >= 0.99f && (book.totalProgress < 0.99f))
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
