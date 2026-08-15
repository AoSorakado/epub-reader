package com.example.epubreader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalPageCurlApi::class)
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
    val safeCurrentPage = currentPageIndex.coerceIn(0, pages.size - 1)

    when (pageAnimStyle) {
        // ==========================================
        // 0. 仿真 / 拟真 (True Physical Page Curl & Corner Peel)
        // ==========================================
        0 -> {
            val curlState = rememberPageCurlState(initialCurrent = safeCurrentPage)
            val curlConfig = rememberPageCurlConfig(
                backPageColor = bgColor,
                backPageContentAlpha = 0.04f,
                shadowColor = Color.Black,
                shadowAlpha = 0.35f,
                shadowRadius = 16.dp,
                dragInteraction = PageCurlConfig.GestureDragInteraction(
                    pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge
                )
            )

            LaunchedEffect(safeCurrentPage) {
                if (safeCurrentPage != curlState.current) {
                    curlState.snapTo(safeCurrentPage)
                }
            }

            LaunchedEffect(curlState.current) {
                if (curlState.current != safeCurrentPage) {
                    onPageChanged(curlState.current)
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                PageCurl(
                    count = pages.size,
                    state = curlState,
                    config = curlConfig,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    key(index) {
                        val page = pages.getOrNull(index) ?: return@PageCurl
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Central hit-zone for menu toggle without stealing edge curl drags
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .fillMaxHeight(0.65f)
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleToolbars() })
                        }
                )
            }
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
                            coverOffset.animateTo(-widthPx, tween(260))
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
                            coverOffset.animateTo(0f, tween(260))
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
                                                coverOffset.animateTo(-widthPx, tween(200))
                                                onPageChanged(safeCurrentPage + 1)
                                                coverOffset.snapTo(0f)
                                                dragDirection = 0
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(0f, tween(200))
                                                dragDirection = 0
                                            }
                                        }
                                    }
                                    1 -> {
                                        if (currentVal > -widthPx * 0.82f || velocity > 500f) {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(0f, tween(200))
                                                onPageChanged(safeCurrentPage - 1)
                                                coverOffset.snapTo(0f)
                                                dragDirection = 0
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                coverOffset.animateTo(-widthPx, tween(200))
                                                dragDirection = 0
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    coverOffset.animateTo(0f, tween(200))
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
                        fadeProgress.animateTo(1f, tween(260))
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)
    ) {
        // Page Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = page.chapterTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor.copy(alpha = 0.45f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Page Body Elements (Fills strictly allocated body height)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
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
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Page Bottom Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bookTitle,
                fontSize = 10.5.sp,
                color = secondaryTextColor.copy(alpha = 0.40f),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${page.pageInChapter} / ${page.totalPagesInChapter}  ·  ${page.pageIndex + 1}/$totalPages",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor.copy(alpha = 0.45f)
            )
        }
    }
}
