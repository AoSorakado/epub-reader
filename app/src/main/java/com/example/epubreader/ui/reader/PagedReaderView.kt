package com.example.epubreader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PagedReaderView(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    onPageChanged: (Int) -> Unit,
    onToggleToolbars: () -> Unit,
    pageAnimStyle: Int, // 0: 仿真, 1: 平移, 2: 覆盖, 3: 淡入, 4: 无动画
    bgColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    customFontFamily: FontFamily?,
    bookTitle: String,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text("正在排版中...", color = secondaryTextColor, fontSize = 14.sp)
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val safeCurrentPage = currentPageIndex.coerceIn(0, pages.size - 1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        fun turnToPage(targetPage: Int, animated: Boolean = true) {
            val clamped = targetPage.coerceIn(0, pages.size - 1)
            if (clamped == safeCurrentPage) {
                coroutineScope.launch {
                    dragOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
                return
            }

            if (!animated || pageAnimStyle == 4) {
                coroutineScope.launch {
                    dragOffset.snapTo(0f)
                    onPageChanged(clamped)
                }
                return
            }

            val isNext = clamped > safeCurrentPage
            val targetOffset = if (isNext) -widthPx else widthPx

            coroutineScope.launch {
                dragOffset.animateTo(
                    targetOffset,
                    spring(dampingRatio = 0.82f, stiffness = 320f)
                )
                onPageChanged(clamped)
                dragOffset.snapTo(0f)
            }
        }

        val velocityTracker = remember { VelocityTracker() }

        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(safeCurrentPage, pages.size, pageAnimStyle) {
                detectTapGestures(
                    onTap = { offset ->
                        val tapX = offset.x
                        when {
                            tapX < widthPx * 0.28f -> {
                                if (safeCurrentPage > 0) turnToPage(safeCurrentPage - 1)
                            }
                            tapX > widthPx * 0.72f -> {
                                if (safeCurrentPage < pages.size - 1) turnToPage(safeCurrentPage + 1)
                            }
                            else -> {
                                onToggleToolbars()
                            }
                        }
                    }
                )
            }
            .pointerInput(safeCurrentPage, pages.size, pageAnimStyle) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        isDragging = false
                        val velocity = velocityTracker.calculateVelocity().x
                        val currentOffset = dragOffset.value

                        val shouldGoNext = (currentOffset < -widthPx * 0.18f || velocity < -600f) && safeCurrentPage < pages.size - 1
                        val shouldGoPrev = (currentOffset > widthPx * 0.18f || velocity > 600f) && safeCurrentPage > 0

                        when {
                            shouldGoNext -> turnToPage(safeCurrentPage + 1, animated = pageAnimStyle != 4)
                            shouldGoPrev -> turnToPage(safeCurrentPage - 1, animated = pageAnimStyle != 4)
                            else -> {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        coroutineScope.launch {
                            dragOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        coroutineScope.launch {
                            val newOffset = (dragOffset.value + dragAmount.x)
                                .coerceIn(
                                    if (safeCurrentPage >= pages.size - 1) -widthPx * 0.15f else -widthPx,
                                    if (safeCurrentPage <= 0) widthPx * 0.15f else widthPx
                                )
                            dragOffset.snapTo(newOffset)
                        }
                    }
                )
            }

        Box(modifier = gestureModifier) {
            val offsetVal = dragOffset.value
            val progress = (offsetVal / widthPx).coerceIn(-1f, 1f)

            when (pageAnimStyle) {
                // 0: 仿真 / 拟真 3D 翻页 (Simulation / 3D Page Curl)
                0 -> {
                    if (offsetVal < 0) {
                        // Turning Next: Underneath page is Next Page, Top page is Current Page flipping away
                        if (safeCurrentPage + 1 < pages.size) {
                            SinglePageRender(
                                page = pages[safeCurrentPage + 1],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        // Shadow on incoming page cast by turning page
                                        val shadowAlpha = (1f - abs(progress)) * 0.22f
                                        drawRect(Color.Black.copy(alpha = shadowAlpha))
                                    }
                            )
                        }

                        // Current Page flipping with 3D rotation & back spine gradient
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cameraDistance = 12000f
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = progress * 75f
                                    translationX = offsetVal * 0.35f
                                }
                                .drawBehind {
                                    // Curl fold edge highlight and shadow
                                    val foldX = size.width * (1f + progress)
                                    if (abs(progress) > 0.01f) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.12f * abs(progress)),
                                                    Color.White.copy(alpha = 0.25f * abs(progress)),
                                                    Color.Transparent
                                                ),
                                                startX = (foldX - 80f).coerceAtLeast(0f),
                                                endX = foldX + 40f
                                            )
                                        )
                                    }
                                }
                        )
                    } else if (offsetVal > 0) {
                        // Turning Prev: Current page is under, Previous page flips in from left
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val shadowAlpha = progress * 0.22f
                                    drawRect(Color.Black.copy(alpha = shadowAlpha))
                                }
                        )

                        if (safeCurrentPage - 1 >= 0) {
                            SinglePageRender(
                                page = pages[safeCurrentPage - 1],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        cameraDistance = 12000f
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        rotationY = -75f + (progress * 75f)
                                        translationX = -widthPx + (offsetVal * 0.65f)
                                    }
                            )
                        }
                    } else {
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 1: 平移 / 滑动 (Slide)
                1 -> {
                    // Previous Page
                    if (offsetVal > 0 && safeCurrentPage - 1 >= 0) {
                        SinglePageRender(
                            page = pages[safeCurrentPage - 1],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = offsetVal - widthPx }
                        )
                    }

                    // Current Page
                    SinglePageRender(
                        page = pages[safeCurrentPage],
                        totalPages = pages.size,
                        bookTitle = bookTitle,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        textSize = textSize,
                        lineHeightMult = lineHeightMult,
                        paragraphSpacing = paragraphSpacing,
                        customFontFamily = customFontFamily,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = offsetVal }
                    )

                    // Next Page
                    if (offsetVal < 0 && safeCurrentPage + 1 < pages.size) {
                        SinglePageRender(
                            page = pages[safeCurrentPage + 1],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = offsetVal + widthPx }
                        )
                    }
                }

                // 2: 覆盖 (Cover)
                2 -> {
                    if (offsetVal < 0) {
                        // Underneath: Next Page
                        if (safeCurrentPage + 1 < pages.size) {
                            SinglePageRender(
                                page = pages[safeCurrentPage + 1],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Top: Current page sliding left with shadow on left edge
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = offsetVal }
                                .shadow(elevation = 16.dp, shape = RectangleShape, clip = false)
                        )
                    } else if (offsetVal > 0) {
                        // Underneath: Current Page
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Top: Previous page sliding in from left with drop shadow
                        if (safeCurrentPage - 1 >= 0) {
                            SinglePageRender(
                                page = pages[safeCurrentPage - 1],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = offsetVal - widthPx }
                                    .shadow(elevation = 16.dp, shape = RectangleShape, clip = false)
                        )
                        }
                    } else {
                        SinglePageRender(
                            page = pages[safeCurrentPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 3: 淡入淡出 (Cross-fade)
                3 -> {
                    val fadeRatio = abs(progress)
                    val targetPage = if (offsetVal < 0) safeCurrentPage + 1 else safeCurrentPage - 1

                    if (targetPage in 0 until pages.size && fadeRatio > 0.01f) {
                        SinglePageRender(
                            page = pages[targetPage],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = fadeRatio
                                    scaleX = 0.96f + (0.04f * fadeRatio)
                                    scaleY = 0.96f + (0.04f * fadeRatio)
                                }
                        )
                    }

                    SinglePageRender(
                        page = pages[safeCurrentPage],
                        totalPages = pages.size,
                        bookTitle = bookTitle,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        textSize = textSize,
                        lineHeightMult = lineHeightMult,
                        paragraphSpacing = paragraphSpacing,
                        customFontFamily = customFontFamily,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = (1f - fadeRatio).coerceIn(0f, 1f)
                                scaleX = 1f - (0.04f * fadeRatio)
                                scaleY = 1f - (0.04f * fadeRatio)
                            }
                    )
                }

                // 4: 无动画 (None)
                else -> {
                    SinglePageRender(
                        page = pages[safeCurrentPage],
                        totalPages = pages.size,
                        bookTitle = bookTitle,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        textSize = textSize,
                        lineHeightMult = lineHeightMult,
                        paragraphSpacing = paragraphSpacing,
                        customFontFamily = customFontFamily,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SinglePageRender(
    page: ReaderPage,
    totalPages: Int,
    bookTitle: String,
    textColor: Color,
    secondaryTextColor: Color,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    customFontFamily: FontFamily?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 52.dp, bottom = 42.dp, start = 24.dp, end = 24.dp)
    ) {
        // Page Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = page.chapterTitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor.copy(alpha = 0.55f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        // Page Body Elements
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            page.elements.forEach { element ->
                when (element) {
                    is PageElement.Title -> {
                        Text(
                            text = element.title,
                            fontSize = (textSize * 1.35f).sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontFamily = customFontFamily,
                            lineHeight = (textSize * 1.35f * 1.35f).sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                        )
                    }
                    is PageElement.Paragraph -> {
                        val displayText = if (!element.isContinuation && !element.text.text.startsWith("　") && !element.text.text.startsWith("  ")) {
                            "　　" + element.text.text
                        } else {
                            element.text.text
                        }
                        Text(
                            text = displayText,
                            fontFamily = customFontFamily,
                            fontSize = textSize.sp,
                            lineHeight = (textSize * lineHeightMult).coerceAtLeast(textSize * 1.15f).sp,
                            color = textColor,
                            modifier = Modifier.padding(bottom = paragraphSpacing.dp)
                        )
                    }
                    is PageElement.Image -> {
                        androidx.compose.foundation.Image(
                            bitmap = element.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }

        // Page Bottom Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bookTitle,
                fontSize = 11.sp,
                color = secondaryTextColor.copy(alpha = 0.45f),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${page.pageInChapter} / ${page.totalPagesInChapter}  ·  ${page.pageIndex + 1}/$totalPages",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor.copy(alpha = 0.55f)
            )
        }
    }
}
