package com.example.epubreader.ui.reader

import kotlinx.coroutines.launch

import androidx.compose.ui.util.fastRoundToInt

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
    val flatItems by viewModel.flatItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val themeIndex by viewModel.themeIndex.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val lineHeightMult by viewModel.lineHeightMult.collectAsState()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsState()
    val chineseMode by viewModel.chineseMode.collectAsState()
    val customFontUri by viewModel.customFontUri.collectAsState()

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

    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    val prefs = context.getSharedPreferences("liquid_settings", android.content.Context.MODE_PRIVATE)
    val immersiveStatusBar = prefs.getBoolean("immersiveStatusBar", false)

    if (immersiveStatusBar && window != null) {
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
        var isFirstLaunch by remember { mutableStateOf(true) }
        LaunchedEffect(showToolbars) {
            if (showToolbars) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                if (isFirstLaunch) {
                    kotlinx.coroutines.delay(400)
                    isFirstLaunch = false
                }
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
            
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialFlatIndex,
                initialFirstVisibleItemScrollOffset = initialOffset
            )
            
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, listState) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        viewModel.saveProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    viewModel.saveProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .layerBackdrop(readerBackdrop)
            ) {
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
        
        // 1. Top Toolbar (Back & Settings Buttons)
        AnimatedVisibility(
            visible = showToolbars,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidButton(
                        onClick = {
                            if (onBackClick != null) {
                                onBackClick()
                            } else {
                                navController.popBackStack()
                            }
                        },
                        backdrop = readerBackdrop
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    LiquidButton(
                        onClick = { showSettings = !showSettings },
                        backdrop = readerBackdrop,
                        modifier = Modifier
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
                                
                                Spacer(modifier = Modifier.height(20.dp))
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
