package com.example.epubreader.ui.reader

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    settingsViewModel: com.example.epubreader.ui.settings.SettingsViewModel? = null,
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

    val handleExit: () -> Unit = remember(onBackClick, navController, context) {
        {
            viewModel.uploadProgressToCloud(context)
            if (onBackClick != null) {
                onBackClick()
            } else {
                navController.popBackStack()
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.uploadProgressToCloud(context)
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (showTocSheet) {
            showTocSheet = false
        } else if (showSettings) {
            showSettings = false
        } else if (showToolbars) {
            showToolbars = false
        } else {
            handleExit()
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
    val chapterCharCounts by viewModel.chapterCharCounts.collectAsState()
    val chapterCumulative by viewModel.chapterCumulativeCharCounts.collectAsState()
    val totalChars by viewModel.totalCharCount.collectAsState()

    val effectiveSettingsViewModel: com.example.epubreader.ui.settings.SettingsViewModel = settingsViewModel
        ?: viewModel(
            viewModelStoreOwner = (context as? androidx.activity.ComponentActivity) ?: (context as androidx.lifecycle.ViewModelStoreOwner),
            factory = com.example.epubreader.ui.settings.SettingsViewModelFactory(context.applicationContext as Application)
        )
    val appTheme by effectiveSettingsViewModel.appTheme.collectAsState()
    val isCustomThemeThreeColors by effectiveSettingsViewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by effectiveSettingsViewModel.customColors.collectAsState()

    val isGlobalDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
    val globalThemeAccent = com.example.epubreader.ui.theme.getThemeAccentColor(
        theme = appTheme,
        customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
    )
    val isGlassDark = themeIndex == 2 || isGlobalDark
    val glassTextColor = if (isGlassDark) Color(0xFFF1F5F9) else Color(0xFF2B173A)
    val glassSecondaryColor = if (isGlassDark) Color(0xFF94A3B8) else Color(0xFF543866).copy(alpha = 0.85f)

    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val dialogWidthDp = minOf(configuration.screenWidthDp.dp - 36.dp, 460.dp)
    val dialogHeightDp = minOf(configuration.screenHeightDp.dp - 72.dp, 560.dp)
    val dialogWidthPx = with(density) { dialogWidthDp.toPx() }
    val dialogHeightPx = with(density) { dialogHeightDp.toPx() }

    val dialogCenterX = screenWidthPx / 2f
    val dialogCenterY = screenHeightPx / 2f

    val settingsAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isSettingsActive = showSettings || settingsAnim.value > 0.001f

    LaunchedEffect(showSettings) {
        if (showSettings) {
            settingsAnim.snapTo(0f)
            settingsAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = 220f,
                    visibilityThreshold = 0.0001f
                )
            )
        } else {
            settingsAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 360f,
                    visibilityThreshold = 0.0001f
                )
            )
        }
    }

    val tocAnim = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val isTocActive = showTocSheet || tocAnim.value > 0.001f

    LaunchedEffect(showTocSheet) {
        if (showTocSheet) {
            tocAnim.snapTo(0f)
            tocAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = 220f,
                    visibilityThreshold = 0.0001f
                )
            )
        } else {
            tocAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 360f,
                    visibilityThreshold = 0.0001f
                )
            )
        }
    }

    val morphScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

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
                try {
                    val tf = android.graphics.Typeface.createFromFile(fontFile)
                    if (tf != null) {
                        viewModel.setCustomFontUri(fontFile.absolutePath)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    val customFontFamily = remember(customFontUri) {
        if (!customFontUri.isNullOrEmpty() && File(customFontUri!!).exists()) {
            try {
                val tf = android.graphics.Typeface.createFromFile(File(customFontUri!!))
                if (tf != null) {
                    FontFamily(androidx.compose.ui.text.font.Typeface(tf))
                } else {
                    FontFamily.Default
                }
            } catch (e: Throwable) {
                FontFamily.Default
            }
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

    val initialPositionStr = bookEntity?.lastReadPosition?.split("_")
    val initialChapterIndex = initialPositionStr?.getOrNull(0)?.toIntOrNull() ?: 0
    val initialOffset = initialPositionStr?.getOrNull(1)?.toIntOrNull() ?: 0
    val initialNodeIndex = initialPositionStr?.getOrNull(2)?.toIntOrNull() ?: 0
    
    val initialFlatIndex = remember(flatItems, initialChapterIndex, initialNodeIndex) {
        flatItems.indexOfFirst {
            it.chapterIndex == initialChapterIndex && (it !is com.example.epubreader.ui.reader.FlatReaderItem.Node || it.nodeIndex >= initialNodeIndex)
        }.coerceAtLeast(0)
    }
    
    val readerTopPadding = maxOf(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        if (immersiveStatusBar) 28.dp else 12.dp
    ) + 12.dp

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
        paragraphSpacing,
        immersiveStatusBar
    ) {
        if (containerWidthPx > 100f && containerHeightPx > 200f && flatItems.isNotEmpty()) {
            val contentWidthPx = containerWidthPx - with(density) { 36.dp.toPx() }
            val topPaddingPx = with(density) { readerTopPadding.toPx() }
            val bottomPaddingPx = with(density) { 20.dp.toPx() }
            val contentHeightPx = containerHeightPx - topPaddingPx - bottomPaddingPx
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
                viewModel.saveProgress(current.chapterIndex, 0)
            }
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialChapterIndex.coerceIn(0, (parsedChapters.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    val continuousProgress by remember(pageTurnMode, pagedCurrentIndex, pages, chapterCharCounts, chapterCumulative, totalChars) {
        derivedStateOf {
            if (pageTurnMode == 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty() || totalChars <= 0) {
                    (bookEntity?.totalProgress ?: 0f)
                } else {
                    val firstItem = visibleItems.firstOrNull()
                    if (firstItem == null) return@derivedStateOf (bookEntity?.totalProgress ?: 0f)

                    // Index 0 is book header, index 1 is chapter 0, etc.
                    val chapterIdx = if (firstItem.index == 0) 0 else (firstItem.index - 1)
                    if (chapterIdx >= chapterCumulative.size) return@derivedStateOf 1f

                    val charsBefore = chapterCumulative.getOrNull(chapterIdx) ?: 0
                    val chapterLen = chapterCharCounts.getOrNull(chapterIdx) ?: 1

                    val itemTopOffset = (-firstItem.offset).toFloat().coerceAtLeast(0f)
                    val itemHeight = firstItem.size.toFloat().coerceAtLeast(1f)
                    val fractionInChapter = (itemTopOffset / itemHeight).coerceIn(0f, 1f)

                    val currentChars = charsBefore + (fractionInChapter * chapterLen)
                    (currentChars / totalChars.toFloat()).coerceIn(0f, 1f)
                }
            } else {
                if (pages.isEmpty()) {
                    (bookEntity?.totalProgress ?: 0f)
                } else {
                    ((pagedCurrentIndex + 1).toFloat() / pages.size.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Decoupled reading position tracking for continuous scrolling mode
    LaunchedEffect(listState, pageTurnMode) {
        if (pageTurnMode == 0) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    viewModel.updateReadingPosition(index)
                }
        }
    }

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
            
            val lifecycleOwner = LocalLifecycleOwner.current
            val currentProgressRef = rememberUpdatedState(continuousProgress)
            val currentListStateRef = rememberUpdatedState(listState)
            val currentPagesRef = rememberUpdatedState(pages)
            val currentPagedIndexRef = rememberUpdatedState(pagedCurrentIndex)
            val currentPageTurnModeRef = rememberUpdatedState(pageTurnMode)

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        if (currentPageTurnModeRef.value == 0) {
                            viewModel.saveProgress(
                                chapterIndex = currentListStateRef.value.firstVisibleItemIndex,
                                offset = currentListStateRef.value.firstVisibleItemScrollOffset,
                                progressOverride = currentProgressRef.value
                            )
                        } else {
                            val current = currentPagesRef.value.getOrNull(currentPagedIndexRef.value)
                            if (current != null) {
                                viewModel.saveProgress(
                                    chapterIndex = current.chapterIndex,
                                    offset = 0,
                                    progressOverride = currentProgressRef.value
                                )
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    if (currentPageTurnModeRef.value == 0) {
                        viewModel.saveProgress(
                            chapterIndex = currentListStateRef.value.firstVisibleItemIndex,
                            offset = currentListStateRef.value.firstVisibleItemScrollOffset,
                            progressOverride = currentProgressRef.value
                        )
                    } else {
                        val current = currentPagesRef.value.getOrNull(currentPagedIndexRef.value)
                        if (current != null) {
                            viewModel.saveProgress(
                                chapterIndex = current.chapterIndex,
                                offset = 0,
                                progressOverride = currentProgressRef.value
                            )
                        }
                    }
                }
            }

            val shouldCaptureBackdrop = showToolbars || isTocActive || isSettingsActive
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .then(if (shouldCaptureBackdrop) Modifier.layerBackdrop(readerBackdrop) else Modifier)
                    .onSizeChanged { size ->
                        containerWidthPx = size.width.toFloat()
                        containerHeightPx = size.height.toFloat()
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
                        contentPadding = PaddingValues(top = readerTopPadding, bottom = 100.dp, start = 24.dp, end = 24.dp)
                    ) {
                        item(key = "book_header", contentType = 0) {
                            Text(
                                text = bookEntity?.title ?: "未知书名",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 32.dp),
                                lineHeight = 40.sp
                            )
                        }
                        
                        itemsIndexed(
                            items = parsedChapters,
                            key = { cIdx, _ -> "ch_$cIdx" },
                            contentType = { _, _ -> 1 }
                        ) { cIdx, chapter ->
                            ScrollableChapterItem(
                                chapterIndex = cIdx,
                                chapter = chapter,
                                chineseMode = chineseMode,
                                customFontFamily = customFontFamily,
                                textSize = textSize,
                                lineHeightMult = lineHeightMult,
                                paragraphSpacing = paragraphSpacing,
                                textColor = textColor
                            )
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
                        customFontUri = customFontUri,
                        bookTitle = bookEntity?.title ?: "轻小说阅读",
                        topPadding = readerTopPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } // End of captured Box

            // --- Glass Overlays Layer ---
            CompositionLocalProvider(
                LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily)
            ) {
                // 1. Top Toolbar (Back, Reading Progress Capsule, and Settings Buttons)
                AnimatedVisibility(
                    visible = showToolbars,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                val currentChapterIndex by remember(pageTurnMode, pagedCurrentIndex) {
                    derivedStateOf {
                        if (pageTurnMode == 0) {
                            (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        } else {
                            pages.getOrNull(pagedCurrentIndex)?.chapterIndex ?: 0
                        }
                    }
                }
                val calculatedProgress = continuousProgress
                val progressPercentText = String.format(java.util.Locale.US, "%.1f", (calculatedProgress * 100f).coerceIn(0f, 100f))
                val estimatedTimeText = viewModel.getEstimatedRemainingTimeText(currentChapterIndex)
                val batteryAndTime = rememberBatteryAndTime()

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

            val topPadding = readerTopPadding

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Back Button (Left)
                LiquidButton(
                    onClick = { handleExit() },
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$estimatedTimeText · $progressPercentText%",
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "·",
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.sp,
                                        color = textColor.copy(alpha = 0.45f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = batteryAndTime.time,
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor.copy(alpha = 0.9f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MiniBatteryIndicator(
                                        level = batteryAndTime.level,
                                        isCharging = batteryAndTime.isCharging,
                                        tintColor = textColor
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${batteryAndTime.level}%",
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor.copy(alpha = 0.75f)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$progressPercentText%",
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "·",
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.sp,
                                        color = textColor.copy(alpha = 0.45f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = batteryAndTime.time,
                                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor.copy(alpha = 0.9f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MiniBatteryIndicator(
                                        level = batteryAndTime.level,
                                        isCharging = batteryAndTime.isCharging,
                                        tintColor = textColor
                                    )
                                }
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
                            alpha = if (settingsAnim.value > 0.001f) 0f else 1f
                        }
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = textColor)
                }
            }
        }

        // 2. Bottom Floating Liquid Toolbar (TOC Button + Chapter Info Pill)
        AnimatedVisibility(
            visible = showToolbars,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val currentChapterName by remember(pageTurnMode, pagedCurrentIndex) {
                derivedStateOf {
                    if (pageTurnMode == 0) {
                        parsedChapters.getOrNull(listState.firstVisibleItemIndex)?.title ?: "正文"
                    } else {
                        pages.getOrNull(pagedCurrentIndex)?.chapterTitle ?: "正文"
                    }
                }
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
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .onGloballyPositioned { coordinates ->
                            tocButtonBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = if (tocAnim.value > 0.001f) 0f else 1f
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
                    isInteractive = false,
                    backdrop = readerBackdrop,
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

        // 2. Liquid Shape Morphing Container for Settings (Pre-inflated, Zero-Frame-Drop GPU Fluid Transform)
        // Scrim overlay with gentle dimming
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { if (isSettingsActive) IntOffset.Zero else IntOffset(100000, 0) }
                .graphicsLayer { alpha = settingsAnim.value * 0.45f }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showSettings = false }
                )
        )

        val fallbackBtnBounds = Rect(
            screenWidthPx - with(density) { 60.dp.toPx() },
            with(density) { 56.dp.toPx() },
            screenWidthPx - with(density) { 16.dp.toPx() },
            with(density) { 100.dp.toPx() }
        )
        val btnBounds = if (settingsButtonBounds != Rect.Zero && settingsButtonBounds.width > 0f) settingsButtonBounds else fallbackBtnBounds

        val btnCenterX = btnBounds.left + btnBounds.width / 2f
        val btnCenterY = btnBounds.top + btnBounds.height / 2f

        val settingsDeltaX = btnCenterX - dialogCenterX
        val settingsDeltaY = btnCenterY - dialogCenterY

        val settingsInitialScaleX = (btnBounds.width / dialogWidthPx).coerceIn(0.02f, 1f)
        val settingsInitialScaleY = (btnBounds.height / dialogHeightPx).coerceIn(0.02f, 1f)

        val activeSurface = globalThemeAccent.copy(alpha = if (isGlassDark) 0.32f else 0.22f)
        val inactiveSurface = if (isGlassDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.38f)
        val sliderTrackColor = if (isGlassDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
        val sliderAccentColor = globalThemeAccent

        Box(
            modifier = Modifier
                .offset { if (isSettingsActive) IntOffset.Zero else IntOffset(100000, 0) }
                .layout { measurable, _ ->
                    val p = settingsAnim.value
                    val w = lerp(btnBounds.width, dialogWidthPx, p).roundToInt()
                    val h = lerp(btnBounds.height, dialogHeightPx, p).roundToInt()
                    val cx = lerp(btnCenterX, dialogCenterX, p)
                    val cy = lerp(btnCenterY, dialogCenterY, p)
                    val x = (cx - w / 2f).roundToInt()
                    val y = (cy - h / 2f).roundToInt()

                    val placeable = measurable.measure(
                        Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                    )
                    layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                        placeable.place(x, y)
                    }
                }
                .drawBackdrop(
                    backdrop = readerBackdrop,
                    shape = {
                        val p = settingsAnim.value.coerceIn(0f, 1f)
                        val r = lerp(btnBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                        RoundedCornerShape(with(density) { r.toDp() })
                    },
                    effects = {
                        val p = settingsAnim.value.coerceIn(0f, 1f)
                        vibrancy()
                        blur(lerp(3f, 8f, p).dp.toPx())
                        lens(
                            refractionHeight = lerp(14f, 24f, p).dp.toPx(),
                            refractionAmount = lerp(28f, 48f, p).dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Plain },
                    shadow = {
                        val p = settingsAnim.value.coerceIn(0f, 1f)
                        Shadow(
                            radius = lerp(4f, 24f, p).dp,
                            color = Color.Black.copy(alpha = lerp(0.04f, 0.22f, p))
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = if (isGlassDark) 0.10f else 0.16f))
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
            // 1. Settings Icon centered in the expanding bubble: visible when smaller
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = settingsAnim.value
                        alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = glassTextColor, modifier = Modifier.size(24.dp))
            }

            // 2. Settings Content: fixed size container with zero layout churn
            Box(
                modifier = Modifier
                    .requiredSize(dialogWidthDp, dialogHeightDp)
                    .graphicsLayer {
                        val p = settingsAnim.value
                        alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
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
                                .background(if (isGlassDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
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
                        color = glassTextColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    val buttonTextStyle = MaterialTheme.typography.labelLarge.copy(
                        color = glassTextColor,
                        fontSize = 14.sp
                    )

                    val themeSelectedBg = globalThemeAccent.copy(alpha = if (isGlassDark) 0.32f else 0.22f)
                    val themeUnselectedBg = if (isGlassDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.38f)

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

                                val activeSurface = globalThemeAccent.copy(alpha = if (isGlassDark) 0.32f else 0.22f)
                                val inactiveSurface = if (isGlassDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.38f)

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

        // 3. Liquid Shape Morphing Container for TOC / 目录 (Pre-inflated, Zero-Frame-Drop GPU Fluid Transform)
        // Scrim overlay with gentle dimming
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { if (isTocActive) IntOffset.Zero else IntOffset(100000, 0) }
                .graphicsLayer { alpha = tocAnim.value * 0.45f }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showTocSheet = false }
                )
        )

        val fallbackTocBtnBounds = Rect(
            with(density) { 24.dp.toPx() },
            screenHeightPx - with(density) { 80.dp.toPx() },
            with(density) { 108.dp.toPx() },
            screenHeightPx - with(density) { 36.dp.toPx() }
        )
        val tocBounds = if (tocButtonBounds != Rect.Zero && tocButtonBounds.width > 0f) tocButtonBounds else fallbackTocBtnBounds

        val tocBtnCenterX = tocBounds.left + tocBounds.width / 2f
        val tocBtnCenterY = tocBounds.top + tocBounds.height / 2f

        val tocDeltaX = tocBtnCenterX - dialogCenterX
        val tocDeltaY = tocBtnCenterY - dialogCenterY

        val tocInitialScaleX = (tocBounds.width / dialogWidthPx).coerceIn(0.02f, 1f)
        val tocInitialScaleY = (tocBounds.height / dialogHeightPx).coerceIn(0.02f, 1f)

        val currentChapterIndex = if (pageTurnMode == 0) {
            flatItems.getOrNull(listState.firstVisibleItemIndex)?.chapterIndex ?: 0
        } else {
            pages.getOrNull(pagedCurrentIndex)?.chapterIndex ?: 0
        }

        Box(
            modifier = Modifier
                .offset { if (isTocActive) IntOffset.Zero else IntOffset(100000, 0) }
                .layout { measurable, _ ->
                    val p = tocAnim.value
                    val w = lerp(tocBounds.width, dialogWidthPx, p).roundToInt()
                    val h = lerp(tocBounds.height, dialogHeightPx, p).roundToInt()
                    val cx = lerp(tocBtnCenterX, dialogCenterX, p)
                    val cy = lerp(tocBtnCenterY, dialogCenterY, p)
                    val x = (cx - w / 2f).roundToInt()
                    val y = (cy - h / 2f).roundToInt()

                    val placeable = measurable.measure(
                        Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                    )
                    layout(screenWidthPx.roundToInt(), screenHeightPx.roundToInt()) {
                        placeable.place(x, y)
                    }
                }
                .drawBackdrop(
                    backdrop = readerBackdrop,
                    shape = {
                        val p = tocAnim.value.coerceIn(0f, 1f)
                        val r = lerp(tocBounds.height / 2f, with(density) { 26.dp.toPx() }, p)
                        RoundedCornerShape(with(density) { r.toDp() })
                    },
                    effects = {
                        val p = tocAnim.value.coerceIn(0f, 1f)
                        vibrancy()
                        blur(lerp(3f, 8f, p).dp.toPx())
                        lens(
                            refractionHeight = lerp(14f, 24f, p).dp.toPx(),
                            refractionAmount = lerp(28f, 48f, p).dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Plain },
                    shadow = {
                        val p = tocAnim.value.coerceIn(0f, 1f)
                        Shadow(
                            radius = lerp(4f, 24f, p).dp,
                            color = Color.Black.copy(alpha = lerp(0.04f, 0.22f, p))
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = if (isGlassDark) 0.10f else 0.16f))
                    },
                    exportedBackdrop = bottomSheetBackdrop
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Intercept clicks so tapping inside does not dismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // 1. TOC Icon / Text: visible when smaller
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = tocAnim.value
                        alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "目录",
                        tint = glassTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "目录",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = glassTextColor
                    )
                }
            }

            // 2. TOC Contents: fixed size container with zero layout churn
            Box(
                modifier = Modifier
                    .requiredSize(dialogWidthDp, dialogHeightDp)
                    .graphicsLayer {
                        val p = tocAnim.value
                        alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
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
                                .background(if (isGlassDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                .clickable { showTocSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = glassTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                                // Current reading chapter for unique TOC selection
                                val currentChapter = if (pageTurnMode == 0) {
                                    parsedChapters.getOrNull(listState.firstVisibleItemIndex)
                                } else {
                                    pages.getOrNull(pagedCurrentIndex)?.let { p -> parsedChapters.getOrNull(p.chapterIndex) }
                                }
                                val currentChIdx = if (pageTurnMode == 0) listState.firstVisibleItemIndex else (pages.getOrNull(pagedCurrentIndex)?.chapterIndex ?: 0)

                                val currentTocIndex = remember(tocList, currentChIdx, currentChapter, pagedCurrentIndex, pageTurnMode) {
                                    if (tocList.isEmpty()) -1
                                    else {
                                        val currentTitle = (if (pageTurnMode == 0) currentChapter?.title else pages.getOrNull(pagedCurrentIndex)?.chapterTitle)?.trim() ?: ""
                                        var found = if (currentTitle.isNotBlank()) {
                                            tocList.indexOfFirst { it.title.trim().equals(currentTitle, ignoreCase = true) }
                                        } else -1

                                        if (found < 0 && currentTitle.isNotBlank()) {
                                            found = tocList.indexOfFirst {
                                                val t = it.title.trim()
                                                t.isNotEmpty() && (t.contains(currentTitle) || currentTitle.contains(t))
                                            }
                                        }

                                        val epubCh = viewModel.epubBook.value?.chapters?.getOrNull(currentChIdx)
                                        if (found < 0 && epubCh != null) {
                                            val withoutAnchor = epubCh.href.substringBefore("#")
                                            val fileName = withoutAnchor.substringAfterLast("/")
                                            found = tocList.indexOfFirst {
                                                it.href.contains(withoutAnchor) || withoutAnchor.contains(it.href) || it.href.substringAfterLast("/") == fileName
                                            }
                                        }

                                        if (found < 0) {
                                            found = currentChIdx.coerceIn(0, tocList.lastIndex)
                                        }
                                        found
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
                                            val isCurrent = (idx == currentTocIndex)

                                            val onItemClick = {
                                                val withoutAnchor = tocItem.href.substringBefore("#")
                                                val fileNameOnly = withoutAnchor.substringAfterLast("/")
                                                var matchedCh = parsedChapters.indexOfFirst {
                                                    it.title.trim().equals(tocItem.title.trim(), ignoreCase = true) || it.title.contains(tocItem.title) || tocItem.title.contains(it.title)
                                                }
                                                if (matchedCh < 0) {
                                                    val chIdx = viewModel.epubBook.value?.chapters?.indexOfFirst {
                                                        it.href.contains(withoutAnchor) || withoutAnchor.contains(it.href) || it.href.substringAfterLast("/") == fileNameOnly
                                                    } ?: -1
                                                    if (chIdx >= 0) {
                                                        matchedCh = chIdx
                                                    }
                                                }
                                                if (matchedCh >= 0) {
                                                    if (pageTurnMode == 0) {
                                                        coroutineScope.launch { listState.scrollToItem(matchedCh, 0) }
                                                        viewModel.saveProgress(matchedCh, 0)
                                                    } else {
                                                        val targetPage = pages.indexOfFirst { it.chapterIndex == matchedCh }.coerceAtLeast(0)
                                                        pagedCurrentIndex = targetPage
                                                        val cur = pages.getOrNull(targetPage)
                                                        if (cur != null) viewModel.saveProgress(cur.chapterIndex, 0)
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
                                                    surfaceColor = globalThemeAccent.copy(alpha = if (isGlassDark) 0.32f else 0.22f),
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
                                            val isCurrent = idx == currentChIdx
                                            val onChapterClick = {
                                                if (pageTurnMode == 0) {
                                                    coroutineScope.launch { listState.scrollToItem(idx, 0) }
                                                    viewModel.saveProgress(idx, 0)
                                                } else {
                                                    val targetPage = pages.indexOfFirst { it.chapterIndex == idx }.coerceAtLeast(0)
                                                    pagedCurrentIndex = targetPage
                                                    val cur = pages.getOrNull(targetPage)
                                                    if (cur != null) viewModel.saveProgress(cur.chapterIndex, 0)
                                                }
                                                showTocSheet = false
                                                showToolbars = false
                                            }

                                            if (isCurrent) {
                                                LiquidButton(
                                                    onClick = onChapterClick,
                                                    backdrop = readerBackdrop,
                                                    shape = RoundedCornerShape(12.dp),
                                                    surfaceColor = globalThemeAccent.copy(alpha = if (isGlassDark) 0.32f else 0.22f),
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

data class BatteryAndTimeState(
    val level: Int,
    val isCharging: Boolean,
    val time: String
)

@Composable
fun rememberBatteryAndTime(): BatteryAndTimeState {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var time by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (rawLevel >= 0 && rawScale > 0) {
                            level = (rawLevel * 100 / rawScale.toFloat()).toInt()
                        }
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == BatteryManager.BATTERY_STATUS_FULL
                    }
                    Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    return BatteryAndTimeState(level, isCharging, time)
}

@Composable
fun MiniBatteryIndicator(
    level: Int,
    isCharging: Boolean,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        if (isCharging) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Charging",
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(13.dp)
            )
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 19.dp, height = 10.dp)) {
            val strokeWidth = 1.15.dp.toPx()
            val cornerRadius = 2.dp.toPx()
            val capWidth = 1.5.dp.toPx()
            val capHeight = 4.dp.toPx()
            val mainBodyWidth = size.width - capWidth - 1.2.dp.toPx()
            val mainBodyHeight = size.height

            // Outer rounded rectangle
            drawRoundRect(
                color = tintColor.copy(alpha = 0.55f),
                size = androidx.compose.ui.geometry.Size(mainBodyWidth, mainBodyHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            // Right terminal cap
            drawRoundRect(
                color = tintColor.copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(mainBodyWidth + 0.8.dp.toPx(), (mainBodyHeight - capHeight) / 2),
                size = androidx.compose.ui.geometry.Size(capWidth, capHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            // Inner fill
            val fillPadding = 1.8.dp.toPx()
            val maxFillWidth = mainBodyWidth - (fillPadding * 2)
            val fillHeight = mainBodyHeight - (fillPadding * 2)
            val fillPercent = (level.coerceIn(0, 100) / 100f)
            val fillWidth = (maxFillWidth * fillPercent).coerceAtLeast(1.dp.toPx())

            val fillColor = when {
                isCharging -> Color(0xFFFFB800)
                level <= 15 -> Color(0xFFFF4D4F)
                else -> tintColor.copy(alpha = 0.85f)
            }

            drawRoundRect(
                color = fillColor,
                topLeft = androidx.compose.ui.geometry.Offset(fillPadding, fillPadding),
                size = androidx.compose.ui.geometry.Size(fillWidth, fillHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
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
                    imageVector = Icons.Filled.Settings,
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

@Composable
private fun ScrollableTitleItem(
    title: String,
    chineseMode: Int,
    customFontFamily: androidx.compose.ui.text.font.FontFamily?,
    textSize: Float,
    textColor: Color
) {
    val displayTitle = remember(title, chineseMode) {
        if (chineseMode == 0) title
        else if (chineseMode == 1) ZhConverterUtil.toSimple(title)
        else ZhConverterUtil.toTraditional(title)
    }
    Text(
        text = displayTitle,
        fontSize = (textSize * 1.3f).sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        fontFamily = customFontFamily,
        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
    )
}

@Composable
private fun ScrollableTextItem(
    node: ChapterNode.TextNode,
    chineseMode: Int,
    customFontFamily: androidx.compose.ui.text.font.FontFamily?,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    textColor: Color
) {
    val rawText = remember(node.text, chineseMode) {
        val raw = if (chineseMode == 0) {
            node.text.text
        } else if (chineseMode == 1) {
            ZhConverterUtil.toSimple(node.text.text)
        } else {
            ZhConverterUtil.toTraditional(node.text.text)
        }
        if (!raw.startsWith("　") && !raw.startsWith("  ")) {
            "　　" + raw
        } else {
            raw
        }
    }

    if (node.text.spanStyles.isEmpty() && node.text.paragraphStyles.isEmpty()) {
        Text(
            text = rawText,
            fontFamily = customFontFamily,
            fontSize = textSize.sp,
            lineHeight = (textSize * lineHeightMult).coerceAtLeast(textSize * 1.1f).sp,
            color = textColor,
            modifier = Modifier.padding(bottom = paragraphSpacing.dp)
        )
    } else {
        val annotated = remember(rawText) {
            val indentDiff = rawText.length - node.text.text.length
            androidx.compose.ui.text.buildAnnotatedString {
                append(rawText)
                node.text.spanStyles.forEach {
                    addStyle(it.item, it.start + indentDiff, it.end + indentDiff)
                }
                node.text.paragraphStyles.forEach {
                    addStyle(it.item, it.start + indentDiff, it.end + indentDiff)
                }
            }
        }
        Text(
            text = annotated,
            fontFamily = customFontFamily,
            fontSize = textSize.sp,
            lineHeight = (textSize * lineHeightMult).coerceAtLeast(textSize * 1.1f).sp,
            color = textColor,
            modifier = Modifier.padding(bottom = paragraphSpacing.dp)
        )
    }
}

private sealed class ChapterSegment {
    data class Text(val text: androidx.compose.ui.text.AnnotatedString) : ChapterSegment()
    data class Image(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ChapterSegment()
}

@Composable
private fun ScrollableChapterItem(
    chapterIndex: Int,
    chapter: ParsedChapter,
    chineseMode: Int,
    customFontFamily: androidx.compose.ui.text.font.FontFamily?,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    textColor: Color
) {
    val segments = remember(chapter, chineseMode) {
        val list = mutableListOf<ChapterSegment>()
        var currentTextBuilder = androidx.compose.ui.text.AnnotatedString.Builder()
        var hasTextInCurrent = false

        for (node in chapter.nodes) {
            when (node) {
                is ChapterNode.TextNode -> {
                    val raw = if (chineseMode == 0) {
                        node.text.text
                    } else if (chineseMode == 1) {
                        ZhConverterUtil.toSimple(node.text.text)
                    } else {
                        ZhConverterUtil.toTraditional(node.text.text)
                    }
                    val withIndent = if (!raw.startsWith("　") && !raw.startsWith("  ")) {
                        "　　" + raw
                    } else {
                        raw
                    }

                    if (hasTextInCurrent) {
                        currentTextBuilder.append("\n\n")
                    }

                    val startOffset = currentTextBuilder.length
                    currentTextBuilder.append(withIndent)
                    val indentDiff = withIndent.length - raw.length

                    node.text.spanStyles.forEach {
                        currentTextBuilder.addStyle(it.item, startOffset + it.start + indentDiff, startOffset + it.end + indentDiff)
                    }
                    node.text.paragraphStyles.forEach {
                        currentTextBuilder.addStyle(it.item, startOffset + it.start + indentDiff, startOffset + it.end + indentDiff)
                    }
                    hasTextInCurrent = true
                }
                is ChapterNode.ImageNode -> {
                    if (hasTextInCurrent) {
                        list.add(ChapterSegment.Text(currentTextBuilder.toAnnotatedString()))
                        currentTextBuilder = androidx.compose.ui.text.AnnotatedString.Builder()
                        hasTextInCurrent = false
                    }
                    val bmp = node.bitmap
                    if (bmp != null) {
                        list.add(ChapterSegment.Image(bmp))
                    }
                }
            }
        }
        if (hasTextInCurrent) {
            list.add(ChapterSegment.Text(currentTextBuilder.toAnnotatedString()))
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 36.dp)
    ) {
        if (chapter.title.isNotBlank() && chapter.title != "Chapter") {
            ScrollableTitleItem(
                title = chapter.title,
                chineseMode = chineseMode,
                customFontFamily = customFontFamily,
                textSize = textSize,
                textColor = textColor
            )
        }
        for (segment in segments) {
            when (segment) {
                is ChapterSegment.Text -> {
                    Text(
                        text = segment.text,
                        fontFamily = customFontFamily,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * lineHeightMult).coerceAtLeast(textSize * 1.15f).sp,
                        color = textColor,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                }
                is ChapterSegment.Image -> {
                    androidx.compose.foundation.Image(
                        bitmap = segment.bitmap,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
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

