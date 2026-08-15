package com.example.epubreader.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

sealed class PageElement {
    data class Title(val title: String) : PageElement()
    data class Paragraph(val text: AnnotatedString, val isContinuation: Boolean = false) : PageElement()
    data class Image(
        val imageData: ByteArray,
        val intrinsicWidth: Int = 1,
        val intrinsicHeight: Int = 1
    ) : PageElement() {
        private var cachedBitmap: ImageBitmap? = null
        val bitmap: ImageBitmap?
            get() {
                if (cachedBitmap != null) return cachedBitmap
                val bmp = try {
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                cachedBitmap = bmp
                return bmp
            }
    }
}

data class ReaderPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val flatItemIndex: Int,
    val elements: List<PageElement>,
    val pageInChapter: Int = 1,
    val totalPagesInChapter: Int = 1
)

object ReaderPagination {

    /**
     * Ultra high-speed parallel pagination engine across all CPU cores.
     * Uses zero-overhead image bounds inspection without decoding bitmaps,
     * and partitions chapters across cores for instantaneous typesetting.
     */
    suspend fun paginate(
        flatItems: List<FlatReaderItem>,
        chineseMode: Int,
        contentWidthPx: Float,
        contentHeightPx: Float,
        textSizePx: Float,
        lineHeightPx: Float,
        paragraphSpacingPx: Float
    ): List<ReaderPage> = withContext(Dispatchers.Default) {
        if (flatItems.isEmpty() || contentWidthPx <= 50f || contentHeightPx <= 100f) {
            return@withContext emptyList()
        }

        // Group flat items by chapter
        val chapters = mutableListOf<MutableList<IndexedValue<FlatReaderItem>>>()
        var currentChapterList: MutableList<IndexedValue<FlatReaderItem>>? = null
        var lastChapterIndex = -1

        for ((idx, item) in flatItems.withIndex()) {
            if (item.chapterIndex != lastChapterIndex || currentChapterList == null) {
                currentChapterList = mutableListOf()
                chapters.add(currentChapterList)
                lastChapterIndex = item.chapterIndex
            }
            currentChapterList.add(IndexedValue(idx, item))
        }

        val charsPerLine = (contentWidthPx / (textSizePx * 1.06f)).toInt().coerceAtLeast(8)
        val titleHeightPx = (textSizePx * 1.35f * 1.35f) + 18f

        // Paginate all chapters in parallel across all CPU cores
        val chapterResults = chapters.map { chapterIndexedItems ->
            async {
                paginateChapter(
                    indexedItems = chapterIndexedItems,
                    chineseMode = chineseMode,
                    contentWidthPx = contentWidthPx,
                    contentHeightPx = contentHeightPx,
                    textSizePx = textSizePx,
                    lineHeightPx = lineHeightPx,
                    paragraphSpacingPx = paragraphSpacingPx,
                    charsPerLine = charsPerLine,
                    titleHeightPx = titleHeightPx
                )
            }
        }.awaitAll()

        // Flatten into unified pages with global continuous index
        val allPages = ArrayList<ReaderPage>()
        var globalPageIndex = 0
        for (cPages in chapterResults) {
            val totalInChapter = cPages.size
            for ((pInCh, page) in cPages.withIndex()) {
                allPages.add(
                    page.copy(
                        pageIndex = globalPageIndex++,
                        pageInChapter = pInCh + 1,
                        totalPagesInChapter = totalInChapter
                    )
                )
            }
        }
        allPages
    }

    private fun paginateChapter(
        indexedItems: List<IndexedValue<FlatReaderItem>>,
        chineseMode: Int,
        contentWidthPx: Float,
        contentHeightPx: Float,
        textSizePx: Float,
        lineHeightPx: Float,
        paragraphSpacingPx: Float,
        charsPerLine: Int,
        titleHeightPx: Float
    ): List<ReaderPage> {
        val pages = mutableListOf<ReaderPage>()
        var currentPageElements = mutableListOf<PageElement>()
        var currentUsedHeightPx = 0f
        var currentChapterIndex = indexedItems.firstOrNull()?.value?.chapterIndex ?: 0
        var currentChapterTitle = "正文"
        var currentStartingFlatIndex = indexedItems.firstOrNull()?.index ?: 0

        fun flushPage() {
            if (currentPageElements.isNotEmpty()) {
                pages.add(
                    ReaderPage(
                        pageIndex = pages.size,
                        chapterIndex = currentChapterIndex,
                        chapterTitle = currentChapterTitle,
                        flatItemIndex = currentStartingFlatIndex,
                        elements = currentPageElements.toList()
                    )
                )
                currentPageElements = mutableListOf()
                currentUsedHeightPx = 0f
            }
        }

        for (indexed in indexedItems) {
            val itemIndex = indexed.index
            when (val item = indexed.value) {
                is FlatReaderItem.Title -> {
                    flushPage()
                    currentChapterIndex = item.chapterIndex
                    currentChapterTitle = item.title
                    currentStartingFlatIndex = itemIndex
                    currentPageElements.add(PageElement.Title(item.title))
                    currentUsedHeightPx += titleHeightPx
                }
                is FlatReaderItem.Node -> {
                    if (currentPageElements.isEmpty()) {
                        currentStartingFlatIndex = itemIndex
                        currentChapterIndex = item.chapterIndex
                    }

                    when (val node = item.node) {
                        is ChapterNode.ImageNode -> {
                            // Ultra-fast metadata-only decoding without pixel decompression
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            try {
                                BitmapFactory.decodeByteArray(node.imageData, 0, node.imageData.size, options)
                            } catch (e: Exception) {
                                // ignore
                            }
                            val rawW = options.outWidth.coerceAtLeast(1)
                            val rawH = options.outHeight.coerceAtLeast(1)
                            val estimatedImgHeight = (contentWidthPx * (rawH.toFloat() / rawW.toFloat())).coerceAtMost(contentHeightPx * 0.75f)

                            if (currentUsedHeightPx + estimatedImgHeight > contentHeightPx && currentPageElements.isNotEmpty()) {
                                flushPage()
                                currentStartingFlatIndex = itemIndex
                            }

                            currentPageElements.add(PageElement.Image(node.imageData, rawW, rawH))
                            currentUsedHeightPx += estimatedImgHeight + 16f
                        }
                        is ChapterNode.TextNode -> {
                            val convertedText: AnnotatedString = if (chineseMode == 0) {
                                node.text
                            } else {
                                val raw = node.text.text
                                val converted = if (chineseMode == 1) ZhConverterUtil.toSimple(raw) else ZhConverterUtil.toTraditional(raw)
                                buildAnnotatedString {
                                    append(converted)
                                    node.text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
                                    node.text.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
                                }
                            }

                            val textLen = convertedText.text.length
                            var offset = 0
                            var isFirstSlice = true

                            while (offset < textLen) {
                                val remainingLen = textLen - offset
                                val totalLines = ((remainingLen + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
                                val availableHeight = (contentHeightPx - currentUsedHeightPx).coerceAtLeast(0f)
                                val linesThatFit = (availableHeight / lineHeightPx).toInt()

                                if (linesThatFit >= 1) {
                                    val linesToTake = minOf(linesThatFit, totalLines)
                                    val charsToTake = minOf(linesToTake * charsPerLine, remainingLen)

                                    val slice = convertedText.subSequence(offset, offset + charsToTake)
                                    currentPageElements.add(
                                        PageElement.Paragraph(
                                            text = slice,
                                            isContinuation = !isFirstSlice
                                        )
                                    )
                                    currentUsedHeightPx += (linesToTake * lineHeightPx) + (if (charsToTake >= remainingLen) paragraphSpacingPx else 0f)

                                    offset += charsToTake
                                    isFirstSlice = false

                                    if (offset < textLen) {
                                        flushPage()
                                        currentStartingFlatIndex = itemIndex
                                    }
                                } else {
                                    flushPage()
                                    currentStartingFlatIndex = itemIndex
                                }
                            }
                        }
                    }
                }
            }
        }

        flushPage()
        return pages
    }
}
