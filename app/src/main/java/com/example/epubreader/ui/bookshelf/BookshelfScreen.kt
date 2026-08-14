package com.example.epubreader.ui.bookshelf

import com.kyant.backdrop.backdrops.rememberCombinedBackdrop

import androidx.compose.ui.util.fastRoundToInt

import androidx.compose.foundation.gestures.detectDragGestures

import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Check

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.settings.SettingsViewModel
import com.example.epubreader.ui.theme.AppTheme
import com.example.epubreader.ui.theme.getThemeGradient
import com.example.epubreader.ui.theme.getThemeAccentColor
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    globalBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop
) {
    val context = LocalContext.current
    val dao = AppDatabase.getDatabase(context).bookDao()
    val viewModel: BookshelfViewModel = viewModel(factory = BookshelfViewModelFactory(dao, context.applicationContext as android.app.Application))

    val books by viewModel.books.collectAsState()
    
    // Use globalBackdrop passed from MainScaffold for glass effects
    
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    var showContextMenuForBook by remember { mutableStateOf<BookEntity?>(null) }
    var showEditDialogForBook by remember { mutableStateOf<BookEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortButtonBounds by remember { mutableStateOf(Rect.Zero) }

    val morphProgress by animateFloatAsState(
        targetValue = if (showSortMenu) 1f else 0f,
        animationSpec = if (showSortMenu) {
            spring(dampingRatio = 0.72f, stiffness = 240f)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 195f)
        },
        label = "sortMorphProgress"
    )

    // Button Jelly Pulse upon collapse
    val sortButtonPulseScale = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(showSortMenu) {
        if (!showSortMenu && morphProgress > 0f) {
            kotlinx.coroutines.delay(260)
            sortButtonPulseScale.snapTo(1.18f)
            sortButtonPulseScale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = 320f)
            )
        }
    }

    val coroutineScope = rememberCoroutineScope()
    
    var pressedBookId by remember { mutableStateOf<Long?>(null) }
    val bookCoords = remember { mutableStateMapOf<Long, LayoutCoordinates>() }
    
    val sortMethod by viewModel.sortMethod.collectAsState()
    
    var draggedItem by remember { mutableStateOf<BookEntity?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var localBooks by remember(books) { mutableStateOf(books.toMutableList()) }

    val groupedItems = remember(localBooks, sortMethod) {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val groups = mutableMapOf<String, MutableList<BookEntity>>()
        val standalones = mutableListOf<BookEntity>()
        
        localBooks.forEach { book ->
            if (book.seriesName.isNullOrEmpty()) {
                standalones.add(book)
            } else {
                groups.getOrPut(book.seriesName) { mutableListOf() }.add(book)
            }
        }
        
        val items = mutableListOf<Any>()
        items.addAll(standalones)
        groups.forEach { (series, list) ->
            if (list.size == 1) {
                items.add(list.first())
            } else {
                list.sortWith { a, b -> collator.compare(a.title, b.title) }
                items.add(Pair(series, list))
            }
        }

        // Sort both standalone books and series together based on selected sortMethod!
        items.sortWith { itemA, itemB ->
            when (sortMethod) {
                0 -> { // 最近阅读 (Last Read DESC)
                    val timeA = when (itemA) {
                        is BookEntity -> if (itemA.lastReadTime > 0) itemA.lastReadTime else itemA.addedTime
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemA.second as List<BookEntity>)).maxOfOrNull { if (it.lastReadTime > 0) it.lastReadTime else it.addedTime } ?: 0L
                        else -> 0L
                    }
                    val timeB = when (itemB) {
                        is BookEntity -> if (itemB.lastReadTime > 0) itemB.lastReadTime else itemB.addedTime
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemB.second as List<BookEntity>)).maxOfOrNull { if (it.lastReadTime > 0) it.lastReadTime else it.addedTime } ?: 0L
                        else -> 0L
                    }
                    timeB.compareTo(timeA)
                }
                1 -> { // 导入时间 (Import Time DESC)
                    val timeA = when (itemA) {
                        is BookEntity -> itemA.addedTime
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemA.second as List<BookEntity>)).maxOfOrNull { it.addedTime } ?: 0L
                        else -> 0L
                    }
                    val timeB = when (itemB) {
                        is BookEntity -> itemB.addedTime
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemB.second as List<BookEntity>)).maxOfOrNull { it.addedTime } ?: 0L
                        else -> 0L
                    }
                    timeB.compareTo(timeA)
                }
                2 -> { // 书籍名称 (Chinese Pinyin ASC: A-Z)
                    val titleA = when (itemA) {
                        is BookEntity -> itemA.title
                        is Pair<*, *> -> itemA.first as String
                        else -> ""
                    }
                    val titleB = when (itemB) {
                        is BookEntity -> itemB.title
                        is Pair<*, *> -> itemB.first as String
                        else -> ""
                    }
                    collator.compare(titleA, titleB)
                }
                3 -> { // 阅读进度 (Progress DESC)
                    val progA = when (itemA) {
                        is BookEntity -> itemA.totalProgress
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemA.second as List<BookEntity>)).map { it.totalProgress }.average().toFloat()
                        else -> 0f
                    }
                    val progB = when (itemB) {
                        is BookEntity -> itemB.totalProgress
                        is Pair<*, *> -> (@Suppress("UNCHECKED_CAST") (itemB.second as List<BookEntity>)).map { it.totalProgress }.average().toFloat()
                        else -> 0f
                    }
                    progB.compareTo(progA)
                }
                else -> 0
            }
        }
        items
    }

    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importLocalBook(it, context)
        }
    }
    
    var selectedSeries by remember { mutableStateOf<Pair<String, List<BookEntity>>?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }
    var seriesLongPressTarget by remember { mutableStateOf<Pair<String, List<BookEntity>>?>(null) }

    // Book Open Morphing State
    var openingBook by remember { mutableStateOf<BookEntity?>(null) }
    var openingBookBounds by remember { mutableStateOf(Rect.Zero) }
    var activeOpeningBook by remember { mutableStateOf<BookEntity?>(null) }
    if (openingBook != null) {
        activeOpeningBook = openingBook
    }

    val isOpeningBook = openingBook != null
    val bookOpenTransition = updateTransition(targetState = isOpeningBook, label = "BookOpenTransition")
    val bookOpenProgress by bookOpenTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.74f, stiffness = 260f)
            } else {
                spring(dampingRatio = 0.82f, stiffness = 340f)
            }
        },
        label = "bookOpenProgress"
    ) { if (it) 1f else 0f }

    LaunchedEffect(bookOpenProgress) {
        if (bookOpenProgress >= 0.96f && isOpeningBook && activeOpeningBook != null && !isNavigating) {
            isNavigating = true
            val targetId = activeOpeningBook!!.id
            navController.navigate("reader/$targetId")
        }
    }

    // Reset debounce & opening state when returning to the screen
    LaunchedEffect(Unit) { 
        isNavigating = false 
        openingBook = null
    }

    val handleBookClick: (BookEntity) -> Unit = { book ->
        if (!isNavigating && openingBook == null) {
            val coords = bookCoords[book.id]
            val bounds = if (coords != null && rootCoords != null) {
                try {
                    rootCoords!!.localBoundingBoxOf(coords, clipBounds = false)
                } catch (e: Exception) { Rect.Zero }
            } else Rect.Zero

            if (bounds != Rect.Zero) {
                openingBookBounds = bounds
                openingBook = book
            } else {
                isNavigating = true
                navController.navigate("reader/${book.id}")
            }
        }
    }

    val appTheme by settingsViewModel.appTheme.collectAsState()
    val isCustomThemeThreeColors by settingsViewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by settingsViewModel.customColors.collectAsState()

    val isDark = appTheme == AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF543866).copy(alpha = 0.8f)

    val bookshelfGradient = getThemeGradient(
        theme = appTheme,
        customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
    )

    val seriesBackdrop = rememberLayerBackdrop()
    val seriesDialogBackdrop = rememberLayerBackdrop()
    val bookshelfBackdrop = rememberLayerBackdrop {
        drawRect(brush = bookshelfGradient)
        drawContent()
    }
    val combinedBackdrop = rememberCombinedBackdrop(globalBackdrop, bookshelfBackdrop)
    val sortDialogBackdrop = rememberLayerBackdrop()
    val activeBackdrop = globalBackdrop
    
    // State to keep content alive during the exit animation
    var displaySeries by remember { mutableStateOf<Pair<String, List<BookEntity>>?>(null) }
    if (selectedSeries != null) {
        displaySeries = selectedSeries
    }

    val isSeriesExpanded = selectedSeries != null && openingBook == null
    val seriesTransition = updateTransition(targetState = isSeriesExpanded, label = "SeriesMorphTransition")
    val seriesExpandProgress by seriesTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.72f, stiffness = 280f)
            } else {
                spring(dampingRatio = 0.82f, stiffness = 340f)
            }
        },
        label = "seriesMorphProgress"
    ) { if (it) 1f else 0f }

    val backgroundBlurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (seriesExpandProgress > 0.01f || bookOpenProgress > 0.01f || showContextMenuForBook != null) 16.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "BackgroundBlur"
    )
    val layoutMethod by viewModel.layoutMethod.collectAsState()

    Box(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { rootCoords = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bookshelfGradient)
                .blur(backgroundBlurRadius)
                .layerBackdrop(bookshelfBackdrop)
        ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = primaryTextColor,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "我的书架", 
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, 
                            fontSize = 24.sp,
                            color = primaryTextColor
                        ) 
                    },
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // Liquid Layout Switcher Button
                            LiquidButton(
                                onClick = { viewModel.setLayoutMethod(if (layoutMethod == 0) 1 else 0) },
                                backdrop = globalBackdrop,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (layoutMethod == 0) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = "切换布局",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Liquid Sort Button
                            LiquidButton(
                                onClick = { showSortMenu = true },
                                backdrop = globalBackdrop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .onGloballyPositioned { coords ->
                                        rootCoords?.let { root ->
                                            sortButtonBounds = root.localBoundingBoxOf(coords, clipBounds = false)
                                        }
                                    }
                                    .graphicsLayer {
                                        alpha = if (morphProgress > 0.001f) 0f else 1f
                                        scaleX = sortButtonPulseScale.value
                                        scaleY = sortButtonPulseScale.value
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sort, 
                                    contentDescription = "排序",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = primaryTextColor,
                        actionIconContentColor = primaryTextColor
                    )
                )
            }
        ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            LazyVerticalGrid(
                columns = if (layoutMethod == 0) GridCells.Fixed(3) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Leave space for bottom bar
            ) {
            items(groupedItems) { item ->
                when (item) {
                    is BookEntity -> {
                        val pointerModifier = if (sortMethod == 2) {
                            Modifier.pointerInput(item.id) {
                                var hasDragged = false
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { 
                                        draggedItem = item
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        hasDragged = false
                                    },
                                    onDrag = { _, dragAmount ->
                                        dragOffset += dragAmount
                                        hasDragged = true
                                    },
                                    onDragEnd = {
                                        if (hasDragged && (dragOffset.x * dragOffset.x + dragOffset.y * dragOffset.y) > 400f) {
                                            viewModel.updateSortOrder(localBooks)
                                        } else {
                                            showContextMenuForBook = item
                                        }
                                        draggedItem = null
                                    },
                                    onDragCancel = { 
                                        if (!hasDragged) showContextMenuForBook = item
                                        draggedItem = null 
                                    }
                                )
                            }.pointerInput(item.id, "tap") {
                                detectTapGestures(
                                    onPress = {
                                        pressedBookId = item.id
                                        tryAwaitRelease()
                                        pressedBookId = null
                                    },
                                    onTap = {
                                        handleBookClick(item)
                                    }
                                )
                            }
                        } else {
                            Modifier.pointerInput(item.id) {
                                detectTapGestures(
                                    onPress = {
                                        pressedBookId = item.id
                                        tryAwaitRelease()
                                        pressedBookId = null
                                    },
                                    onTap = {
                                        handleBookClick(item)
                                    },
                                    onLongPress = {
                                        showContextMenuForBook = item
                                    }
                                )
                            }
                        }
                        BookItem(
                            book = item, 
                            isListLayout = layoutMethod == 1, 
                            isPressed = (pressedBookId == item.id), 
                            backdrop = globalBackdrop, 
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            modifier = pointerModifier.onGloballyPositioned { coords -> 
                                bookCoords[item.id] = coords 
                            }
                        )
                    }
                    is Pair<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val series = item as Pair<String, List<BookEntity>>
                        SeriesItem(
                            seriesName = series.first,
                            books = series.second,
                            rootCoords = rootCoords,
                            backdrop = globalBackdrop,
                            isHidden = (series.first == selectedSeries?.first && seriesExpandProgress > 0.35f),
                            isListLayout = layoutMethod == 1,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onClick = { bounds ->
                                if (!isNavigating && openingBook == null) {
                                    sourceBounds = bounds
                                    selectedSeries = series
                                }
                            },
                            onLongClick = {
                                seriesLongPressTarget = series
                            }
                        )
                    }
                }
            }
            
            if (groupedItems.isEmpty()) {
                item {
                    Text("书架空空如也，点击右上角导入书籍，或者前往配置页同步 WebDAV", modifier = Modifier.padding(16.dp))
                }
            }
        }
        } // Close LazyVerticalGrid Box
        
        } // Close Scaffold content block
    } // Close Inner Box

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        // ==========================================
        // 1. Fluid Series Morphing Container (Glass Expand/Collapse)
        // ==========================================
        if (seriesExpandProgress > 0.001f && displaySeries != null) {
            val (seriesTitle, seriesBooks) = displaySeries!!
            val expandedWidthPx = minOf(screenWidthPx * 0.92f, with(density) { 480.dp.toPx() })
            val expandedHeightPx = minOf(screenHeightPx * 0.78f, with(density) { 620.dp.toPx() })
            val targetLeft = (screenWidthPx - expandedWidthPx) / 2f
            val targetTop = (screenHeightPx - expandedHeightPx) / 2f

            val currentLeft = lerp(sourceBounds.left, targetLeft, seriesExpandProgress)
            val currentTop = lerp(sourceBounds.top, targetTop, seriesExpandProgress)
            val currentWidth = lerp(sourceBounds.width, expandedWidthPx, seriesExpandProgress).coerceAtLeast(1f)
            val currentHeight = lerp(sourceBounds.height, expandedHeightPx, seriesExpandProgress).coerceAtLeast(1f)
            val currentRadius = lerp(20f, 28f, seriesExpandProgress)

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * seriesExpandProgress))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isNavigating && openingBook == null) {
                            selectedSeries = null
                        }
                    }
            )

            // Morphing Glass Container
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
                        backdrop = bookshelfBackdrop,
                        shape = { RoundedCornerShape(with(density) { currentRadius.dp }) },
                        effects = {
                            vibrancy()
                            blur(lerp(3f, 8f, seriesExpandProgress).dp.toPx())
                            lens(
                                refractionHeight = lerp(16f, 24f, seriesExpandProgress).dp.toPx(),
                                refractionAmount = lerp(32f, 48f, seriesExpandProgress).dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                        },
                        exportedBackdrop = seriesDialogBackdrop
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside dialog */ }
            ) {
                // Collapsed Stage Thumbnail (fades out)
                if (seriesExpandProgress < 0.35f) {
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { sourceBounds.width.toDp() },
                                height = with(density) { sourceBounds.height.toDp() }
                            )
                            .graphicsLayer {
                                alpha = (1f - seriesExpandProgress * 3f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SeriesItemContent(
                            seriesName = seriesTitle,
                            books = seriesBooks,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

                // Expanded Stage Content (fades in)
                if (seriesExpandProgress > 0.15f) {
                    val contentAlpha = ((seriesExpandProgress - 0.20f) / 0.80f).coerceIn(0f, 1f)
                    val contentScale = lerp(0.92f, 1f, contentAlpha)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = contentAlpha
                                scaleX = contentScale
                                scaleY = contentScale
                            }
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = seriesTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .drawBackdrop(
                                        backdrop = seriesDialogBackdrop,
                                        shape = { androidx.compose.foundation.shape.CircleShape },
                                        effects = {
                                            vibrancy()
                                            lens(
                                                refractionHeight = 8f.dp.toPx(),
                                                refractionAmount = 14f.dp.toPx(),
                                                chromaticAberration = true
                                            )
                                        },
                                        highlight = { Highlight.Plain },
                                        onDrawSurface = { drawRect(Color.White.copy(alpha = if (isDark) 0.10f else 0.18f)) }
                                    )
                                    .clickable { selectedSeries = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = primaryTextColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${seriesBooks.size} 册 · 点按阅读 · 长按管理",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryTextColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        
                        val seriesBooksState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(
                            state = seriesBooksState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            itemsIndexed(seriesBooks) { index, book ->
                                val volumeNumber = index + 1
                                SeriesInnerBookRow(
                                    book = book,
                                    seriesTitle = seriesTitle,
                                    volumeNumber = volumeNumber,
                                    isPressed = (pressedBookId == book.id),
                                    isDark = isDark,
                                    backdrop = seriesDialogBackdrop,
                                    listState = seriesBooksState,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords -> 
                                            bookCoords[book.id] = coords 
                                        }
                                        .pointerInput(book.id) {
                                            detectTapGestures(
                                                onPress = {
                                                    pressedBookId = book.id
                                                    tryAwaitRelease()
                                                    pressedBookId = null
                                                },
                                                onTap = {
                                                    handleBookClick(book)
                                                },
                                                onLongPress = {
                                                    showContextMenuForBook = book
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. Fluid Book Open Morphing Overlay (Card -> Full Screen Reader)
        // ==========================================
        if (bookOpenProgress > 0.001f && activeOpeningBook != null) {
            val book = activeOpeningBook!!
            val targetLeft = 0f
            val targetTop = 0f
            val targetWidth = screenWidthPx
            val targetHeight = screenHeightPx

            val currentLeft = lerp(openingBookBounds.left, targetLeft, bookOpenProgress)
            val currentTop = lerp(openingBookBounds.top, targetTop, bookOpenProgress)
            val currentWidth = lerp(openingBookBounds.width, targetWidth, bookOpenProgress).coerceAtLeast(1f)
            val currentHeight = lerp(openingBookBounds.height, targetHeight, bookOpenProgress).coerceAtLeast(1f)
            val currentRadius = lerp(20f, 0f, bookOpenProgress)

            // Dynamic background scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * bookOpenProgress))
            )

            // Fullscreen Morphing Glass Sheet
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
                        backdrop = bookshelfBackdrop,
                        shape = { RoundedCornerShape(with(density) { currentRadius.dp }) },
                        effects = {
                            vibrancy()
                            blur(lerp(3f, 16f, bookOpenProgress).dp.toPx())
                            lens(
                                refractionHeight = lerp(16f, 32f, bookOpenProgress).dp.toPx(),
                                refractionAmount = lerp(32f, 56f, bookOpenProgress).dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = {
                            val baseColor = if (isDark) Color(0xFF0F172A) else Color.White
                            drawRect(baseColor.copy(alpha = lerp(0.12f, 0.95f, bookOpenProgress)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Collapsed Book Thumbnail (fading out smoothly in first 35%)
                if (bookOpenProgress < 0.40f) {
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { openingBookBounds.width.toDp() },
                                height = with(density) { openingBookBounds.height.toDp() }
                            )
                            .graphicsLayer {
                                alpha = (1f - bookOpenProgress * 2.8f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BookItem(
                            book = book,
                            isListLayout = layoutMethod == 1,
                            backdrop = globalBackdrop,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

                // Expanded Reader Atmosphere View (fading in and expanding gently)
                if (bookOpenProgress > 0.15f) {
                    val contentAlpha = ((bookOpenProgress - 0.20f) / 0.80f).coerceIn(0f, 1f)
                    val contentScale = lerp(0.88f, 1f, contentAlpha)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = contentAlpha
                                scaleX = contentScale
                                scaleY = contentScale
                            }
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (book.coverImage != null && File(book.coverImage).exists()) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(book.coverImage))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            color = primaryTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = book.author ?: "未知作者",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "正在载入阅读...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Draw the book in focus exactly over its original position
        if (showContextMenuForBook != null) {
            val book = showContextMenuForBook!!
            val coords = bookCoords[book.id]
            val bounds = if (coords != null && rootCoords != null) {
                try {
                    rootCoords!!.localBoundingBoxOf(coords, clipBounds = false)
                } catch (e: Exception) { Rect.Zero }
            } else Rect.Zero
            
            if (bounds != Rect.Zero) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                        .size(
                            width = with(density) { bounds.width.toDp() },
                            height = with(density) { bounds.height.toDp() }
                        )
                ) {
                    if (selectedSeries != null) {
                        val seriesBooks = selectedSeries!!.second
                        val index = seriesBooks.indexOfFirst { it.id == book.id }
                        SeriesInnerBookRow(
                            book = book,
                            seriesTitle = selectedSeries!!.first,
                            volumeNumber = if (index >= 0) index + 1 else 1,
                            isPressed = (pressedBookId == book.id),
                            isDark = isDark,
                            backdrop = seriesDialogBackdrop
                        )
                    } else {
                        BookItem(
                            book = book, 
                            isListLayout = layoutMethod == 1, 
                            isPressed = (pressedBookId == book.id), 
                            backdrop = globalBackdrop,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showContextMenuForBook != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { showContextMenuForBook = null } }
            ) {
                showContextMenuForBook?.let { book ->
                    val coords = bookCoords[book.id]
                    val bounds = if (coords != null && rootCoords != null) {
                        try {
                            rootCoords!!.localBoundingBoxOf(coords, clipBounds = false)
                        } catch (e: Exception) { Rect.Zero }
                    } else Rect.Zero
                    
                    val density = LocalDensity.current
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    
                    Box(
                        modifier = Modifier.offset {
                            // Menu dimensions — must match GlassContextMenu width
                            val menuWidthPx  = with(density) { 180.dp.toPx() }
                            val menuHeightPx = with(density) {  90.dp.toPx() }
                            val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
                            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                            val margin  = with(density) { 12.dp.toPx() }
                            val spacing = with(density) { 12.dp.toPx() }

                            // Horizontally: center on the book, then clamp to screen
                            val x = (bounds.left + bounds.width / 2f - menuWidthPx / 2f)
                                .coerceIn(margin, screenWidthPx - menuWidthPx - margin)

                            // Vertically: prefer BELOW the book; fall back to ABOVE
                            var y = bounds.bottom + spacing
                            if (y + menuHeightPx > screenHeightPx - margin) {
                                y = bounds.top - menuHeightPx - spacing
                            }
                            y = y.coerceIn(margin, screenHeightPx - menuHeightPx - margin)

                            IntOffset(x.toInt(), y.toInt())
                        }
                    ) {
                        GlassContextMenu(
                            backdrop = activeBackdrop,
                            onEditClick = {
                                showEditDialogForBook = book
                                showContextMenuForBook = null
                                selectedSeries = null
                            },
                            onDeleteClick = {
                                bookToDelete = book
                                showContextMenuForBook = null
                            }
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = bookToDelete != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .pointerInput(Unit) { detectTapGestures { bookToDelete = null } },
                contentAlignment = Alignment.Center
            ) {
                bookToDelete?.let { book ->
                    Box(modifier = Modifier.fillMaxWidth(0.85f).pointerInput(Unit) { detectTapGestures { /* consume clicks inside dialog */ } }) {
                        GlassDeleteDialog(
                            bookTitle = book.title,
                            backdrop = activeBackdrop,
                            onDismiss = { bookToDelete = null },
                            onConfirm = {
                                viewModel.deleteBook(book)
                                bookToDelete = null
                            }
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = seriesLongPressTarget != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .pointerInput(Unit) { detectTapGestures { seriesLongPressTarget = null } },
                contentAlignment = Alignment.Center
            ) {
                seriesLongPressTarget?.let { (seriesName, books) ->
                    Box(modifier = Modifier.fillMaxWidth(0.85f).pointerInput(Unit) { detectTapGestures { /* consume */ } }) {
                        GlassDeleteDialog(
                            bookTitle = "「$seriesName」系列共 ${books.size} 册",
                            backdrop = activeBackdrop,
                            onDismiss = { seriesLongPressTarget = null },
                            onConfirm = {
                                books.forEach { viewModel.deleteBook(it) }
                                seriesLongPressTarget = null
                                selectedSeries = null
                            }
                        )
                    }
                }
            }
        }
        
        // Liquid Morphing Sort Menu Container (Anchored at Top-Right under Sort Button)
        if (morphProgress > 0.001f || showSortMenu) {
            // Scrim overlay with gentle dimming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morphProgress * 0.15f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showSortMenu = false }
                    )
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = LocalDensity.current
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val fallbackBtnBounds = Rect(
                    screenWidthPx - with(density) { 60.dp.toPx() },
                    with(density) { 44.dp.toPx() },
                    screenWidthPx - with(density) { 16.dp.toPx() },
                    with(density) { 88.dp.toPx() }
                )
                val btnBounds = if (sortButtonBounds != Rect.Zero) sortButtonBounds else fallbackBtnBounds

                val menuWidthPx = with(density) { 156.dp.toPx() }
                val menuHeightPx = with(density) { 178.dp.toPx() }
                val dialogLeft = (btnBounds.right - menuWidthPx).coerceAtLeast(with(density) { 16.dp.toPx() })
                val dialogTop = btnBounds.bottom + with(density) { 6.dp.toPx() }
                val dialogBounds = Rect(dialogLeft, dialogTop, dialogLeft + menuWidthPx, dialogTop + menuHeightPx)

                val currentLeft = androidx.compose.ui.util.lerp(btnBounds.left, dialogBounds.left, morphProgress)
                val currentTop = androidx.compose.ui.util.lerp(btnBounds.top, dialogBounds.top, morphProgress)
                val currentWidth = androidx.compose.ui.util.lerp(btnBounds.width, menuWidthPx, morphProgress).coerceAtLeast(1f)
                val currentHeight = androidx.compose.ui.util.lerp(btnBounds.height, menuHeightPx, morphProgress).coerceAtLeast(1f)
                val currentCornerRadius = androidx.compose.ui.util.lerp(btnBounds.height / 2f, with(density) { 24.dp.toPx() }, morphProgress)

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
                            backdrop = bookshelfBackdrop,
                            shape = { RoundedCornerShape(with(density) { currentCornerRadius.toDp() }) },
                            effects = {
                                vibrancy()
                                blur(androidx.compose.ui.util.lerp(3f, 8f, morphProgress).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, morphProgress).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, morphProgress).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.10f))
                            },
                            exportedBackdrop = sortDialogBackdrop
                        )
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent taps from dismissing through to scrim
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Morphing Icon: Sort icon centered inside button, fades out as it expands
                    if (morphProgress < 0.6f) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    alpha = (1f - morphProgress * 2.5f).coerceIn(0f, 1f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort, 
                                contentDescription = "排序",
                                tint = primaryTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Morphing Content: Unified Segmented Glass Slider (Single Glass Capsule on Single Track)
                    if (morphProgress > 0.2f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((morphProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                }
                        ) {
                            val sortOptions = listOf(
                                "最近阅读",
                                "导入时间",
                                "书籍名称",
                                "阅读进度"
                            )
                            val itemHeightDp = 38.dp
                            val itemSpacingDp = 2.dp
                            val itemSlotHeightPx = with(density) { (itemHeightDp + itemSpacingDp).toPx() }

                            var isDraggingSlider by remember { mutableStateOf(false) }
                            var localDragProgress by remember { mutableFloatStateOf(sortMethod.toFloat()) }
                            
                            LaunchedEffect(sortMethod, isDraggingSlider) {
                                if (!isDraggingSlider) {
                                    localDragProgress = sortMethod.toFloat()
                                }
                            }

                            val animatedThumbProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isDraggingSlider) localDragProgress else sortMethod.toFloat(),
                                animationSpec = if (isDraggingSlider) {
                                    spring(dampingRatio = 0.90f, stiffness = 800f)
                                } else {
                                    spring(dampingRatio = 0.50f, stiffness = 340f)
                                },
                                label = "sortThumbProgress"
                            )

                            val currentHoverIndex = if (isDraggingSlider) {
                                kotlin.math.round(localDragProgress).toInt().coerceIn(0, sortOptions.size - 1)
                            } else {
                                sortMethod
                            }

                            val sortTextBackdrop = rememberLayerBackdrop()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((itemHeightDp + itemSpacingDp) * sortOptions.size - itemSpacingDp)
                                    .pointerInput(sortOptions.size) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isDraggingSlider = true
                                                localDragProgress = (offset.y / itemSlotHeightPx).coerceIn(0f, (sortOptions.size - 1).toFloat())
                                            },
                                            onDragEnd = {
                                                val finalIndex = kotlin.math.round(localDragProgress).toInt().coerceIn(0, sortOptions.size - 1)
                                                viewModel.setSortMethod(finalIndex)
                                                isDraggingSlider = false
                                            },
                                            onDragCancel = {
                                                isDraggingSlider = false
                                                localDragProgress = sortMethod.toFloat()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                localDragProgress = (localDragProgress + dragAmount.y / itemSlotHeightPx).coerceIn(-0.2f, (sortOptions.size - 0.8f))
                                            }
                                        )
                                    }
                            ) {
                                // 1. Base Layer: Inactive/Neutral Dark Options Text
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacingDp)
                                ) {
                                    sortOptions.forEach { label ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(itemHeightDp)
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 14.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                color = secondaryTextColor.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                // 2. Hidden Layer: Active Highlighted Theme Accent Text (captured by sortTextBackdrop)
                                val themeAccent = getThemeAccentColor(appTheme, if (isCustomThemeThreeColors) customColors else customColors.take(2))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(0f)
                                        .layerBackdrop(sortTextBackdrop),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacingDp)
                                ) {
                                    sortOptions.forEach { label ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(itemHeightDp)
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 14.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                                color = themeAccent
                                            )
                                        }
                                    }
                                }

                                // 3. The Glass Slider Capsule Thumb (CombinedBackdrop with strong edge lens refraction)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeightDp)
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            val y = (animatedThumbProgress * itemSlotHeightPx).fastRoundToInt()
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, y)
                                            }
                                        }
                                        .drawBackdrop(
                                            backdrop = rememberCombinedBackdrop(sortDialogBackdrop, sortTextBackdrop),
                                            shape = { RoundedCornerShape(12.dp) },
                                            effects = {
                                                vibrancy()
                                                lens(
                                                    refractionHeight = 14f.dp.toPx(),
                                                    refractionAmount = 20f.dp.toPx(),
                                                    depthEffect = true
                                                )
                                            },
                                            highlight = { Highlight.Default },
                                            shadow = { Shadow(alpha = 0.20f) },
                                            innerShadow = {
                                                InnerShadow(
                                                    radius = 8.dp,
                                                    alpha = 0.55f
                                                )
                                            }
                                        )
                                )

                                // 4. Interactive Clickable Targets
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacingDp)
                                ) {
                                    sortOptions.forEachIndexed { index, _ ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(itemHeightDp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(
                                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.setSortMethod(index)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        showEditDialogForBook?.let { book ->
            EditBookDialog(
                book = book,
                onDismissRequest = { showEditDialogForBook = null },
                onConfirm = { newTitle, newCoverUri ->
                    viewModel.updateBookInfo(book, newTitle, newCoverUri, context)
                    showEditDialogForBook = null
                }
            )
        }
        
    } // Close root Box
} // Close BookshelfScreen

@Composable
fun SeriesItem(
    seriesName: String,
    books: List<BookEntity>,
    rootCoords: LayoutCoordinates?,
    backdrop: com.kyant.backdrop.Backdrop,
    isHidden: Boolean = false,
    isListLayout: Boolean = false,
    isDark: Boolean = false,
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866),
    onClick: (Rect) -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var localBounds by remember { mutableStateOf(Rect.Zero) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
        label = "seriesPressScale"
    )

    Box(
        modifier = Modifier
            .padding(6.dp)
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { coords ->
                rootCoords?.let { root ->
                    localBounds = root.localBoundingBoxOf(coords, clipBounds = false)
                }
            }
            .combinedClickable(
                onClick = { onClick(localBounds) },
                onLongClick = onLongClick?.let { handler ->
                    {
                        isPressed = true
                        handler()
                        isPressed = false
                    }
                }
            )
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(20.dp) },
                effects = {
                    vibrancy()
                    blur(3f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                shadow = { 
                    Shadow(
                        radius = 10.dp,
                        color = Color.Black.copy(alpha = 0.08f)
                    ) 
                },
                onDrawSurface = { 
                    drawRect(Color.White.copy(alpha = 0.12f)) 
                }
            )
            .padding(10.dp)
    ) {
        if (isListLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val maxVisible = minOf(3, books.size)
                    for (i in maxVisible - 1 downTo 0) {
                        val book = books[i]
                        val rank = maxVisible - 1 - i
                        val offsetDp = rank * 4
                        val scaleVal = 1f - (rank * 0.05f)
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = (-offsetDp).dp, x = offsetDp.dp)
                                .graphicsLayer {
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                }
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = (maxVisible - i).dp
                        ) {
                            if (book.coverImage != null && File(book.coverImage).exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(File(book.coverImage)).crossfade(true).build(),
                                    contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = primaryTextColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${books.size} 册全集",
                        color = secondaryTextColor,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "系列合集",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(0.7f)
                        .fillMaxWidth()
                ) {
                    val maxVisible = minOf(3, books.size)
                    for (i in maxVisible - 1 downTo 0) {
                        val book = books[i]
                        val rank = maxVisible - 1 - i
                        val offsetDx = (rank * 7).dp
                        val offsetDy = (-rank * 5).dp
                        val scaleVal = 1f - (rank * 0.05f)
                        val rotVal = when (rank) {
                            1 -> 3.5f
                            2 -> -3f
                            else -> 0f
                        }
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = offsetDx, y = offsetDy)
                                .graphicsLayer {
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                    rotationZ = rotVal
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .border(0.6.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = (6 - rank * 2).dp
                        ) {
                            if (book.coverImage != null && File(book.coverImage).exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(book.coverImage))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("暂无封面", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${books.size} 册", color = Color.White, fontSize = 10.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = primaryTextColor,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesItemContent(
    seriesName: String,
    books: List<BookEntity>,
    isDark: Boolean = false,
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866)
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.7f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val maxVisible = minOf(3, books.size)
            for (i in maxVisible - 1 downTo 0) {
                val book = books[i]
                val rank = maxVisible - 1 - i
                val offsetDx = (rank * 7).dp
                val offsetDy = (-rank * 5).dp
                val scaleVal = 1f - (rank * 0.05f)
                val rotVal = when (rank) {
                    1 -> 3.5f
                    2 -> -3f
                    else -> 0f
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = offsetDx, y = offsetDy)
                        .graphicsLayer {
                            scaleX = scaleVal
                            scaleY = scaleVal
                            rotationZ = rotVal
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .border(0.6.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = (6 - rank * 2).dp
                ) {
                    if (book.coverImage != null && File(book.coverImage).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(book.coverImage))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("暂无封面", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("${books.size} 册", color = Color.White, fontSize = 10.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = seriesName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = primaryTextColor,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    book: BookEntity,
    isListLayout: Boolean = false,
    isPressed: Boolean = false,
    backdrop: com.kyant.backdrop.Backdrop,
    isDark: Boolean = false,
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866),
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 420f),
        label = "bookPressScale"
    )

    Box(
        modifier = modifier
            .padding(6.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(20.dp) },
                effects = {
                    vibrancy()
                    blur(3f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                shadow = { 
                    Shadow(
                        radius = 10.dp,
                        color = Color.Black.copy(alpha = 0.08f)
                    ) 
                },
                onDrawSurface = { 
                    drawRect(Color.White.copy(alpha = 0.12f)) 
                }
            )
            .padding(10.dp)
    ) {
        if (isListLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (book.coverImage != null && File(book.coverImage).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(book.coverImage))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        ) {
                            Text("暂无", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.author ?: "未知作者",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (book.isWebDav) "云端同步" else "本地导入",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(0.7f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (book.coverImage != null && File(book.coverImage).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(book.coverImage))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        ) {
                            Text("暂无封面", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = primaryTextColor,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesInnerBookRow(
    book: BookEntity,
    seriesTitle: String,
    volumeNumber: Int,
    isPressed: Boolean = false,
    isDark: Boolean = false,
    backdrop: com.kyant.backdrop.Backdrop? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    modifier: Modifier = Modifier
) {
    val compactTitle = book.title
        .removePrefix(seriesTitle)
        .trim()
        .removePrefix(seriesTitle)
        .trim()
        .let { title ->
            if (title.matches(Regex("[0-9０-９]+"))) "第 $title 卷" else title
        }
        .ifBlank { "第 $volumeNumber 卷" }
        
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "seriesBookPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (backdrop != null) {
                    Modifier
                        .drawWithContent {
                            listState?.firstVisibleItemScrollOffset
                            listState?.firstVisibleItemIndex
                            drawContent()
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(18.dp) },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(
                                    refractionHeight = 10f.dp.toPx(),
                                    refractionAmount = 18f.dp.toPx(),
                                    chromaticAberration = true,
                                    depthEffect = true
                                )
                            },
                            highlight = { Highlight.Default },
                            shadow = {
                                Shadow(
                                    radius = 6.dp,
                                    color = Color.Black.copy(alpha = 0.08f)
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 8.dp,
                                    alpha = if (isPressed) 0.45f else 0.22f
                                )
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isPressed) 0.24f else 0.14f))
                            }
                        )
                } else {
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            width = 0.6.dp,
                            color = Color.White.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(18.dp)
                        )
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val volumeColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF6C4A70).copy(alpha = 0.90f)
            val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF231130)
            val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF765D7A).copy(alpha = 0.85f)

            Text(
                text = volumeNumber.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = volumeColor,
                modifier = Modifier.width(32.dp)
            )
            Box(
                modifier = Modifier
                    .size(44.dp, 64.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (book.coverImage != null && File(book.coverImage).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(book.coverImage))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    ) {
                        Text("暂无", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = compactTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (book.isWebDav) "云端分卷" else "本地已导入",
                    style = MaterialTheme.typography.labelMedium,
                    color = subtitleColor
                )
            }
        }
    }
}
