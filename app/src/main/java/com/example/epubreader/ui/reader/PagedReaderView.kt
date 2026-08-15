package com.example.epubreader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.epubreader.ui.reader.components.SimulationPageView
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagedReaderView(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    onPageChanged: (Int) -> Unit,
    onToggleToolbars: () -> Unit,
    onOpenToc: () -> Unit,
    pageAnimStyle: Int, // 0: 仿真, 1: 平移, 2: 覆盖, 3: 淡入, 4: 无动画
    bgColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    customFontFamily: FontFamily?,
    customFontUri: String? = null,
    bookTitle: String,
    topPadding: Dp = 48.dp,
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
    val safeCurrentPage = currentPageIndex.coerceIn(0, pages.size - 1)

    when (pageAnimStyle) {
        // ==========================================
        // 0. 仿真 / 拟真 (Classic High-Fidelity 120Hz Simulation View)
        // ==========================================
        0 -> {
            SimulationPageView(
                pages = pages,
                currentPageIndex = safeCurrentPage,
                onPageChanged = onPageChanged,
                onToggleToolbars = onToggleToolbars,
                onOpenToc = onOpenToc,
                bgColor = bgColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                textSize = textSize,
                lineHeightMult = lineHeightMult,
                paragraphSpacing = paragraphSpacing,
                customFontFamily = customFontFamily,
                customFontUri = customFontUri,
                bookTitle = bookTitle,
                topPadding = topPadding,
                modifier = modifier
            )
        }

        // ==========================================
        // 1. 平移 / 滑动 (120Hz Hardware HorizontalPager)
        // ==========================================
        1 -> {
            val pagerState = rememberPagerState(initialPage = safeCurrentPage, pageCount = { pages.size })

            LaunchedEffect(safeCurrentPage) {
                if (safeCurrentPage != pagerState.currentPage) {
                    pagerState.scrollToPage(safeCurrentPage)
                }
            }

            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != safeCurrentPage) {
                    onPageChanged(pagerState.currentPage)
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pageIndex) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val width = size.width.toFloat()
                                        when {
                                            offset.x < width * 0.28f -> {
                                                if (pagerState.currentPage > 0) {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                                }
                                            }
                                            offset.x > width * 0.72f -> {
                                                if (pagerState.currentPage < pages.size - 1) {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                                }
                                            }
                                            else -> onToggleToolbars()
                                        }
                                    }
                                )
                            }
                    ) {
                        SinglePageRender(
                            page = page,
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            bgColor = bgColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            topPadding = topPadding,
                            onOpenToc = onOpenToc,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. 覆盖 (Classic 2-Layer Static Stack)
        // ==========================================
        2 -> {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val coverOffset = remember { Animatable(0f) }
                var dragDirection by remember { mutableStateOf(0) } // -1: next, 1: prev, 0: idle
                val velocityTracker = remember { VelocityTracker() }

                fun turnNext() {
                    if (safeCurrentPage < pages.size - 1) {
                        coroutineScope.launch {
                            dragDirection = -1
                            coverOffset.animateTo(-widthPx, tween(320, easing = FastOutSlowInEasing))
                            onPageChanged(safeCurrentPage + 1)
                            coverOffset.snapTo(0f)
                            dragDirection = 0
                        }
                    }
                }

                fun turnPrev() {
                    if (safeCurrentPage > 0) {
                        coroutineScope.launch {
                            dragDirection = 1
                            coverOffset.snapTo(-widthPx)
                            coverOffset.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
                            onPageChanged(safeCurrentPage - 1)
                            coverOffset.snapTo(0f)
                            dragDirection = 0
                        }
                    }
                }

                val coverGestureModifier = Modifier
                    .fillMaxSize()
                    .pointerInput(safeCurrentPage, pages.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                when {
                                    offset.x < widthPx * 0.28f -> turnPrev()
                                    offset.x > widthPx * 0.72f -> turnNext()
                                    else -> onToggleToolbars()
                                }
                            }
                        )
                    }
                    .pointerInput(safeCurrentPage, pages.size) {
                        detectDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity().x
                                val currentVal = coverOffset.value

                                when (dragDirection) {
                                    -1 -> {
                                        if (currentVal < -widthPx * 0.18f || velocity < -500f) {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(-widthPx, tween(260, easing = FastOutSlowInEasing))
                                                onPageChanged(safeCurrentPage + 1)
                                                coverOffset.snapTo(0f)
                                                dragDirection = 0
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                                                dragDirection = 0
                                            }
                                        }
                                    }
                                    1 -> {
                                        if (currentVal > -widthPx * 0.82f || velocity > 500f) {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                                                onPageChanged(safeCurrentPage - 1)
                                                coverOffset.snapTo(0f)
                                                dragDirection = 0
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(-widthPx, tween(260, easing = FastOutSlowInEasing))
                                                dragDirection = 0
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    coverOffset.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                                    dragDirection = 0
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                coroutineScope.launch {
                                    if (dragDirection == 0) {
                                        if (dragAmount.x < 0 && safeCurrentPage < pages.size - 1) {
                                            dragDirection = -1
                                            coverOffset.snapTo(0f)
                                        } else if (dragAmount.x > 0 && safeCurrentPage > 0) {
                                            dragDirection = 1
                                            coverOffset.snapTo(-widthPx)
                                        }
                                    }

                                    if (dragDirection == -1) {
                                        val newX = (coverOffset.value + dragAmount.x).coerceIn(-widthPx, 0f)
                                        coverOffset.snapTo(newX)
                                    } else if (dragDirection == 1) {
                                        val newX = (coverOffset.value + dragAmount.x).coerceIn(-widthPx, 0f)
                                        coverOffset.snapTo(newX)
                                    }
                                }
                            }
                        )
                    }

                Box(modifier = coverGestureModifier) {
                    when (dragDirection) {
                        -1 -> {
                            // Underneath: Next Page (static)
                            if (safeCurrentPage + 1 < pages.size) {
                                SinglePageRender(
                                    page = pages[safeCurrentPage + 1],
                                    totalPages = pages.size,
                                    bookTitle = bookTitle,
                                    bgColor = bgColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    textSize = textSize,
                                    lineHeightMult = lineHeightMult,
                                    paragraphSpacing = paragraphSpacing,
                                    customFontFamily = customFontFamily,
                                    topPadding = topPadding,
                                    onOpenToc = onOpenToc,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Top: Current page sliding left with shadow
                            SinglePageRender(
                                page = pages[safeCurrentPage],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                bgColor = bgColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                topPadding = topPadding,
                                onOpenToc = onOpenToc,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = coverOffset.value }
                                    .shadow(elevation = 20.dp, shape = RectangleShape, clip = false)
                            )
                        }
                        1 -> {
                            // Underneath: Current Page (static)
                            SinglePageRender(
                                page = pages[safeCurrentPage],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                bgColor = bgColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                topPadding = topPadding,
                                onOpenToc = onOpenToc,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Top: Previous page sliding in from left with shadow
                            if (safeCurrentPage - 1 >= 0) {
                                SinglePageRender(
                                    page = pages[safeCurrentPage - 1],
                                    totalPages = pages.size,
                                    bookTitle = bookTitle,
                                    bgColor = bgColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    textSize = textSize,
                                    lineHeightMult = lineHeightMult,
                                    paragraphSpacing = paragraphSpacing,
                                    customFontFamily = customFontFamily,
                                    topPadding = topPadding,
                                    onOpenToc = onOpenToc,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = coverOffset.value }
                                        .shadow(elevation = 20.dp, shape = RectangleShape, clip = false)
                                )
                            }
                        }
                        else -> {
                            SinglePageRender(
                                page = pages[safeCurrentPage],
                                totalPages = pages.size,
                                bookTitle = bookTitle,
                                bgColor = bgColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                customFontFamily = customFontFamily,
                                topPadding = topPadding,
                                onOpenToc = onOpenToc,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. 淡入淡出 (Direct 2-Page Cross-Fade)
        // ==========================================
        3 -> {
            var targetPageIndex by remember { mutableStateOf<Int?>(null) }
            val fadeProgress = remember { Animatable(0f) }

            fun fadeTo(target: Int) {
                if (target in 0 until pages.size && target != safeCurrentPage) {
                    coroutineScope.launch {
                        targetPageIndex = target
                        fadeProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                        onPageChanged(target)
                        targetPageIndex = null
                        fadeProgress.snapTo(0f)
                    }
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .pointerInput(safeCurrentPage, pages.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width.toFloat()
                                when {
                                    offset.x < width * 0.28f -> fadeTo(safeCurrentPage - 1)
                                    offset.x > width * 0.72f -> fadeTo(safeCurrentPage + 1)
                                    else -> onToggleToolbars()
                                }
                            }
                        )
                    }
            ) {
                // Base page
                SinglePageRender(
                    page = pages[safeCurrentPage],
                    totalPages = pages.size,
                    bookTitle = bookTitle,
                    bgColor = bgColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    textSize = textSize,
                    lineHeightMult = lineHeightMult,
                    paragraphSpacing = paragraphSpacing,
                    customFontFamily = customFontFamily,
                    topPadding = topPadding,
                    onOpenToc = onOpenToc,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (targetPageIndex != null) (1f - fadeProgress.value) else 1f }
                )

                // Target fading-in page (no blank in between)
                targetPageIndex?.let { target ->
                    if (target in 0 until pages.size) {
                        SinglePageRender(
                            page = pages[target],
                            totalPages = pages.size,
                            bookTitle = bookTitle,
                            bgColor = bgColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            textSize = textSize,
                            lineHeightMult = lineHeightMult,
                            paragraphSpacing = paragraphSpacing,
                            customFontFamily = customFontFamily,
                            topPadding = topPadding,
                            onOpenToc = onOpenToc,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = fadeProgress.value }
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. 无动画 (Instant Direct Cut)
        // ==========================================
        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .pointerInput(safeCurrentPage, pages.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width.toFloat()
                                when {
                                    offset.x < width * 0.28f -> {
                                        if (safeCurrentPage > 0) onPageChanged(safeCurrentPage - 1)
                                    }
                                    offset.x > width * 0.72f -> {
                                        if (safeCurrentPage < pages.size - 1) onPageChanged(safeCurrentPage + 1)
                                    }
                                    else -> onToggleToolbars()
                                }
                            }
                        )
                    }
            ) {
                SinglePageRender(
                    page = pages[safeCurrentPage],
                    totalPages = pages.size,
                    bookTitle = bookTitle,
                    bgColor = bgColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    textSize = textSize,
                    lineHeightMult = lineHeightMult,
                    paragraphSpacing = paragraphSpacing,
                    customFontFamily = customFontFamily,
                    topPadding = topPadding,
                    onOpenToc = onOpenToc,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SinglePageRender(
    page: ReaderPage,
    totalPages: Int,
    bookTitle: String,
    bgColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    customFontFamily: FontFamily?,
    topPadding: Dp = 48.dp,
    onOpenToc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleFontSize = remember(textSize) { (textSize * 1.32f).sp }
    val titleLineHeight = remember(textSize) { (textSize * 1.32f * 1.32f).sp }
    val bodyFontSize = remember(textSize) { textSize.sp }
    val bodyLineHeight = remember(textSize, lineHeightMult) { (textSize * lineHeightMult).coerceAtLeast(textSize * 1.15f).sp }
    val bottomSpacing = remember(paragraphSpacing) { paragraphSpacing.dp }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(start = 18.dp, end = 18.dp, top = topPadding, bottom = 20.dp)
    ) {
        // Page Body Elements (Occupies main viewport cleanly)
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            page.elements.forEach { element ->
                when (element) {
                    is PageElement.Title -> {
                        Text(
                            text = element.title,
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontFamily = customFontFamily,
                            lineHeight = titleLineHeight,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }
                    is PageElement.Paragraph -> {
                        val displayText = remember(element.text.text, element.isContinuation) {
                            if (!element.isContinuation && !element.text.text.startsWith("　") && !element.text.text.startsWith("  ")) {
                                "　　" + element.text.text
                            } else {
                                element.text.text
                            }
                        }
                        Text(
                            text = displayText,
                            fontFamily = customFontFamily,
                            fontSize = bodyFontSize,
                            lineHeight = bodyLineHeight,
                            color = textColor,
                            modifier = Modifier.padding(bottom = bottomSpacing)
                        )
                    }
                    is PageElement.Image -> {
                        element.bitmap?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

