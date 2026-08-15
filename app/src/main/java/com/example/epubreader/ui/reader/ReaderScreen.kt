package com.example.epubreader.ui.reader

import kotlinx.coroutines.launch

import androidx.compose.ui.util.fastRoundToInt

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.components.liquid.LiquidSlider
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap

import android.app.Application
import com.github.houbb.opencc4j.util.ZhConverterUtil
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    navController: NavController,
    bookId: Long,
    backgroundBackdrop: com.kyant.backdrop.Backdrop,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dao = AppDatabase.getDatabase(context).bookDao()
    val viewModel: ReaderViewModel = viewModel(
        key = "reader_$bookId",
        factory = ReaderViewModelFactory(bookId, dao, context.applicationContext as Application)
    )

    var showToolbars by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsButtonBounds by remember { mutableStateOf(Rect.Zero) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (showSettings) {
            showSettings = false
        } else if (showToolbars) {
            showToolbars = false
        } else {
            if (onBackClick != null) {
                onBackClick()
            } else {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(bookId) {
        viewModel.loadBook(context)
    }

    val bookEntity by viewModel.bookEntity.collectAsState()
    val parsedChapters by viewModel.parsedChapters.collectAsState()
    val tocList by viewModel.toc.collectAsState()
    val flatItems by viewModel.flatItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val themeIndex by viewModel.themeIndex.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val lineHeightMult by viewModel.lineHeightMult.collectAsState()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsState()
    val chineseMode by viewModel.chineseMode.collectAsState()
    val customFontUri by viewModel.customFontUri.collectAsState()
    val pageTurnMode by viewModel.pageTurnMode.collectAsState()
    val pageAnimStyle by viewModel.pageAnimStyle.collectAsState()

    val settingsViewModel: com.example.epubreader.ui.settings.SettingsViewModel = viewModel()
    val appTheme by settingsViewModel.appTheme.collectAsState()
    val isCustomThemeThreeColors by settingsViewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by settingsViewModel.customColors.collectAsState()

    val morphProgress by animateFloatAsState(
        targetValue = if (showSettings) 1f else 0f,
        animationSpec = if (showSettings) {
            spring(
                dampingRatio = 0.72f,
                stiffness = 240f
            )
        } else {
            spring(
                dampingRatio = 0.78f, // Fluid damping
                stiffness = 195f      // Balanced, snappy yet fluid collapse (黄金速率，灵动不拖沓)
            )
        },
        label = "SettingsMorphProgress"
    )

    // Button Jelly Pulse upon collapse
    val buttonPulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val morphScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showSettings) {
        if (!showSettings && morphProgress > 0f) {
            kotlinx.coroutines.delay(260)
            buttonPulseScale.snapTo(1.18f)
            buttonPulseScale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = 320f)
            )
        }
    }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fontFile = File(context.filesDir, "custom_font.ttf")
                inputStream?.use { input ->
                    FileOutputStream(fontFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.setCustomFontUri(fontFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val customFontFamily = remember(customFontUri) {
        if (!customFontUri.isNullOrEmpty() && File(customFontUri!!).exists()) {
            FontFamily(Font(File(customFontUri!!)))
        } else {
            FontFamily.Default
        }
    }

    val themeColors = listOf(
        Pair(Color.White, Color(0xFF222222)), // 0: White
        Pair(Color(0xFFFDF6E3), Color(0xFF5C4B37)), // 1: Sepia
        Pair(Color(0xFF1E1E22), Color(0xFFD4D4D4)) // 2: Dark
    )
    val currentTheme = themeColors.getOrElse(themeIndex) { themeColors[0] }
    val bgColor = currentTheme.first
    val textColor = currentTheme.second
    val isDark = themeIndex == 2

    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    val prefs = context.getSharedPreferences("liquid_settings", android.content.Context.MODE_PRIVATE)
    val immersiveStatusBar = prefs.getBoolean("immersiveStatusBar", false)

    if (immersiveStatusBar && window != null) {
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
        LaunchedEffect(Unit) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        DisposableEffect(Unit) {
            onDispose {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val readerBackdrop = rememberLayerBackdrop {
        drawRect(bgColor)
        drawContent()
    }
    val bottomSheetBackdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = textColor, 
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在解析书籍... ${(loadingProgress * 100).toInt()}%", 
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
        } else {
            val initialPositionStr = bookEntity?.lastReadPosition?.split("_")
            val initialChapterIndex = initialPositionStr?.getOrNull(0)?.toIntOrNull() ?: 0
            val initialOffset = initialPositionStr?.getOrNull(1)?.toIntOrNull() ?: 0
            val initialNodeIndex = initialPositionStr?.getOrNull(2)?.toIntOrNull() ?: 0
            
            val initialFlatIndex = remember(flatItems, initialChapterIndex, initialNodeIndex) {
                flatItems.indexOfFirst {
                    it.chapterIndex == initialChapterIndex && (it !is com.example.epubreader.ui.reader.FlatReaderItem.Node || it.nodeIndex >= initialNodeIndex)
                }.coerceAtLeast(0)
            }
            
            val density = androidx.compose.ui.platform.LocalDensity.current
            var containerWidthPx by remember { mutableStateOf(0f) }
            var containerHeightPx by remember { mutableStateOf(0f) }

            val pages = remember(
                flatItems,
                chineseMode,
                containerWidthPx,
                containerHeightPx,
                textSize,
                lineHeightMult,
                paragraphSpacing
            ) {
                if (containerWidthPx > 100f && containerHeightPx > 200f) {
                    val contentWidthPx = containerWidthPx - with(density) { 36.dp.toPx() }
                    val contentHeightPx = containerHeightPx - with(density) { (20.dp + 8.dp + 26.dp + 12.dp).toPx() }
                    val textSizePx = with(density) { textSize.sp.toPx() }
                    val lineHeightPx = (textSizePx * lineHeightMult).coerceAtLeast(textSizePx * 1.15f)
                    val paragraphSpacingPx = with(density) { paragraphSpacing.dp.toPx() }

                    ReaderPagination.paginate(
                        flatItems = flatItems,
                        chineseMode = chineseMode,
                        contentWidthPx = contentWidthPx,
                        contentHeightPx = contentHeightPx,
                        textSizePx = textSizePx,
                        lineHeightPx = lineHeightPx,
                        paragraphSpacingPx = paragraphSpacingPx
                    )
                } else emptyList()
            }

            var pagedCurrentIndex by remember { mutableStateOf(0) }
            var hasInitializedPagedIndex by remember { mutableStateOf(false) }
            var showTocSheet by remember { mutableStateOf(false) }

            LaunchedEffect(pages.isNotEmpty()) {
                if (pages.isNotEmpty() && !hasInitializedPagedIndex) {
                    hasInitializedPagedIndex = true
                    if (initialFlatIndex > 0) {
                        val matched = pages.indexOfFirst { it.flatItemIndex >= initialFlatIndex }
                        if (matched >= 0) {
                            pagedCurrentIndex = matched
                        }
                    }
                }
            }

            fun onPagedIndexChanged(newPage: Int) {
                if (newPage != pagedCurrentIndex) {
                    pagedCurrentIndex = newPage
                    val current = pages.getOrNull(newPage)
                    if (current != null) {
                        viewModel.saveProgress(current.flatItemIndex, 0)
                    }
                }
            }

            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialFlatIndex,
                initialFirstVisibleItemScrollOffset = initialOffset
            )
            
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, listState, pagedCurrentIndex, pageTurnMode) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        if (pageTurnMode == 0) {
                            viewModel.saveProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                        } else {
                            val current = pages.getOrNull(pagedCurrentIndex)
                            if (current != null) {
                                viewModel.saveProgress(current.flatItemIndex, 0)
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    if (pageTurnMode == 0) {
                        viewModel.saveProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    } else {
                        val current = pages.getOrNull(pagedCurrentIndex)
                        if (current != null) {
                            viewModel.saveProgress(current.flatItemIndex, 0)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .layerBackdrop(readerBackdrop)
                    .onGloballyPositioned { coords ->
                        containerWidthPx = coords.size.width.toFloat()
                        containerHeightPx = coords.size.height.toFloat()
                    }
            ) {
                if (pageTurnMode == 0) {
                    // Main continuous scrolling area
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { 
                                showToolbars = !showToolbars
                                if (!showToolbars) showSettings = false
                            },
                        contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp, start = 24.dp, end = 24.dp)
                    ) {
                        item {
                            Text(
                                text = bookEntity?.title ?: "未知书名",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 32.dp),
                                lineHeight = 40.sp
                            )
                        }
                        
                        items(flatItems) { item ->
                            when (item) {
                                is com.example.epubreader.ui.reader.FlatReaderItem.Title -> {
                                    Text(
                                        text = item.title,
                                        fontSize = (textSize * 1.3f).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                                    )
                                }
                                is com.example.epubreader.ui.reader.FlatReaderItem.Node -> {
                                    val node = item.node
                                    when (node) {
                                        is ChapterNode.TextNode -> {
                                            val displayAnnotated = remember(node.text, chineseMode) {
                                                if (chineseMode == 0) node.text
                                                else {
                                                    val newStr = if (chineseMode == 1) ZhConverterUtil.toSimple(node.text.text) else ZhConverterUtil.toTraditional(node.text.text)
                                                    buildAnnotatedString {
                                                        append(newStr)
                                                        node.text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
                                                        node.text.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
                                                    }
                                                }
                                            }
                                            Text(
                                                text = displayAnnotated,
                                                fontFamily = customFontFamily,
                                                fontSize = textSize.sp,
                                                lineHeight = (textSize * lineHeightMult).coerceAtLeast(textSize * 1.1f).sp,
                                                color = textColor,
                                                modifier = Modifier.padding(bottom = paragraphSpacing.dp)
                                            )
                                        }
                                        is ChapterNode.ImageNode -> {
                                            val bitmap = remember(node.imageData) {
                                                BitmapFactory.decodeByteArray(node.imageData, 0, node.imageData.size)?.asImageBitmap()
                                            }
                                            if (bitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bitmap,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.FillWidth,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 16.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } // End of LazyColumn
                } else {
                    // Paged Reader View (Horizontal Page Turn)
                    PagedReaderView(
                        pages = pages,
                        currentPageIndex = pagedCurrentIndex,
                        onPageChanged = { onPagedIndexChanged(it) },
                        onToggleToolbars = {
                            showToolbars = !showToolbars
                            if (!showToolbars) showSettings = false
                        },
                        onOpenToc = { showTocSheet = true },
                        pageAnimStyle = pageAnimStyle,
                        bgColor = bgColor,
                        textColor = textColor,
                        secondaryTextColor = if (themeIndex == 2) Color(0xFF94A3B8) else Color(0xFF64748B),
                        textSize = textSize,
                        lineHeightMult = lineHeightMult,
                        paragraphSpacing = paragraphSpacing,
                        customFontFamily = customFontFamily,
                        bookTitle = bookEntity?.title ?: "轻小说阅读",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } // End of captured Box

            // Background Dimming Overlay
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            detectTapGestures { showSettings = false }
                        }
                )
            }

            // --- Glass Overlays Layer ---
            
            // 1. Top Toolbar (Back, Reading Progress Capsule, and Settings Buttons)
            AnimatedVisibility(
                visible = showToolbars,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val totalItems = flatItems.size
                val currentItemIndex = if (pageTurnMode == 0) {
                    listState.firstVisibleItemIndex
                } else {
                    pages.getOrNull(pagedCurrentIndex)?.flatItemIndex ?: 0
                }
                val calculatedProgress = if (totalItems > 0) {
                    ((currentItemIndex + 1).toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
                } else {
                    (bookEntity?.totalProgress ?: 0f)
                }
                val progressPercent = (calculatedProgress * 100).toInt()
                val estimatedTimeText = viewModel.getEstimatedRemainingTimeText(currentItemIndex)

                LaunchedEffect(currentItemIndex) {
                    viewModel.updateReadingPosition(currentItemIndex)
                }

            // Continuous Reading Health / Eye Rest Reminder (60 min, 90 min, 120 min)
            LaunchedEffect(Unit) {
                var elapsedSeconds = 0L
                val milestonesNotified = mutableSetOf<Long>()
                while (true) {
                    kotlinx.coroutines.delay(1000L)
                    elapsedSeconds++
                    when {
                        elapsedSeconds >= 3600L && 3600L !in milestonesNotified -> {
                            milestonesNotified.add(3600L)
                            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                                text = "☕ 您已专注阅读 1 小时，请注意放松眼睛，眺望远方~",
                                type = com.example.epubreader.ui.components.toast.ToastType.Health,
                                durationMs = 3000L
                            )
                        }
                        elapsedSeconds >= 5400L && 5400L !in milestonesNotified -> {
                            milestonesNotified.add(5400L)
                            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                                text = "🌿 您已连续阅读 1.5 小时，建议起身活动一下哦~",
                                type = com.example.epubreader.ui.components.toast.ToastType.Health,
                                durationMs = 3000L
                            )
                        }
                        elapsedSeconds >= 7200L && 7200L !in milestonesNotified -> {
                            milestonesNotified.add(7200L)
                            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                                text = "✨ 您已沉浸阅读 2 小时，给眼睛放个小假吧~",
                                type = com.example.epubreader.ui.components.toast.ToastType.Health,
                                durationMs = 3000L
                            )
                        }
                    }
                }
            }

            val haptic = LocalHapticFeedback.current
            var isCapsuleExpanded by remember { mutableStateOf(false) }

            LaunchedEffect(showToolbars) {
                if (!showToolbars) {
                    isCapsuleExpanded = false
                }
            }

            val topPadding = maxOf(
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                if (immersiveStatusBar) 28.dp else 12.dp
            ) + 12.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // 1. Back Button (Pinned to Left)
                LiquidButton(
                    onClick = {
                        if (onBackClick != null) {
                            onBackClick()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    backdrop = readerBackdrop,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                }

                // 2. Reading Progress Liquid Capsule (Pinned to Center, expands symmetrically to both sides)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(44.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 340f
                            ),
                            alignment = Alignment.Center
                        )
                        .drawBackdrop(
                            backdrop = readerBackdrop,
                            shape = { RoundedCornerShape(50) },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(14f.dp.toPx(), 28f.dp.toPx(), chromaticAberration = true)
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                            }
                        )
                        .border(
                            width = 0.8.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.35f else 0.70f),
                                    Color.White.copy(alpha = if (isDark) 0.12f else 0.30f)
                                )
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isCapsuleExpanded = true
                                },
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isCapsuleExpanded = !isCapsuleExpanded
                                }
                            )
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isCapsuleExpanded,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            (fadeIn(animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f)) + scaleIn(initialScale = 0.90f, transformOrigin = TransformOrigin.Center)) togetherWith
                            (fadeOut(animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f)) + scaleOut(targetScale = 0.90f, transformOrigin = TransformOrigin.Center))
                        },
                        label = "capsuleAnimatedContent"
                    ) { expanded ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoStories,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.80f),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (expanded) {
                                Text(
                                    text = "$estimatedTimeText · $progressPercent%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = "$progressPercent%",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                // 3. Settings Button (Pinned to Right)
                LiquidButton(
                    onClick = { showSettings = !showSettings },
                    backdrop = readerBackdrop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .onGloballyPositioned { coordinates ->
                            settingsButtonBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = if (morphProgress > 0.001f) 0f else 1f
                            scaleX = buttonPulseScale.value
                            scaleY = buttonPulseScale.value
                        }
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = textColor)
                }
            }
        }

        // 2. Bottom Floating Liquid Toolbar (TOC Button + Chapter Info Pill)
        AnimatedVisibility(
            visible = showToolbars && !showSettings,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val currentChapterName = if (pageTurnMode == 0) {
                val currentFlat = flatItems.getOrNull(listState.firstVisibleItemIndex)
                if (currentFlat != null) parsedChapters.getOrNull(currentFlat.chapterIndex)?.title ?: "正文" else "正文"
            } else {
                pages.getOrNull(pagedCurrentIndex)?.chapterTitle ?: "正文"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: 目录 TOC LiquidButton (Frosted glass with List icon & "目录")
                LiquidButton(
                    onClick = {
                        showToolbars = false
                        showSettings = false
                        showTocSheet = true
                    },
                    backdrop = readerBackdrop,
                    surfaceColor = if (themeIndex == 2) Color.White.copy(0.12f) else Color.White.copy(0.28f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.List,
                            contentDescription = "目录",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "目录",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // Right: Current Chapter Name Glass Pill
                LiquidButton(
                    onClick = {
                        showToolbars = false
                        showSettings = false
                        showTocSheet = true
                    },
                    backdrop = readerBackdrop,
                    surfaceColor = if (themeIndex == 2) Color.White.copy(0.10f) else Color.White.copy(0.20f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = currentChapterName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(max = 210.dp)
                    )
                }
            }
        }

        // 2. Liquid Shape Morphing Container (Button expands into Dialog with damping & jelly physics)
        if (morphProgress > 0.001f || showSettings) {
            // Scrim overlay with gentle dimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morphProgress * 0.15f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettings = false }
                    )
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = LocalDensity.current
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val fallbackBtnBounds = Rect(
                    screenWidthPx - with(density) { 64.dp.toPx() },
                    with(density) { 44.dp.toPx() },
                    screenWidthPx - with(density) { 16.dp.toPx() },
                    with(density) { 92.dp.toPx() }
                )
                val btnBounds = if (settingsButtonBounds != Rect.Zero) settingsButtonBounds else fallbackBtnBounds

                val dialogWidthPx = with(density) { (maxWidth - 40.dp).toPx() }
                val dialogHeightPx = with(density) { 560.dp.toPx().coerceAtMost(maxHeight.toPx() - 100.dp.toPx()) }
                val dialogLeft = (screenWidthPx - dialogWidthPx) / 2f
                val dialogTop = (screenHeightPx - dialogHeightPx) / 2f
                val dialogBounds = Rect(dialogLeft, dialogTop, dialogLeft + dialogWidthPx, dialogTop + dialogHeightPx)

                val currentLeft = androidx.compose.ui.util.lerp(btnBounds.left, dialogBounds.left, morphProgress)
                val currentTop = androidx.compose.ui.util.lerp(btnBounds.top, dialogBounds.top, morphProgress)
                val currentWidth = androidx.compose.ui.util.lerp(btnBounds.width, dialogWidthPx, morphProgress).coerceAtLeast(1f)
                val currentHeight = androidx.compose.ui.util.lerp(btnBounds.height, dialogHeightPx, morphProgress).coerceAtLeast(1f)
                val currentCornerRadius = androidx.compose.ui.util.lerp(btnBounds.height / 2f, with(density) { 28.dp.toPx() }, morphProgress).coerceAtLeast(0f)

                val glassTextColor = if (themeIndex == 2) Color.White else Color(0xFF1C1C1E)
                val glassPanelColor = if (themeIndex == 2) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

                Box(
                    modifier = Modifier
                        .layout { measurable, _ ->
                            val placeable = measurable.measure(
                                Constraints.fixed(currentWidth.fastRoundToInt(), currentHeight.fastRoundToInt())
                            )
                            layout(currentWidth.fastRoundToInt(), currentHeight.fastRoundToInt()) {
                                placeable.place(currentLeft.fastRoundToInt(), currentTop.fastRoundToInt())
                            }
                        }
                        .drawBackdrop(
                            backdrop = readerBackdrop,
                            shape = { RoundedCornerShape(with(density) { currentCornerRadius.coerceAtLeast(0f).toDp() }) },
                            effects = {
                                vibrancy()
                                blur(androidx.compose.ui.util.lerp(3f, 8f, morphProgress).coerceAtLeast(0.1f).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, morphProgress).coerceAtLeast(0.1f).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, morphProgress).coerceAtLeast(0.1f).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                val surfaceColor = if (themeIndex == 2) Color.White.copy(0.06f) else Color.White.copy(0.10f)
                                drawRect(surfaceColor)
                            },
                            exportedBackdrop = bottomSheetBackdrop
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent taps from dismissing through to scrim
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Morphing Icon: Settings Icon centered inside button, fades out as it expands
                    if (morphProgress < 0.6f) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    alpha = (1f - morphProgress * 2.5f).coerceIn(0f, 1f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = textColor)
                        }
                    }

                    // Morphing Content: Settings controls fade in as the container reaches full dialog size
                    if (morphProgress > 0.2f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((morphProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                }
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val headerStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = glassTextColor,
                                    letterSpacing = 0.6.sp
                                )
                                val labelStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glassTextColor
                                )
                                val buttonTextStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color = glassTextColor
                                )
                                val sliderTrackColor = if (themeIndex == 2) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)
                                
                                val currentThemeAccent = com.example.epubreader.ui.theme.getThemeAccentColor(
                                    theme = appTheme,
                                    customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
                                )
                                val sliderAccentColor = when (themeIndex) {
                                    1 -> Color(0xFF52855B) // Eye-care warm green/sage
                                    2 -> Color(0xFF38BDF8) // Night mode luminous cyan
                                    else -> currentThemeAccent // Light mode matches global theme
                                }

                                Text("排版调整", style = headerStyle)
                                Spacer(modifier = Modifier.height(18.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("字号", style = labelStyle, modifier = Modifier.width(40.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("A", style = labelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    LiquidSlider(
                                        value = { textSize },
                                        onValueChange = { viewModel.setTextSize(it) },
                                        valueRange = 12f..32f,
                                        visibilityThreshold = 0.1f,
                                        backdrop = bottomSheetBackdrop,
                                        accentColor = sliderAccentColor,
                                        trackColor = sliderTrackColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("A", style = labelStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                                }

                                Spacer(modifier = Modifier.height(18.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("行距", style = labelStyle, modifier = Modifier.width(40.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("小", style = labelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    LiquidSlider(
                                        value = { lineHeightMult },
                                        onValueChange = { viewModel.setLineHeightMult(it) },
                                        valueRange = 1.0f..3.0f,
                                        visibilityThreshold = 0.05f,
                                        backdrop = bottomSheetBackdrop,
                                        accentColor = sliderAccentColor,
                                        trackColor = sliderTrackColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("大", style = labelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
                                }
                                
                                Spacer(modifier = Modifier.height(18.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("段距", style = labelStyle, modifier = Modifier.width(40.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("小", style = labelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    LiquidSlider(
                                        value = { paragraphSpacing },
                                        onValueChange = { viewModel.setParagraphSpacing(it) },
                                        valueRange = 0f..48f,
                                        visibilityThreshold = 1f,
                                        backdrop = bottomSheetBackdrop,
                                        accentColor = sliderAccentColor,
                                        trackColor = sliderTrackColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("大", style = labelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
                                }
                                
                                Spacer(modifier = Modifier.height(28.dp))
                                
                                Text("主题色彩", style = headerStyle)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LiquidButton(
                                        onClick = { viewModel.setTheme(0) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 0) Color.White.copy(0.35f) else Color.White.copy(0.08f),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Box(Modifier.size(15.dp).background(Color.White, CircleShape).border(1.5.dp, Color.Black.copy(0.25f), CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                        Text("亮白", style = buttonTextStyle, fontWeight = if (themeIndex == 0) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setTheme(1) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 1) Color(0xFFFDF6E3).copy(alpha = 0.45f) else Color(0xFFFDF6E3).copy(alpha = 0.12f),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Box(Modifier.size(15.dp).background(Color(0xFFFDF6E3), CircleShape).border(1.5.dp, Color.Black.copy(0.25f), CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                        Text("护眼", style = buttonTextStyle, fontWeight = if (themeIndex == 1) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setTheme(2) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 2) Color(0xFF1E1E22).copy(alpha = 0.55f) else Color(0xFF1E1E22).copy(alpha = 0.15f),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Box(Modifier.size(15.dp).background(Color(0xFF1E1E22), CircleShape).border(1.5.dp, Color.White.copy(0.35f), CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                        Text("夜间", style = buttonTextStyle, fontWeight = if (themeIndex == 2) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(28.dp))
                                
                                Text("字体转换", style = headerStyle)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val activeSurface = if (themeIndex == 2) Color.White.copy(0.30f) else Color.White.copy(0.35f)
                                    val inactiveSurface = if (themeIndex == 2) Color.White.copy(0.08f) else Color.White.copy(0.08f)

                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(0) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 0) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("原文", style = buttonTextStyle, fontWeight = if (chineseMode == 0) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(1) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 1) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("简体", style = buttonTextStyle, fontWeight = if (chineseMode == 1) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(2) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 2) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("繁体", style = buttonTextStyle, fontWeight = if (chineseMode == 2) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(28.dp))
                                
                                Text("自定义字体", style = headerStyle)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val activeSurface = if (themeIndex == 2) Color.White.copy(0.30f) else Color.White.copy(0.35f)
                                    val inactiveSurface = if (themeIndex == 2) Color.White.copy(0.08f) else Color.White.copy(0.08f)
                                    val isCustom = !customFontUri.isNullOrEmpty()

                                    LiquidButton(
                                        onClick = { viewModel.setCustomFontUri(null) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (!isCustom) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("系统默认", style = buttonTextStyle, fontWeight = if (!isCustom) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = { fontLauncher.launch("*/*") },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (isCustom) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("导入字体", style = buttonTextStyle, fontWeight = if (isCustom) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(28.dp))
                                
                                Text("翻页方式", style = headerStyle)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val activeSurface = if (themeIndex == 2) Color.White.copy(0.30f) else Color.White.copy(0.35f)
                                    val inactiveSurface = if (themeIndex == 2) Color.White.copy(0.08f) else Color.White.copy(0.08f)

                                    LiquidButton(
                                        onClick = {
                                            if (pageTurnMode != 0) {
                                                val targetFlat = pages.getOrNull(pagedCurrentIndex)?.flatItemIndex ?: 0
                                                viewModel.setPageTurnMode(0)
                                                coroutineScope.launch { listState.scrollToItem(targetFlat) }
                                            }
                                        },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (pageTurnMode == 0) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("上下滚动", style = buttonTextStyle, fontWeight = if (pageTurnMode == 0) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                    LiquidButton(
                                        onClick = {
                                            if (pageTurnMode != 1) {
                                                val currentFlat = listState.firstVisibleItemIndex
                                                val matchedPage = pages.indexOfFirst { it.flatItemIndex >= currentFlat }.coerceAtLeast(0)
                                                pagedCurrentIndex = matchedPage
                                                viewModel.setPageTurnMode(1)
                                            }
                                        },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (pageTurnMode == 1) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("左右翻页", style = buttonTextStyle, fontWeight = if (pageTurnMode == 1) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    }
                                }

                                AnimatedVisibility(
                                    visible = pageTurnMode == 1,
                                    enter = expandVertically(spring(0.78f, 320f)) + fadeIn(),
                                    exit = shrinkVertically(spring(0.82f, 340f)) + fadeOut()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text("翻页动画", style = headerStyle)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val animOptions = listOf(
                                                0 to "仿真",
                                                1 to "平移",
                                                2 to "覆盖",
                                                3 to "淡入",
                                                4 to "无"
                                            )
                                            val activeSurface = if (themeIndex == 2) Color.White.copy(0.30f) else Color.White.copy(0.35f)
                                            val inactiveSurface = if (themeIndex == 2) Color.White.copy(0.08f) else Color.White.copy(0.08f)

                                            animOptions.forEach { (styleIndex, label) ->
                                                val isSelected = pageAnimStyle == styleIndex
                                                LiquidButton(
                                                    onClick = { viewModel.setPageAnimStyle(styleIndex) },
                                                    backdrop = readerBackdrop,
                                                    surfaceColor = if (isSelected) activeSurface else inactiveSurface,
                                                    modifier = Modifier.weight(1f).height(42.dp)
                                                ) {
                                                    Text(
                                                        label,
                                                        style = buttonTextStyle.copy(fontSize = 13.sp),
                                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }

            // 3. Chapter Directory / TOC Liquid Glass Modal Sheet
            AnimatedVisibility(
                visible = showTocSheet,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            detectTapGestures { showTocSheet = false }
                        }
                )
            }

            AnimatedVisibility(
                visible = showTocSheet,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) + fadeIn(tween(250)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(220)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val currentChapterIndex = if (pageTurnMode == 0) {
                    flatItems.getOrNull(listState.firstVisibleItemIndex)?.chapterIndex ?: 0
                } else {
                    pages.getOrNull(pagedCurrentIndex)?.chapterIndex ?: 0
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .drawBackdrop(
                            backdrop = readerBackdrop,
                            shape = { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) },
                            effects = {
                                vibrancy()
                                blur(12.dp.toPx())
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                val surfaceColor = if (themeIndex == 2) Color(0xFF1E1E24).copy(0.92f) else Color(0xFFF8FAFC).copy(0.92f)
                                drawRect(surfaceColor)
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
                    ) {
                        // TOC Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "目录",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "共 ${if (tocList.isNotEmpty()) tocList.size else parsedChapters.size} 章",
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }

                            LiquidButton(
                                onClick = { showTocSheet = false },
                                backdrop = readerBackdrop,
                                surfaceColor = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = textColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Chapters LazyColumn (Rich EPUB TOC with exact chapter and section names)
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (tocList.isNotEmpty()) {
                                itemsIndexed(tocList) { idx, tocItem ->
                                    val isCurrent = if (pageTurnMode == 0) {
                                        val curFlat = flatItems.getOrNull(listState.firstVisibleItemIndex)
                                        curFlat != null && parsedChapters.getOrNull(curFlat.chapterIndex)?.title == tocItem.title
                                    } else {
                                        pages.getOrNull(pagedCurrentIndex)?.chapterTitle == tocItem.title
                                    }
                                    val activeItemBg = if (themeIndex == 2) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.35f)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (isCurrent) Modifier.background(activeItemBg)
                                                else Modifier
                                            )
                                            .clickable {
                                                val withoutAnchor = tocItem.href.substringBefore("#")
                                                var matchedFlat = flatItems.indexOfFirst {
                                                    it is FlatReaderItem.Title && (it.title.trim().equals(tocItem.title.trim(), ignoreCase = true) || it.title.contains(tocItem.title) || tocItem.title.contains(it.title))
                                                }
                                                if (matchedFlat < 0) {
                                                    val matchedCh = parsedChapters.indexOfFirst {
                                                        it.title.trim().equals(tocItem.title.trim(), ignoreCase = true) || it.title.contains(tocItem.title) || tocItem.title.contains(it.title)
                                                    }
                                                    if (matchedCh >= 0) {
                                                        matchedFlat = flatItems.indexOfFirst { it.chapterIndex == matchedCh }
                                                    }
                                                }
                                                if (matchedFlat < 0) {
                                                    val chIdx = viewModel.epubBook.value?.chapters?.indexOfFirst { it.href.contains(withoutAnchor) } ?: -1
                                                    if (chIdx >= 0) {
                                                        matchedFlat = flatItems.indexOfFirst { it.chapterIndex == chIdx }
                                                    }
                                                }
                                                if (matchedFlat >= 0) {
                                                    if (pageTurnMode == 0) {
                                                        coroutineScope.launch { listState.scrollToItem(matchedFlat) }
                                                        viewModel.saveProgress(matchedFlat, 0)
                                                    } else {
                                                        val targetPage = pages.indexOfFirst { it.flatItemIndex >= matchedFlat }.coerceAtLeast(0)
                                                        pagedCurrentIndex = targetPage
                                                        val cur = pages.getOrNull(targetPage)
                                                        if (cur != null) viewModel.saveProgress(cur.flatItemIndex, 0)
                                                    }
                                                    showTocSheet = false
                                                }
                                            }
                                            .padding(
                                                start = (tocItem.level * 14 + 14).dp,
                                                end = 14.dp,
                                                top = 11.dp,
                                                bottom = 11.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (tocItem.level == 0) {
                                            Text(
                                                text = "%02d".format(idx + 1),
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.45f),
                                                modifier = Modifier.width(28.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(if (isCurrent) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.4f), CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tocItem.title,
                                            fontSize = if (tocItem.level == 0) 14.sp else 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else if (tocItem.level == 0) FontWeight.Medium else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(parsedChapters) { idx, chapter ->
                                    val isCurrent = idx == currentChapterIndex
                                    val activeItemBg = if (themeIndex == 2) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.35f)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (isCurrent) Modifier.background(activeItemBg)
                                                else Modifier
                                            )
                                            .clickable {
                                                if (pageTurnMode == 0) {
                                                    val targetFlat = flatItems.indexOfFirst { it.chapterIndex == idx }
                                                    if (targetFlat >= 0) {
                                                        coroutineScope.launch { listState.scrollToItem(targetFlat) }
                                                        viewModel.saveProgress(targetFlat, 0)
                                                    }
                                                } else {
                                                    val targetPage = pages.indexOfFirst { it.chapterIndex == idx }
                                                    if (targetPage >= 0) {
                                                        pagedCurrentIndex = targetPage
                                                        val cur = pages.getOrNull(targetPage)
                                                        if (cur != null) viewModel.saveProgress(cur.flatItemIndex, 0)
                                                    }
                                                }
                                                showTocSheet = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "%02d".format(idx + 1),
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.45f),
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = chapter.title.ifBlank { "第 ${idx + 1} 章" },
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ThemeButton(color: Color, name: String, isSelected: Boolean, textColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape)
                .then(
                    if (isSelected) Modifier.padding(4.dp).background(color, CircleShape).clip(CircleShape)
                    else Modifier
                )
        ) {
            if (isSelected) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.2f), CircleShape))
                Icon(
                    imageVector = Icons.Filled.Settings, // You can use check icon instead
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, fontSize = 12.sp, color = if (isSelected) textColor else textColor.copy(alpha = 0.5f))
    }
}
