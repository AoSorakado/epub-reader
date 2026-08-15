package com.example.epubreader.ui.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
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
    val initialPage = currentPageIndex.coerceIn(0, pages.size - 1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })

    // Sync external target index (e.g. initial load or chapter jump)
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex in 0 until pages.size && currentPageIndex != pagerState.currentPage) {
            pagerState.scrollToPage(currentPageIndex)
        }
    }

    // Sync internal page changes back to ViewModel
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    BoxWithConstraints(
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
            
            // pageOffset:
            // 0.0 = current page
            // +1.0 = next page (to the right)
            // -1.0 = previous page (to the left)
            val pageOffset = (pageIndex - pagerState.currentPage) + pagerState.currentPageOffsetFraction

            val pageGraphicsModifier = when (pageAnimStyle) {
                // 0: 仿真 / 拟真 (3D Page Curl with Spine Rotation & Gradient lighting)
                0 -> {
                    Modifier
                        .graphicsLayer {
                            val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                            if (clampedOffset < 0f) {
                                // Turning forward: Left page rotating into depth around spine
                                translationX = -clampedOffset * size.width * 0.65f
                                cameraDistance = 16000f
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = clampedOffset * 72f
                                shadowElevation = (16f * abs(clampedOffset)).dp.toPx()
                            } else if (clampedOffset > 0f) {
                                // Turning backward: Right page coming in or under
                                translationX = -clampedOffset * size.width
                                cameraDistance = 16000f
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -72f + (1f - clampedOffset) * 72f
                                shadowElevation = (16f * (1f - clampedOffset)).dp.toPx()
                            } else {
                                translationX = 0f
                                rotationY = 0f
                            }
                        }
                        .zIndex(if (pageOffset <= 0f) 2f else 1f)
                        .drawWithContent {
                            drawContent()
                            val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                            if (clampedOffset < 0f) {
                                // Light and shadow along spine fold
                                val progress = abs(clampedOffset)
                                val foldX = size.width * (1f - progress)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.16f * progress),
                                            Color.White.copy(alpha = 0.32f * progress),
                                            Color.Transparent
                                        ),
                                        startX = (foldX - 90f).coerceAtLeast(0f),
                                        endX = foldX + 50f
                                    )
                                )
                            } else if (clampedOffset > 0f) {
                                // Underneath shadow
                                val progress = 1f - pageOffset.coerceIn(0f, 1f)
                                drawRect(Color.Black.copy(alpha = (1f - progress) * 0.22f))
                            }
                        }
                }

                // 1: 平移 (Slide - Smooth 120Hz 1:1 hardware pager)
                1 -> {
                    Modifier
                        .graphicsLayer {
                            shadowElevation = 8.dp.toPx()
                        }
                }

                // 2: 覆盖 (Cover - Top page slides over stationary bottom page)
                2 -> {
                    Modifier
                        .graphicsLayer {
                            val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                            if (clampedOffset > 0f) {
                                // Next page stays stationary beneath
                                translationX = -clampedOffset * size.width
                            } else {
                                // Current page slides left normally with elevation drop shadow
                                translationX = 0f
                                shadowElevation = 16.dp.toPx()
                            }
                        }
                        .zIndex(if (pageOffset <= 0f) 2f else 1f)
                }

                // 3: 淡入淡出 (Cross-fade)
                3 -> {
                    Modifier
                        .graphicsLayer {
                            val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                            // Stack pages directly on top of each other
                            translationX = -clampedOffset * size.width
                            alpha = (1f - abs(clampedOffset)).coerceIn(0f, 1f)
                            val scale = 0.96f + (0.04f * (1f - abs(clampedOffset)))
                            scaleX = scale
                            scaleY = scale
                        }
                }

                // 4: 无动画 (None - Direct Jump)
                else -> {
                    Modifier
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(pageGraphicsModifier)
                    .pointerInput(pageIndex, pages.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val tapX = offset.x
                                val width = size.width.toFloat()
                                when {
                                    tapX < width * 0.28f -> {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                if (pageAnimStyle == 4) {
                                                    pagerState.scrollToPage(pagerState.currentPage - 1)
                                                } else {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        }
                                    }
                                    tapX > width * 0.72f -> {
                                        if (pagerState.currentPage < pages.size - 1) {
                                            coroutineScope.launch {
                                                if (pageAnimStyle == 4) {
                                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                                } else {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        onToggleToolbars()
                                    }
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
