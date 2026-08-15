package com.example.epubreader.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.github.houbb.opencc4j.util.ZhConverterUtil

sealed class PageElement {
    data class Title(val title: String) : PageElement()
    data class Paragraph(val text: AnnotatedString, val isContinuation: Boolean = false) : PageElement()
    data class Image(val bitmap: ImageBitmap) : PageElement()
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

    fun paginate(
        flatItems: List<FlatReaderItem>,
        chineseMode: Int,
        contentWidthPx: Float,
        contentHeightPx: Float,
        textSizePx: Float,
        lineHeightPx: Float,
        paragraphSpacingPx: Float
    ): List<ReaderPage> {
        if (flatItems.isEmpty() || contentWidthPx <= 50f || contentHeightPx <= 100f) {
            return emptyList()
        }

        val rawPages = mutableListOf<ReaderPage>()
        var currentPageElements = mutableListOf<PageElement>()
        var currentUsedHeightPx = 0f
        var currentChapterIndex = 0
        var currentChapterTitle = "正文"
        var currentStartingFlatIndex = 0

        val charsPerLine = (contentWidthPx / (textSizePx * 1.02f)).toInt().coerceAtLeast(10)
        val titleHeightPx = textSizePx * 1.35f * 1.4f + 36f

        fun flushPage() {
            if (currentPageElements.isNotEmpty()) {
                rawPages.add(
                    ReaderPage(
                        pageIndex = rawPages.size,
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

        flatItems.forEachIndexed { itemIndex, item ->
            when (item) {
                is FlatReaderItem.Title -> {
                    // New chapter starts on a fresh page
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
                            val bitmap = try {
                                BitmapFactory.decodeByteArray(node.imageData, 0, node.imageData.size)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }

                            if (bitmap != null) {
                                val estimatedImgHeight = (contentWidthPx * (bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f))).coerceAtMost(contentHeightPx * 0.75f)
                                if (currentUsedHeightPx + estimatedImgHeight > contentHeightPx && currentPageElements.isNotEmpty()) {
                                    flushPage()
                                    currentStartingFlatIndex = itemIndex
                                }
                                currentPageElements.add(PageElement.Image(bitmap))
                                currentUsedHeightPx += estimatedImgHeight + 16f
                            }
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

                            var remainingString = convertedText
                            var isFirstSliceOfParagraph = true

                            while (remainingString.text.isNotEmpty()) {
                                val totalLines = kotlin.math.ceil(remainingString.text.length.toFloat() / charsPerLine.toFloat()).toInt().coerceAtLeast(1)
                                val availableHeight = (contentHeightPx - currentUsedHeightPx).coerceAtLeast(0f)
                                val linesThatFit = (availableHeight / lineHeightPx).toInt()

                                if (linesThatFit >= 1) {
                                    val linesToTake = minOf(linesThatFit, totalLines)
                                    val charsToTake = (linesToTake * charsPerLine).coerceAtMost(remainingString.text.length)

                                    val slice = remainingString.subSequence(0, charsToTake)
                                    currentPageElements.add(
                                        PageElement.Paragraph(
                                            text = slice,
                                            isContinuation = !isFirstSliceOfParagraph
                                        )
                                    )
                                    currentUsedHeightPx += (linesToTake * lineHeightPx) + (if (linesToTake >= totalLines) paragraphSpacingPx else 0f)

                                    remainingString = if (charsToTake < remainingString.text.length) {
                                        remainingString.subSequence(charsToTake, remainingString.text.length)
                                    } else {
                                        AnnotatedString("")
                                    }
                                    isFirstSliceOfParagraph = false

                                    if (remainingString.text.isNotEmpty()) {
                                        flushPage()
                                        currentStartingFlatIndex = itemIndex
                                    }
                                } else {
                                    // Not enough room on this page, start new page
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

        // Assign pageInChapter and totalPagesInChapter
        val chapterPageCounts = mutableMapOf<Int, Int>()
        rawPages.forEach { page ->
            chapterPageCounts[page.chapterIndex] = (chapterPageCounts[page.chapterIndex] ?: 0) + 1
        }

        val chapterCurrentIndex = mutableMapOf<Int, Int>()
        return rawPages.map { page ->
            val cur = (chapterCurrentIndex[page.chapterIndex] ?: 0) + 1
            chapterCurrentIndex[page.chapterIndex] = cur
            val total = chapterPageCounts[page.chapterIndex] ?: 1
            page.copy(pageInChapter = cur, totalPagesInChapter = total)
        }
    }
}
