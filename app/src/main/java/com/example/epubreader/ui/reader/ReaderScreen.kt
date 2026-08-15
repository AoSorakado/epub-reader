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
    var showTocSheet by remember { mutableStateOf(false) }
    var settingsButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var tocButtonBounds by remember { mutableStateOf(Rect.Zero) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (showTocSheet) {
            showTocSheet = false
        } else if (showSettings) {
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

    val settingsMorphProgress by animateFloatAsState(
        targetValue = if (showSettings) 1f else 0f,
        animationSpec = if (showSettings) {
            spring(
                dampingRatio = 0.76f,
                stiffness = 280f
            )
        } else {
            spring(
                dampingRatio = 0.82f,
                stiffness = 250f
            )
        },
        label = "SettingsMorphProgress"
    )

    val tocMorphProgress by animateFloatAsState(
        targetValue = if (showTocSheet) 1f else 0f,
        animationSpec = if (showTocSheet) {
            spring(
                dampingRatio = 0.76f,
                stiffness = 280f
            )
        } else {
            spring(
                dampingRatio = 0.82f,
                stiffness = 250f
            )
        },
        label = "TocMorphProgress"
    )

    // Button Jelly Pulse upon collapse
    val settingsPulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val tocPulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val morphScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showSettings) {
        if (!showSettings && settingsMorphProgress > 0f) {
            kotlinx.coroutines.delay(200)
            settingsPulseScale.snapTo(1.16f)
            settingsPulseScale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = 340f)
            )
        }
    }

    LaunchedEffect(showTocSheet) {
        if (!showTocSheet && tocMorphProgress > 0f) {
            kotlinx.coroutines.delay(200)
            tocPulseScale.snapTo(1.16f)
            tocPulseScale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = 340f)
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
            var containerWidthPx by remember { mutableFloatStateOf(0f) }
            var containerHeightPx by remember { mutableFloatStateOf(0f) }

            var pages by remember { mutableStateOf<List<com.example.epubreader.ui.reader.ReaderPage>>(emptyList()) }

            LaunchedEffect(
                flatItems,
                chineseMode,
                containerWidthPx,
                containerHeightPx,
                textSize,
                lineHeightMult,
                paragraphSpacing
            ) {
                if (containerWidthPx > 100f && containerHeightPx > 200f && flatItems.isNotEmpty()) {
                    val contentWidthPx = containerWidthPx - with(density) { 36.dp.toPx() }
                    val contentHeightPx = containerHeightPx - with(density) { 40.dp.toPx() }
                    val textSizePx = with(density) { textSize.sp.toPx() }
                    val lineHeightPx = (textSizePx * lineHeightMult).coerceAtLeast(textSizePx * 1.15f)
                    val paragraphSpacingPx = with(density) { paragraphSpacing.dp.toPx() }

                    val computedPages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        ReaderPagination.paginate(
                            flatItems = flatItems,
                            chineseMode = chineseMode,
                            contentWidthPx = contentWidthPx,
                            contentHeightPx = contentHeightPx,
                            textSizePx = textSizePx,
                            lineHeightPx = lineHeightPx,
                            paragraphSpacingPx = paragraphSpacingPx
                        )
                    }
                    pages = computedPages
                }
            }

            var pagedCurrentIndex by remember { mutableStateOf(0) }
            var hasInitializedPagedIndex by remember { mutableStateOf(false) }

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
                                if (showTocSheet) {
                                    showTocSheet = false
                                } else if (showSettings) {
                                    showSettings = false
                                } else {
                                    showToolbars = !showToolbars
                                }
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
                            if (showTocSheet) {
                                showTocSheet = false
                            } else if (showSettings) {
                                showSettings = false
                            } else {
                                showToolbars = !showToolbars
                            }
                        },
                        onOpenToc = {
                            showSettings = false
                            showTocSheet = true
                        },
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
                visible = showToolbars && !showSettings && !showTocSheet,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Back Button (Left)
                LiquidButton(
                    onClick = {
                        if (onBackClick != null) {
                            onBackClick()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    backdrop = readerBackdrop,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                }

                // 2. Reading Progress Liquid Capsule (Center)
                LiquidButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isCapsuleExpanded = !isCapsuleExpanded
                    },
                    backdrop = readerBackdrop,
                    shape = CircleShape,
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 340f
                            ),
                            alignment = Alignment.Center
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = isCapsuleExpanded,
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f)) + scaleIn(initialScale = 0.90f, transformOrigin = TransformOrigin.Center)) togetherWith
                                (fadeOut(animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f)) + scaleOut(targetScale = 0.90f, transformOrigin = TransformOrigin.Center))
                            },
                            label = "capsuleAnimatedContent"
                        ) { expanded ->
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

                // Right: Settings Button
                LiquidButton(
                    onClick = {
                        showTocSheet = false
                        showSettings = !showSettings
                    },
                    backdrop = readerBackdrop,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .onGloballyPositioned { coordinates ->
                            settingsButtonBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = if (settingsMorphProgress > 0.001f) 0f else 1f
                            scaleX = settingsPulseScale.value
                            scaleY = settingsPulseScale.value
                        }
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = textColor)
                }
            }
        }

        // 2. Bottom Floating Liquid Toolbar (TOC Button + Chapter Info Pill)
        AnimatedVisibility(
            visible = showToolbars && !showSettings && !showTocSheet,
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
                        showSettings = false
                        showTocSheet = true
                    },
                    backdrop = readerBackdrop,
                    surfaceColor = if (themeIndex == 2) Color.White.copy(0.12f) else Color.White.copy(0.28f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .onGloballyPositioned { coordinates ->
                            tocButtonBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = if (tocMorphProgress > 0.001f) 0f else 1f
                            scaleX = tocPulseScale.value
                            scaleY = tocPulseScale.value
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
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

                // Right: Current Chapter Name Glass Pill (Display only, non-clickable)
                LiquidButton(
                    onClick = {},
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

        // 2. Liquid Shape Morphing Container for Settings (Button expands into Dialog with damping & jelly physics)
        if (settingsMorphProgress > 0.001f || showSettings) {
            // Scrim overlay with gentle dimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = settingsMorphProgress * 0.45f }
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

                val dialogWidthPx = with(density) { (maxWidth - 36.dp).toPx().coerceAtMost(460.dp.toPx()) }
                val dialogHeightPx = with(density) { 560.dp.toPx().coerceAtMost(maxHeight.toPx() - 72.dp.toPx()) }
                val dialogLeft = (screenWidthPx - dialogWidthPx) / 2f
                val dialogTop = (screenHeightPx - dialogHeightPx) / 2f
                val dialogBounds = Rect(dialogLeft, dialogTop, dialogLeft + dialogWidthPx, dialogTop + dialogHeightPx)

                val currentLeft = androidx.compose.ui.util.lerp(btnBounds.left, dialogBounds.left, settingsMorphProgress)
                val currentTop = androidx.compose.ui.util.lerp(btnBounds.top, dialogBounds.top, settingsMorphProgress)
                val currentWidth = androidx.compose.ui.util.lerp(btnBounds.width, dialogWidthPx, settingsMorphProgress).coerceAtLeast(1f)
                val currentHeight = androidx.compose.ui.util.lerp(btnBounds.height, dialogHeightPx, settingsMorphProgress).coerceAtLeast(1f)
                val currentCornerRadius = androidx.compose.ui.util.lerp(btnBounds.height / 2f, with(density) { 28.dp.toPx() }, settingsMorphProgress).coerceAtLeast(0f)

                val glassTextColor = if (themeIndex == 2) Color.White else Color(0xFF1C1C1E)

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
                                blur(androidx.compose.ui.util.lerp(3f, 8f, settingsMorphProgress).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, settingsMorphProgress).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, settingsMorphProgress).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (themeIndex == 2) 0.10f else 0.12f))
                            },
                            exportedBackdrop = bottomSheetBackdrop
                        )
                        .clip(RoundedCornerShape(with(density) { currentCornerRadius.coerceAtLeast(0f).toDp() }))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent taps from dismissing through to scrim
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Morphing Icon: Settings Icon centered inside button, fades out as it expands
                    if (settingsMorphProgress < 0.5f) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    alpha = (1f - settingsMorphProgress * 2.2f).coerceIn(0f, 1f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = textColor)
                        }
                    }

                    // Morphing Content: Settings controls fade in as the container reaches full dialog size
                    if (settingsMorphProgress > 0.15f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((settingsMorphProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                }
                                .padding(top = 22.dp, bottom = 20.dp, start = 22.dp, end = 22.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "排版调整",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = glassTextColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (themeIndex == 2) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
                                            .border(0.6.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            .clickable { showSettings = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", color = glassTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                var localTextSize by remember(textSize) { mutableFloatStateOf(textSize) }
                                var localLineHeight by remember(lineHeightMult) { mutableFloatStateOf(lineHeightMult) }
                                var localParagraphSpacing by remember(paragraphSpacing) { mutableFloatStateOf(paragraphSpacing) }

                                LaunchedEffect(localTextSize) {
                                    if (localTextSize != textSize) {
                                        kotlinx.coroutines.delay(100)
                                        viewModel.setTextSize(localTextSize)
                                    }
                                }
                                LaunchedEffect(localLineHeight) {
                                    if (localLineHeight != lineHeightMult) {
                                        kotlinx.coroutines.delay(100)
                                        viewModel.setLineHeightMult(localLineHeight)
                                    }
                                }
                                LaunchedEffect(localParagraphSpacing) {
                                    if (localParagraphSpacing != paragraphSpacing) {
                                        kotlinx.coroutines.delay(100)
                                        viewModel.setParagraphSpacing(localParagraphSpacing)
                                    }
                                }

                                val headerStyle = MaterialTheme.typography.titleSmall.copy(
                                    color = glassTextColor.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                val buttonTextStyle = MaterialTheme.typography.labelLarge.copy(
                                    color = glassTextColor,
                                    fontSize = 14.sp
                                )

                                val isGlobalDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
                                val globalThemeAccent = com.example.epubreader.ui.theme.getThemeAccentColor(
                                    theme = appTheme,
                                    customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
                                )

                                val themeSelectedBg = globalThemeAccent.copy(alpha = if (isGlobalDark) 0.32f else 0.22f)
                                val themeUnselectedBg = if (isGlobalDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.40f)

                                // 1. Theme Color Selection
                                Text("阅读主题", style = headerStyle)
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isCustomTheme = isCustomThemeThreeColors
                                    val lightColor = if (isCustomTheme) customColors.getOrElse(0) { Color(0xFFFAF9F6) } else Color(0xFFFAF9F6)
                                    val sepiaColor = if (isCustomTheme) customColors.getOrElse(1) { Color(0xFFF4ECD8) } else Color(0xFFF4ECD8)
                                    val darkColor = if (isCustomTheme) customColors.getOrElse(2) { Color(0xFF1C1C1E) } else Color(0xFF1C1C1E)

                                    LiquidButton(
                                        onClick = { viewModel.setTheme(0) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 0) themeSelectedBg else themeUnselectedBg,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(14.dp).background(lightColor, CircleShape).border(1.dp, Color.Black.copy(0.1f), CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "默认",
                                                style = buttonTextStyle,
                                                color = if (themeIndex == 0) globalThemeAccent else glassTextColor,
                                                fontWeight = if (themeIndex == 0) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LiquidButton(
                                        onClick = { viewModel.setTheme(1) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 1) themeSelectedBg else themeUnselectedBg,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(14.dp).background(sepiaColor, CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "羊皮纸",
                                                style = buttonTextStyle,
                                                color = if (themeIndex == 1) globalThemeAccent else glassTextColor,
                                                fontWeight = if (themeIndex == 1) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LiquidButton(
                                        onClick = { viewModel.setTheme(2) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (themeIndex == 2) themeSelectedBg else themeUnselectedBg,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(14.dp).background(darkColor, CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "夜间",
                                                style = buttonTextStyle,
                                                color = if (themeIndex == 2) globalThemeAccent else glassTextColor,
                                                fontWeight = if (themeIndex == 2) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                val sliderAccentColor = globalThemeAccent
                                val sliderTrackColor = if (isGlobalDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)

                                // 2. Font Size Control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("字号大小", style = headerStyle)
                                    Text("${localTextSize.toInt()}sp", style = headerStyle.copy(fontWeight = FontWeight.ExtraBold, color = globalThemeAccent))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LiquidSlider(
                                    value = { localTextSize },
                                    onValueChange = { localTextSize = it },
                                    valueRange = 12f..36f,
                                    visibilityThreshold = 0.5f,
                                    backdrop = readerBackdrop,
                                    accentColor = sliderAccentColor,
                                    trackColor = sliderTrackColor
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // 3. Line Height Control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("行高间距", style = headerStyle)
                                    Text(String.format(java.util.Locale.US, "%.1fx", localLineHeight), style = headerStyle.copy(fontWeight = FontWeight.ExtraBold, color = globalThemeAccent))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LiquidSlider(
                                    value = { localLineHeight },
                                    onValueChange = { localLineHeight = it },
                                    valueRange = 1.0f..2.5f,
                                    visibilityThreshold = 0.05f,
                                    backdrop = readerBackdrop,
                                    accentColor = sliderAccentColor,
                                    trackColor = sliderTrackColor
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // 4. Paragraph Spacing Control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("段落间距", style = headerStyle)
                                    Text("${localParagraphSpacing.toInt()}dp", style = headerStyle.copy(fontWeight = FontWeight.ExtraBold, color = globalThemeAccent))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LiquidSlider(
                                    value = { localParagraphSpacing },
                                    onValueChange = { localParagraphSpacing = it },
                                    valueRange = 0f..32f,
                                    visibilityThreshold = 1f,
                                    backdrop = readerBackdrop,
                                    accentColor = sliderAccentColor,
                                    trackColor = sliderTrackColor
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                val activeSurface = globalThemeAccent.copy(alpha = if (isGlobalDark) 0.32f else 0.22f)
                                val inactiveSurface = if (isGlobalDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.40f)

                                // 5. Simplified / Traditional Chinese Converter
                                Text("简繁转换", style = headerStyle)
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(0) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 0) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "原文",
                                            style = buttonTextStyle,
                                            color = if (chineseMode == 0) globalThemeAccent else glassTextColor,
                                            fontWeight = if (chineseMode == 0) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(1) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 1) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "简体",
                                            style = buttonTextStyle,
                                            color = if (chineseMode == 1) globalThemeAccent else glassTextColor,
                                            fontWeight = if (chineseMode == 1) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                    LiquidButton(
                                        onClick = { viewModel.setChineseMode(2) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (chineseMode == 2) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "繁體",
                                            style = buttonTextStyle,
                                            color = if (chineseMode == 2) globalThemeAccent else glassTextColor,
                                            fontWeight = if (chineseMode == 2) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 6. Custom Font Selection
                                Text("阅读字体", style = headerStyle)
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiquidButton(
                                        onClick = { viewModel.setCustomFontUri(null) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (customFontUri == null) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "系统默认",
                                            style = buttonTextStyle,
                                            color = if (customFontUri == null) globalThemeAccent else glassTextColor,
                                            fontWeight = if (customFontUri == null) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }

                                    LiquidButton(
                                        onClick = { fontLauncher.launch("*/*") },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (customFontUri != null) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            if (customFontUri != null) "自定义字体" else "选择字体",
                                            style = buttonTextStyle,
                                            color = if (customFontUri != null) globalThemeAccent else glassTextColor,
                                            fontWeight = if (customFontUri != null) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 7. Page Turn Mode
                                Text("翻页模式", style = headerStyle)
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiquidButton(
                                        onClick = { viewModel.setPageTurnMode(0) },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (pageTurnMode == 0) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "上下滚动",
                                            style = buttonTextStyle,
                                            color = if (pageTurnMode == 0) globalThemeAccent else glassTextColor,
                                            fontWeight = if (pageTurnMode == 0) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                    LiquidButton(
                                        onClick = { 
                                            viewModel.setPageTurnMode(1)
                                            if (pageTurnMode == 0) {
                                                val flatIdx = listState.firstVisibleItemIndex
                                                viewModel.saveProgress(flatIdx, 0)
                                            }
                                        },
                                        backdrop = readerBackdrop,
                                        surfaceColor = if (pageTurnMode == 1) activeSurface else inactiveSurface,
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text(
                                            "左右翻页",
                                            style = buttonTextStyle,
                                            color = if (pageTurnMode == 1) globalThemeAccent else glassTextColor,
                                            fontWeight = if (pageTurnMode == 1) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                }

                                if (pageTurnMode == 1) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("翻页动画", style = headerStyle)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    val animStyles = listOf("仿真", "平移", "覆盖", "淡入", "无动画")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        animStyles.forEachIndexed { index, name ->
                                            LiquidButton(
                                                onClick = { viewModel.setPageAnimStyle(index) },
                                                backdrop = readerBackdrop,
                                                surfaceColor = if (pageAnimStyle == index) activeSurface else inactiveSurface,
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Text(
                                                    name,
                                                    style = buttonTextStyle.copy(fontSize = 12.5.sp),
                                                    color = if (pageAnimStyle == index) globalThemeAccent else glassTextColor,
                                                    fontWeight = if (pageAnimStyle == index) FontWeight.ExtraBold else FontWeight.SemiBold
                                                )
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
        }

        // 3. Liquid Shape Morphing Container for TOC / 目录 (Bottom-Left button expands into TOC Dialog with damping & jelly physics)
        if (tocMorphProgress > 0.001f || showTocSheet) {
            // Scrim overlay with gentle dimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = tocMorphProgress * 0.45f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showTocSheet = false }
                    )
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = LocalDensity.current
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val fallbackTocBtnBounds = Rect(
                    with(density) { 24.dp.toPx() },
                    screenHeightPx - with(density) { 80.dp.toPx() },
                    with(density) { 108.dp.toPx() },
                    screenHeightPx - with(density) { 36.dp.toPx() }
                )
                val btnBounds = if (tocButtonBounds != Rect.Zero) tocButtonBounds else fallbackTocBtnBounds

                val dialogWidthPx = with(density) { (maxWidth - 36.dp).toPx().coerceAtMost(460.dp.toPx()) }
                val dialogHeightPx = with(density) { 560.dp.toPx().coerceAtMost(maxHeight.toPx() - 72.dp.toPx()) }
                val dialogLeft = (screenWidthPx - dialogWidthPx) / 2f
                val dialogTop = (screenHeightPx - dialogHeightPx) / 2f
                val dialogBounds = Rect(dialogLeft, dialogTop, dialogLeft + dialogWidthPx, dialogTop + dialogHeightPx)

                val currentLeft = androidx.compose.ui.util.lerp(btnBounds.left, dialogBounds.left, tocMorphProgress)
                val currentTop = androidx.compose.ui.util.lerp(btnBounds.top, dialogBounds.top, tocMorphProgress)
                val currentWidth = androidx.compose.ui.util.lerp(btnBounds.width, dialogWidthPx, tocMorphProgress).coerceAtLeast(1f)
                val currentHeight = androidx.compose.ui.util.lerp(btnBounds.height, dialogHeightPx, tocMorphProgress).coerceAtLeast(1f)
                val currentCornerRadius = androidx.compose.ui.util.lerp(btnBounds.height / 2f, with(density) { 28.dp.toPx() }, tocMorphProgress).coerceAtLeast(0f)

                val currentChapterIndex = if (pageTurnMode == 0) {
                    flatItems.getOrNull(listState.firstVisibleItemIndex)?.chapterIndex ?: 0
                } else {
                    pages.getOrNull(pagedCurrentIndex)?.chapterIndex ?: 0
                }

                val isGlobalDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
                val globalThemeAccent = com.example.epubreader.ui.theme.getThemeAccentColor(
                    theme = appTheme,
                    customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
                )
                val glassTextColor = if (isGlobalDark) Color.White else Color(0xFF1C1C1E)

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
                                blur(androidx.compose.ui.util.lerp(3f, 8f, tocMorphProgress).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, tocMorphProgress).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, tocMorphProgress).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isGlobalDark) 0.10f else 0.12f))
                            },
                            exportedBackdrop = bottomSheetBackdrop
                        )
                        .clip(RoundedCornerShape(with(density) { currentCornerRadius.coerceAtLeast(0f).toDp() }))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Intercept clicks so tapping inside does not dismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Morphing Icon: 目录 button text & icon centered, fades out as it expands
                    if (tocMorphProgress < 0.5f) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = (1f - tocMorphProgress * 2.2f).coerceIn(0f, 1f)
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
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

                    // Morphing Content: TOC Header + Chapter List LazyColumn
                    if (tocMorphProgress > 0.15f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((tocMorphProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                }
                                .padding(top = 22.dp, start = 22.dp, end = 22.dp, bottom = 18.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
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
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = glassTextColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "共 ${if (tocList.isNotEmpty()) tocList.size else parsedChapters.size} 章",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = glassTextColor.copy(alpha = 0.5f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isGlobalDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
                                            .border(0.6.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            .clickable { showTocSheet = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", color = glassTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

                                            val onItemClick = {
                                                val withoutAnchor = tocItem.href.substringBefore("#")
                                                val fileNameOnly = withoutAnchor.substringAfterLast("/")
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
                                                    val chIdx = viewModel.epubBook.value?.chapters?.indexOfFirst {
                                                        it.href.contains(withoutAnchor) || withoutAnchor.contains(it.href) || it.href.substringAfterLast("/") == fileNameOnly
                                                    } ?: -1
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
                                                    showToolbars = false
                                                }
                                            }

                                            if (isCurrent) {
                                                LiquidButton(
                                                    onClick = onItemClick,
                                                    backdrop = readerBackdrop,
                                                    shape = RoundedCornerShape(12.dp),
                                                    surfaceColor = globalThemeAccent.copy(alpha = if (isGlobalDark) 0.32f else 0.22f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 44.dp)
                                                        .padding(start = (tocItem.level * 14).dp)
                                                ) {
                                                    if (tocItem.level == 0) {
                                                        Text(
                                                            text = "${(idx + 1).toString().padStart(2, '0')}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = globalThemeAccent,
                                                            modifier = Modifier.width(28.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(end = 8.dp)
                                                                .size(5.dp)
                                                                .background(globalThemeAccent, CircleShape)
                                                        )
                                                    }

                                                    Text(
                                                        text = tocItem.title,
                                                        fontSize = if (tocItem.level == 0) 14.5.sp else 13.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = globalThemeAccent,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(globalThemeAccent, CircleShape)
                                                    )
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable(onClick = onItemClick)
                                                        .padding(
                                                            start = (12 + (tocItem.level * 14)).dp,
                                                            end = 12.dp,
                                                            top = 10.dp,
                                                            bottom = 10.dp
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (tocItem.level == 0) {
                                                        Text(
                                                            text = "${(idx + 1).toString().padStart(2, '0')}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = glassTextColor.copy(alpha = 0.4f),
                                                            modifier = Modifier.width(28.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(end = 10.dp)
                                                                .size(4.dp)
                                                                .background(glassTextColor.copy(alpha = 0.35f), CircleShape)
                                                        )
                                                    }

                                                    Text(
                                                        text = tocItem.title,
                                                        fontSize = if (tocItem.level == 0) 14.5.sp else 13.5.sp,
                                                        fontWeight = if (tocItem.level == 0) FontWeight.Bold else FontWeight.Normal,
                                                        color = glassTextColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        itemsIndexed(parsedChapters) { idx, chapter ->
                                            val isCurrent = idx == currentChapterIndex
                                            val onChapterClick = {
                                                val flatIndex = flatItems.indexOfFirst { it.chapterIndex == idx }
                                                if (flatIndex >= 0) {
                                                    if (pageTurnMode == 0) {
                                                        coroutineScope.launch { listState.scrollToItem(flatIndex) }
                                                        viewModel.saveProgress(flatIndex, 0)
                                                    } else {
                                                        val targetPage = pages.indexOfFirst { it.chapterIndex == idx }.coerceAtLeast(0)
                                                        pagedCurrentIndex = targetPage
                                                        val cur = pages.getOrNull(targetPage)
                                                        if (cur != null) viewModel.saveProgress(cur.flatItemIndex, 0)
                                                    }
                                                }
                                                showTocSheet = false
                                                showToolbars = false
                                            }

                                            if (isCurrent) {
                                                LiquidButton(
                                                    onClick = onChapterClick,
                                                    backdrop = readerBackdrop,
                                                    shape = RoundedCornerShape(12.dp),
                                                    surfaceColor = globalThemeAccent.copy(alpha = if (isGlobalDark) 0.32f else 0.22f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 44.dp)
                                                ) {
                                                    Text(
                                                        text = "${(idx + 1).toString().padStart(2, '0')}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = globalThemeAccent,
                                                        modifier = Modifier.width(28.dp)
                                                    )

                                                    Text(
                                                        text = chapter.title.ifBlank { "第 ${idx + 1} 章" },
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = globalThemeAccent,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(globalThemeAccent, CircleShape)
                                                    )
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable(onClick = onChapterClick)
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${(idx + 1).toString().padStart(2, '0')}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = glassTextColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.width(28.dp)
                                                    )

                                                    Text(
                                                        text = chapter.title.ifBlank { "第 ${idx + 1} 章" },
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        color = glassTextColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
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
