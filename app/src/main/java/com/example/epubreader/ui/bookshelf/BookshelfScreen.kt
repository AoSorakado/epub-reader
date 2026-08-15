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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.epubreader.ui.components.Screen
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

sealed class ContextMenuTarget {
    data class Book(val book: BookEntity, val isInner: Boolean = false) : ContextMenuTarget()
    data class Series(val series: Pair<String, List<BookEntity>>) : ContextMenuTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    globalBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onReaderActiveChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val dao = AppDatabase.getDatabase(context).bookDao()
    val viewModel: BookshelfViewModel = viewModel(factory = BookshelfViewModelFactory(dao, context.applicationContext as android.app.Application))

    val books by viewModel.books.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // Use globalBackdrop passed from MainScaffold for glass effects
    
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    var contextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var activeContextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var lastTargetBounds by remember { mutableStateOf(Rect.Zero) }
    var showEditDialogForBook by remember { mutableStateOf<BookEntity?>(null) }
    var activeEditBook by remember { mutableStateOf<BookEntity?>(null) }
    var editSourceBounds by remember { mutableStateOf(Rect.Zero) }
    var isEditExpanded by remember { mutableStateOf(false) }
    var isEditingInnerBook by remember { mutableStateOf(false) }
    var editInnerSeriesInfo by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val editTransition = updateTransition(targetState = isEditExpanded, label = "EditMorphTransition")
    val editExpandProgress by editTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.82f, stiffness = 220f)
            } else {
                spring(dampingRatio = 0.86f, stiffness = 260f)
            }
        },
        label = "editExpandProgress"
    ) { if (it) 1f else 0f }

    LaunchedEffect(editExpandProgress, isEditExpanded) {
        if (!isEditExpanded && editExpandProgress <= 0.001f && showEditDialogForBook != null) {
            showEditDialogForBook = null
            activeEditBook = null
            editSourceBounds = Rect.Zero
            isEditingInnerBook = false
            editInnerSeriesInfo = null
        }
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortButtonBounds by remember { mutableStateOf(Rect.Zero) }
    val seriesCoords = remember { mutableStateMapOf<String, LayoutCoordinates>() }

    LaunchedEffect(showEditDialogForBook) {
        if (showEditDialogForBook != null) {
            activeEditBook = showEditDialogForBook
        }
    }

    LaunchedEffect(contextMenuTarget) {
        if (contextMenuTarget != null) {
            activeContextMenuTarget = contextMenuTarget
        }
    }

    val isContextMenuOpen = (contextMenuTarget != null)
    val contextMenuTransition = updateTransition(targetState = isContextMenuOpen, label = "ContextMenuTransition")
    val contextMenuProgress by contextMenuTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.80f, stiffness = 240f)
            } else {
                spring(dampingRatio = 0.85f, stiffness = 280f)
            }
        },
        label = "contextMenuProgress"
    ) { if (it) 1f else 0f }

    val isContextMenuOverlayActive = (contextMenuProgress > 0.001f || contextMenuTarget != null)

    LaunchedEffect(contextMenuProgress) {
        if (contextMenuProgress <= 0.001f && contextMenuTarget == null) {
            activeContextMenuTarget = null
            lastTargetBounds = Rect.Zero
        }
    }

    val morphProgress by animateFloatAsState(
        targetValue = if (showSortMenu) 1f else 0f,
        animationSpec = if (showSortMenu) {
            spring(dampingRatio = 0.82f, stiffness = 220f)
        } else {
            spring(dampingRatio = 0.86f, stiffness = 260f)
        },
        label = "sortMorphProgress"
    )

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
    var openingBook by remember { mutableStateOf<BookEntity?>(null) }
    var openingBookBounds by remember { mutableStateOf(Rect.Zero) }
    var openingBookIsListLayout by remember { mutableStateOf(false) }
    var isOpeningBookExpanded by remember { mutableStateOf(false) }

    val isOpeningBookActive = openingBook != null
    val bookOpenTransition = updateTransition(targetState = isOpeningBookExpanded, label = "BookOpenTransition")
    val bookOpenProgress by bookOpenTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.80f, stiffness = 180f)
            } else {
                spring(dampingRatio = 0.85f, stiffness = 210f)
            }
        },
        label = "bookOpenProgress"
    ) { if (it) 1f else 0f }

    LaunchedEffect(bookOpenProgress, isOpeningBookExpanded) {
        val isReading = isOpeningBookExpanded || bookOpenProgress > 0.001f
        onReaderActiveChanged?.invoke(isReading)
        if (!isOpeningBookExpanded && bookOpenProgress <= 0.001f && openingBook != null) {
            openingBook = null
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val handleBookClick: (BookEntity, Boolean) -> Unit = { book, isList ->
        if (!isOpeningBookActive) {
            val coords = bookCoords[book.id]
            val bounds = if (coords != null && rootCoords != null) {
                try {
                    rootCoords!!.localBoundingBoxOf(coords, clipBounds = false)
                } catch (e: Exception) { Rect.Zero }
            } else Rect.Zero

            if (bounds != Rect.Zero) {
                openingBook = book
                openingBookBounds = bounds
                openingBookIsListLayout = isList
                isOpeningBookExpanded = true
            } else {
                val fallbackBounds = Rect(
                    screenWidthPx * 0.1f, screenHeightPx * 0.3f,
                    screenWidthPx * 0.9f, screenHeightPx * 0.7f
                )
                openingBook = book
                openingBookBounds = fallbackBounds
                openingBookIsListLayout = isList
                isOpeningBookExpanded = true
            }
        }
    }

    val appTheme by settingsViewModel.appTheme.collectAsState()
    val isCustomThemeThreeColors by settingsViewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by settingsViewModel.customColors.collectAsState()

    val isDark = appTheme == AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF543866).copy(alpha = 0.8f)

    val themeAccent = getThemeAccentColor(
        theme = appTheme,
        customColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
    )
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
    
    var displaySeries by remember { mutableStateOf<Pair<String, List<BookEntity>>?>(null) }
    var isSeriesExpanded by remember { mutableStateOf(false) }

    val seriesTransition = updateTransition(targetState = isSeriesExpanded, label = "SeriesMorphTransition")
    val seriesExpandProgress by seriesTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.82f, stiffness = 220f)
            } else {
                spring(dampingRatio = 0.86f, stiffness = 260f)
            }
        },
        label = "seriesMorphProgress"
    ) { if (it) 1f else 0f }

    LaunchedEffect(seriesExpandProgress, isSeriesExpanded) {
        if (!isSeriesExpanded && seriesExpandProgress <= 0.001f && displaySeries != null) {
            displaySeries = null
            selectedSeries = null
        }
    }

    val handleSeriesClick: (Pair<String, List<BookEntity>>, Rect) -> Unit = { series, bounds ->
        if (!isOpeningBookActive && !isSeriesExpanded) {
            sourceBounds = bounds
            selectedSeries = series
            displaySeries = series
            isSeriesExpanded = true
        }
    }

    val handleSeriesDismiss: () -> Unit = {
        if (!isOpeningBookActive && isSeriesExpanded) {
            isSeriesExpanded = false
        }
    }

    val backgroundBlurRadius = maxOf(
        lerp(0f, 16f, seriesExpandProgress),
        lerp(0f, 16f, editExpandProgress),
        lerp(0f, 16f, bookOpenProgress),
        if (contextMenuTarget != null) 16f else 0f
    ).dp

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
                            LiquidButton(
                                onClick = {
                                    localImportLauncher.launch(arrayOf("application/epub+zip", "text/plain", "application/octet-stream", "*/*"))
                                },
                                backdrop = globalBackdrop,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "导入书籍",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

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
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
            items(groupedItems) { item ->
                when (item) {
                    is BookEntity -> {
                        val isOpeningThis = (openingBook?.id == item.id && bookOpenProgress > 0.001f)
                        val dragModifier = if (sortMethod == 2) {
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
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            contextMenuTarget = ContextMenuTarget.Book(item, isInner = false)
                                        }
                                        draggedItem = null
                                    },
                                    onDragCancel = { 
                                        if (!hasDragged) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            contextMenuTarget = ContextMenuTarget.Book(item, isInner = false)
                                        }
                                        draggedItem = null 
                                    }
                                )
                            }
                        } else Modifier

                        val currentContextMenu = contextMenuTarget ?: activeContextMenuTarget
                        val isItemContextMenuActive = isContextMenuOverlayActive && (currentContextMenu is ContextMenuTarget.Book && currentContextMenu.book.id == item.id && !currentContextMenu.isInner)
                        val isEditingThis = (showEditDialogForBook?.id == item.id && (isEditExpanded || editExpandProgress > 0.001f)) || (activeEditBook?.id == item.id && (isEditExpanded || editExpandProgress > 0.001f))
                        BookItem(
                            book = item, 
                            isListLayout = layoutMethod == 1, 
                            isPressed = false, 
                            backdrop = globalBackdrop, 
                            isDark = isDark,
                            isHidden = isOpeningThis || isItemContextMenuActive || isEditingThis,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onPositioned = { coords ->
                                bookCoords[item.id] = coords
                            },
                            onClick = {
                                handleBookClick(item, layoutMethod == 1)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                contextMenuTarget = ContextMenuTarget.Book(item, isInner = false)
                            },
                            modifier = dragModifier.padding(6.dp)
                        )
                    }
                    is Pair<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val series = item as Pair<String, List<BookEntity>>
                        val currentContextMenu = contextMenuTarget ?: activeContextMenuTarget
                        val isSeriesContextMenuActive = isContextMenuOverlayActive && (currentContextMenu is ContextMenuTarget.Series && currentContextMenu.series.first == series.first)
                        val isSeriesHidden = (displaySeries?.first == series.first && seriesExpandProgress > 0.001f) || isSeriesContextMenuActive
                        SeriesItem(
                            seriesName = series.first,
                            books = series.second,
                            rootCoords = rootCoords,
                            backdrop = globalBackdrop,
                            isHidden = isSeriesHidden,
                            isListLayout = layoutMethod == 1,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onPositioned = { coords ->
                                seriesCoords[series.first] = coords
                            },
                            onClick = { bounds ->
                                handleSeriesClick(series, bounds)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                contextMenuTarget = ContextMenuTarget.Series(series)
                            },
                            modifier = Modifier.padding(6.dp)
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
        }
        
        }
    }

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
            val currentRadius = lerp(20f, 28f, seriesExpandProgress).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.10f * seriesExpandProgress).coerceIn(0f, 1f)))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        handleSeriesDismiss()
                    }
            )

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
                        shape = { RoundedCornerShape(with(density) { currentRadius.coerceAtLeast(0f).dp }) },
                        effects = {
                            vibrancy()
                            blur(6f.dp.toPx())
                            lens(
                                refractionHeight = 16f.dp.toPx(),
                                refractionAmount = 32f.dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            Shadow(
                                radius = 20.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.35f else 0.15f)
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                        }
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                if (seriesExpandProgress < 0.40f) {
                    val collapsedAlpha = (1f - (seriesExpandProgress / 0.35f)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SeriesItemContent(
                            seriesName = seriesTitle,
                            books = seriesBooks,
                            isListLayout = layoutMethod == 1,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

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
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
                                    .border(0.6.dp, Color.White.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                    .clickable { handleSeriesDismiss() },
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
                                val isBookOpeningThis = (openingBook?.id == book.id && bookOpenProgress > 0.001f)
                                val currentContextMenu = contextMenuTarget ?: activeContextMenuTarget
                                val isInnerContextMenuActive = isContextMenuOverlayActive && (currentContextMenu is ContextMenuTarget.Book && currentContextMenu.book.id == book.id && currentContextMenu.isInner)
                                val isEditingThis = (showEditDialogForBook?.id == book.id && (isEditExpanded || editExpandProgress > 0.001f)) || (activeEditBook?.id == book.id && (isEditExpanded || editExpandProgress > 0.001f))
                                SeriesInnerBookRow(
                                    book = book,
                                    seriesTitle = seriesTitle,
                                    volumeNumber = volumeNumber,
                                    isPressed = false,
                                    isDark = isDark,
                                    themeAccent = themeAccent,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isHidden = isBookOpeningThis || isInnerContextMenuActive || isEditingThis,
                                    onPositioned = { coords ->
                                        bookCoords[book.id] = coords
                                    },
                                    onClick = {
                                        handleBookClick(book, true)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        contextMenuTarget = ContextMenuTarget.Book(book, isInner = true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (bookOpenProgress > 0.001f && openingBook != null) {
            val book = openingBook!!
            val targetLeft = 0f
            val targetTop = 0f
            val targetWidth = screenWidthPx
            val targetHeight = screenHeightPx

            val currentLeft = lerp(openingBookBounds.left, targetLeft, bookOpenProgress)
            val currentTop = lerp(openingBookBounds.top, targetTop, bookOpenProgress)
            val currentWidth = lerp(openingBookBounds.width, targetWidth, bookOpenProgress).coerceAtLeast(1f)
            val currentHeight = lerp(openingBookBounds.height, targetHeight, bookOpenProgress).coerceAtLeast(1f)
            val currentRadius = lerp(20f, 0f, bookOpenProgress).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.55f * bookOpenProgress).coerceIn(0f, 1f)))
            )

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
                        backdrop = globalBackdrop,
                        shape = { RoundedCornerShape(with(density) { currentRadius.coerceAtLeast(0f).dp }) },
                        effects = {
                            vibrancy()
                            blur(lerp(3f, 16f, bookOpenProgress).coerceAtLeast(0.1f).dp.toPx())
                            lens(
                                refractionHeight = lerp(16f, 32f, bookOpenProgress).coerceAtLeast(0.1f).dp.toPx(),
                                refractionAmount = lerp(32f, 56f, bookOpenProgress).coerceAtLeast(0.1f).dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = {
                            val baseColor = if (isDark) Color(0xFF0F172A) else Color.White
                            drawRect(baseColor.copy(alpha = lerp(0.12f, 0.95f, bookOpenProgress).coerceIn(0f, 1f)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bookOpenProgress < 0.40f) {
                    val collapsedAlpha = (1f - (bookOpenProgress / 0.35f)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BookItemContent(
                            book = book,
                            isListLayout = openingBookIsListLayout,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

                if (bookOpenProgress > 0.15f && bookOpenProgress < 0.95f) {
                    val atmosphereAlpha = if (bookOpenProgress < 0.70f) {
                        ((bookOpenProgress - 0.15f) / 0.45f).coerceIn(0f, 1f)
                    } else {
                        (1f - ((bookOpenProgress - 0.70f) / 0.25f)).coerceIn(0f, 1f)
                    }
                    val contentScale = lerp(0.92f, 1f, bookOpenProgress)

                    if (openingBookIsListLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = atmosphereAlpha
                                    scaleX = contentScale
                                    scaleY = contentScale
                                }
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (book.coverImage != null && File(book.coverImage).exists()) {
                                val coverWidth = lerp(60f, 130f, bookOpenProgress).dp
                                Box(
                                    modifier = Modifier
                                        .width(coverWidth)
                                        .aspectRatio(0.7f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(File(book.coverImage))
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                    color = primaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = book.author ?: "未知作者",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "正在载入阅读...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = atmosphereAlpha
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
                                            .crossfade(false)
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
                
                if (bookOpenProgress >= 0.70f) {
                    val readerAlpha = ((bookOpenProgress - 0.70f) / 0.30f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = readerAlpha
                            }
                    ) {
                        com.example.epubreader.ui.reader.ReaderScreen(
                            navController = navController,
                            bookId = book.id,
                            settingsViewModel = settingsViewModel,
                            backgroundBackdrop = bookshelfBackdrop,
                            onBackClick = {
                                isOpeningBookExpanded = false // Smoothly collapses back to book card on bookshelf!
                            }
                        )
                    }
                }
            }
        }

        // ── Unified Tactile Context Menu Overlay ──────────────────────────────────────
        if (contextMenuProgress > 0.001f || contextMenuTarget != null) {
            // Soft Frosted Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contextMenuProgress * 0.22f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        contextMenuTarget = null
                    }
            )

            val target = contextMenuTarget ?: activeContextMenuTarget

            // Target Bounds and Position
            val currentBounds = when (target) {
                is ContextMenuTarget.Book -> {
                    val coords = bookCoords[target.book.id]
                    if (coords != null && coords.isAttached && rootCoords != null && rootCoords!!.isAttached) {
                        try { rootCoords!!.localBoundingBoxOf(coords, clipBounds = false) } catch (e: Exception) { Rect.Zero }
                    } else Rect.Zero
                }
                is ContextMenuTarget.Series -> {
                    val coords = seriesCoords[target.series.first]
                    if (coords != null && coords.isAttached && rootCoords != null && rootCoords!!.isAttached) {
                        try { rootCoords!!.localBoundingBoxOf(coords, clipBounds = false) } catch (e: Exception) { Rect.Zero }
                    } else Rect.Zero
                }
                null -> Rect.Zero
            }

            if (currentBounds != Rect.Zero && currentBounds.width > 10f && currentBounds.height > 10f) {
                lastTargetBounds = currentBounds
            }

            val effectiveTargetBounds = if (lastTargetBounds != Rect.Zero) {
                lastTargetBounds
            } else {
                currentBounds
            }

            if (effectiveTargetBounds != Rect.Zero && target != null) {
                val elevatedScale = lerp(1.0f, 1.04f, contextMenuProgress)

                // 1. Focused Elevated Floating Item
                Box(
                    modifier = Modifier
                        .offset { IntOffset(effectiveTargetBounds.left.toInt(), effectiveTargetBounds.top.toInt()) }
                        .size(
                            width = with(density) { effectiveTargetBounds.width.toDp() },
                            height = with(density) { effectiveTargetBounds.height.toDp() }
                        )
                        .graphicsLayer {
                            alpha = if (showEditDialogForBook != null || isEditExpanded) 0f else 1f
                            scaleX = elevatedScale
                            scaleY = elevatedScale
                        }
                ) {
                    when (target) {
                        is ContextMenuTarget.Book -> {
                            if (target.isInner) {
                                val seriesBooks = selectedSeries?.second ?: emptyList()
                                val index = seriesBooks.indexOfFirst { it.id == target.book.id }
                                SeriesInnerBookRow(
                                    book = target.book,
                                    seriesTitle = selectedSeries?.first ?: "",
                                    volumeNumber = if (index >= 0) index + 1 else 1,
                                    isPressed = false,
                                    isDark = isDark,
                                    themeAccent = themeAccent,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            } else {
                                BookItem(
                                    book = target.book,
                                    isListLayout = layoutMethod == 1,
                                    isPressed = false,
                                    backdrop = globalBackdrop,
                                    isDark = isDark,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                        is ContextMenuTarget.Series -> {
                            SeriesItem(
                                seriesName = target.series.first,
                                books = target.series.second,
                                rootCoords = rootCoords,
                                backdrop = globalBackdrop,
                                isListLayout = layoutMethod == 1,
                                isDark = isDark,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = {}
                            )
                        }
                    }
                }

                // 2. Liquid Glass Floating Menu
                val menuWidthPx = with(density) { 190.dp.toPx() }
                val menuHeightPx = with(density) { 180.dp.toPx() }
                val margin = with(density) { 16.dp.toPx() }
                val spacing = with(density) { 12.dp.toPx() }

                val menuLeft = (effectiveTargetBounds.left + effectiveTargetBounds.width / 2f - menuWidthPx / 2f)
                    .coerceIn(margin, screenWidthPx - menuWidthPx - margin)

                var menuTop = effectiveTargetBounds.bottom + spacing
                if (menuTop + menuHeightPx > screenHeightPx - margin) {
                    menuTop = effectiveTargetBounds.top - menuHeightPx - spacing
                }
                menuTop = menuTop.coerceIn(margin, screenHeightPx - menuHeightPx - margin)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(menuLeft.toInt(), menuTop.toInt()) }
                        .graphicsLayer {
                            alpha = contextMenuProgress
                            scaleX = lerp(0.85f, 1f, contextMenuProgress)
                            scaleY = lerp(0.85f, 1f, contextMenuProgress)
                        }
                ) {
                    val menuItems = when (target) {
                        is ContextMenuTarget.Book -> {
                            if (target.isInner) {
                                listOf(
                                    ContextMenuItem(
                                        title = "立即阅读",
                                        icon = Icons.Filled.AutoStories,
                                        onClick = {
                                            val b = target.book
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                            handleBookClick(b, true)
                                        }
                                    ),
                                    ContextMenuItem(
                                        title = "编辑此卷",
                                        icon = Icons.Filled.Edit,
                                        onClick = {
                                            val b = target.book
                                            val bounds = if (effectiveTargetBounds != Rect.Zero) effectiveTargetBounds else Rect.Zero
                                            val seriesBooks = selectedSeries?.second ?: emptyList()
                                            val index = seriesBooks.indexOfFirst { it.id == b.id }
                                            val volNum = if (index >= 0) index + 1 else 1
                                            val sTitle = selectedSeries?.first ?: (b.seriesName ?: "")

                                            editSourceBounds = bounds
                                            isEditingInnerBook = true
                                            editInnerSeriesInfo = Pair(sTitle, volNum)
                                            showEditDialogForBook = b
                                            activeEditBook = b
                                            isEditExpanded = true
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                        }
                                    ),
                                    ContextMenuItem(
                                        title = "移出此系列",
                                        icon = Icons.Filled.DriveFileMove,
                                        onClick = {
                                            val b = target.book
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                            viewModel.updateBook(b.copy(seriesName = null))
                                            selectedSeries = null
                                        }
                                    ),
                                    ContextMenuItem(
                                        title = "删除此卷",
                                        icon = Icons.Filled.Delete,
                                        isDestructive = true,
                                        onClick = {
                                            val b = target.book
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                            bookToDelete = b
                                        }
                                    )
                                )
                            } else {
                                listOf(
                                    ContextMenuItem(
                                        title = "立即阅读",
                                        icon = Icons.Filled.AutoStories,
                                        onClick = {
                                            val b = target.book
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                            handleBookClick(b, layoutMethod == 1)
                                        }
                                    ),
                                    ContextMenuItem(
                                        title = "编辑书籍",
                                        icon = Icons.Filled.Edit,
                                        onClick = {
                                            val b = target.book
                                            val bounds = if (effectiveTargetBounds != Rect.Zero && effectiveTargetBounds.width > 10f) effectiveTargetBounds else Rect.Zero
                                            editSourceBounds = bounds
                                            isEditingInnerBook = false
                                            editInnerSeriesInfo = null
                                            showEditDialogForBook = b
                                            activeEditBook = b
                                            isEditExpanded = true
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                        }
                                    ),
                                    ContextMenuItem(
                                        title = "删除书籍",
                                        icon = Icons.Filled.Delete,
                                        isDestructive = true,
                                        onClick = {
                                            val b = target.book
                                            contextMenuTarget = null
                                            activeContextMenuTarget = null
                                            lastTargetBounds = Rect.Zero
                                            bookToDelete = b
                                        }
                                    )
                                )
                            }
                        }
                        is ContextMenuTarget.Series -> {
                            listOf(
                                ContextMenuItem(
                                    title = "展开系列",
                                    icon = Icons.Filled.FolderOpen,
                                    onClick = {
                                        val s = target.series
                                        contextMenuTarget = null
                                        val coords = seriesCoords[s.first]
                                        val bounds = if (coords != null && rootCoords != null) {
                                            try { rootCoords!!.localBoundingBoxOf(coords, clipBounds = false) } catch (e: Exception) { Rect.Zero }
                                        } else Rect.Zero
                                        handleSeriesClick(s, bounds)
                                    }
                                ),
                                ContextMenuItem(
                                    title = "解散系列",
                                    icon = Icons.Filled.FolderOff,
                                    onClick = {
                                        val s = target.series
                                        contextMenuTarget = null
                                        s.second.forEach { book ->
                                            viewModel.updateBook(book.copy(seriesName = null))
                                        }
                                    }
                                ),
                                ContextMenuItem(
                                    title = "删除全系列",
                                    icon = Icons.Filled.Delete,
                                    isDestructive = true,
                                    onClick = {
                                        val s = target.series
                                        contextMenuTarget = null
                                        seriesLongPressTarget = s
                                    }
                                )
                            )
                        }
                        null -> emptyList()
                    }

                    GlassContextMenu(
                        backdrop = bookshelfBackdrop,
                        items = menuItems,
                        isDark = isDark,
                        primaryTextColor = primaryTextColor
                    )
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
                val currentCornerRadius = androidx.compose.ui.util.lerp(btnBounds.height / 2f, with(density) { 24.dp.toPx() }, morphProgress).coerceAtLeast(0f)

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
                                drawRect(Color.White.copy(alpha = 0.10f))
                            },
                            exportedBackdrop = sortDialogBackdrop
                        )
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent taps from dismissing through to scrim
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Morphing Icon: Sort icon centered inside button, fades out as it expands
                    if (morphProgress < 0.5f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = (1f - (morphProgress / 0.35f)).coerceIn(0f, 1f)
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
                    if (morphProgress > 0.15f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer {
                                    alpha = ((morphProgress - 0.20f) / 0.80f).coerceIn(0f, 1f)
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

        if ((isEditExpanded || editExpandProgress > 0.001f) && activeEditBook != null) {
            val book = activeEditBook!!
            val expandedWidthPx = minOf(screenWidthPx * 0.90f, with(density) { 420.dp.toPx() })
            val expandedHeightPx = minOf(screenHeightPx * 0.85f, with(density) { 440.dp.toPx() })
            val targetLeft = (screenWidthPx - expandedWidthPx) / 2f
            val targetTop = (screenHeightPx - expandedHeightPx) / 2f

            val initialBounds = if (editSourceBounds != Rect.Zero && editSourceBounds.width > 10f && editSourceBounds.height > 10f) {
                editSourceBounds
            } else {
                Rect(targetLeft, targetTop, targetLeft + expandedWidthPx, targetTop + expandedHeightPx)
            }

            val currentLeft = lerp(initialBounds.left, targetLeft, editExpandProgress)
            val currentTop = lerp(initialBounds.top, targetTop, editExpandProgress)
            val currentWidth = lerp(initialBounds.width, expandedWidthPx, editExpandProgress).coerceAtLeast(1f)
            val currentHeight = lerp(initialBounds.height, expandedHeightPx, editExpandProgress).coerceAtLeast(1f)
            val currentRadius = lerp(20f, 24f, editExpandProgress).coerceAtLeast(0f)

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.32f * editExpandProgress).coerceIn(0f, 1f)))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isEditExpanded = false
                    }
            )

            // Morphing Modal Box
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
                        shape = { RoundedCornerShape(with(density) { currentRadius.coerceAtLeast(0f).dp }) },
                        effects = {
                            vibrancy()
                            blur(6f.dp.toPx())
                            lens(
                                refractionHeight = 16f.dp.toPx(),
                                refractionAmount = 32f.dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            Shadow(
                                radius = 20.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.35f else 0.15f)
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                                Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                            )
                        ),
                        shape = RoundedCornerShape(with(density) { currentRadius.coerceAtLeast(0f).dp })
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                if (editExpandProgress < 0.35f) {
                    val collapsedAlpha = (1f - (editExpandProgress / 0.35f)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isEditingInnerBook) 6.dp else 10.dp, vertical = if (isEditingInnerBook) 4.dp else 10.dp)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                            },
                        contentAlignment = if (isEditingInnerBook || layoutMethod == 1) Alignment.CenterStart else Alignment.Center
                    ) {
                        if (isEditingInnerBook) {
                            val (sTitle, volNum) = editInnerSeriesInfo ?: Pair(book.seriesName ?: "", 1)
                            SeriesInnerBookRow(
                                book = book,
                                seriesTitle = sTitle,
                                volumeNumber = volNum,
                                isPressed = false,
                                isDark = isDark,
                                themeAccent = themeAccent,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        } else {
                            BookItemContent(
                                book = book,
                                isListLayout = layoutMethod == 1,
                                isDark = isDark,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }

                if (editExpandProgress > 0.15f) {
                    val contentAlpha = ((editExpandProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
                    val contentScale = lerp(0.92f, 1f, contentAlpha)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = contentAlpha
                                scaleX = contentScale
                                scaleY = contentScale
                            }
                    ) {
                        EditBookDialog(
                            book = book,
                            backdrop = bookshelfBackdrop,
                            isDark = isDark,
                            themeAccent = themeAccent,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            showCardContainer = false,
                            onDismissRequest = { isEditExpanded = false },
                            onConfirm = { newTitle, newAuthor, newSeries, newCoverUri ->
                                viewModel.updateBookInfo(book, newTitle, newAuthor, newSeries, newCoverUri, context)
                                isEditExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
    } // Close root Box
} // Close BookshelfScreen

@OptIn(ExperimentalFoundationApi::class)
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
    onPositioned: ((LayoutCoordinates) -> Unit)? = null,
    onClick: (Rect) -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var localBounds by remember { mutableStateOf(Rect.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val isItemPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isItemPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "seriesPressScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { coords ->
                onPositioned?.invoke(coords)
                rootCoords?.let { root ->
                    localBounds = root.localBoundingBoxOf(coords, clipBounds = false)
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(localBounds) },
                onLongClick = onLongClick
            )
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(22.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                shadow = { 
                    Shadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.20f else 0.08f)
                    ) 
                },
                onDrawSurface = { 
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.14f else 0.24f),
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.10f)
                            )
                        )
                    ) 
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(10.dp)
    ) {
        SeriesItemContent(
            seriesName = seriesName,
            books = books,
            isListLayout = isListLayout,
            isDark = isDark,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor
        )
    }
}

@Composable
fun SeriesItemContent(
    seriesName: String,
    books: List<BookEntity>,
    isListLayout: Boolean = false,
    isDark: Boolean = false,
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866)
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
                                model = ImageRequest.Builder(LocalContext.current).data(File(book.coverImage)).crossfade(false).build(),
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
                    color = primaryTextColor
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
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                0.7.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.20f))
                                ),
                                RoundedCornerShape(10.dp)
                            ),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = (8 - rank * 2).dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (book.coverImage != null && File(book.coverImage).exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(book.coverImage))
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))) {
                                    Text("暂无封面", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                }
                            }
                            // Spine 3D Shadow
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(6.dp)
                                    .align(Alignment.CenterStart)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.White.copy(alpha = 0.15f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
                
                // Series Volume Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Black.copy(alpha = 0.50f)
                                )
                            )
                        )
                        .border(
                            0.6.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.20f))
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${books.size} 册",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        letterSpacing = 0.2.sp
                    )
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

@Composable
fun BookItemContent(
    book: BookEntity,
    isListLayout: Boolean = false,
    isDark: Boolean = false,
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866)
) {
    if (isListLayout) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp, 84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        0.6.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                if (book.coverImage != null && File(book.coverImage).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(book.coverImage))
                            .crossfade(false)
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

                // Spine 3D illusion
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFinished = book.totalProgress >= 0.999f
                    val isUnread = book.totalProgress <= 0.0001f && (book.lastReadPosition.isNullOrEmpty() || book.lastReadPosition == "0_0_0")
                    val progressPercentFloat = (book.totalProgress * 100f).coerceIn(0.1f, 99.9f)
                    val progressFormatted = String.format(java.util.Locale.US, "%.1f", progressPercentFloat)
                    val statusText = if (isFinished) "已读完" else if (isUnread) "未读" else "进度 $progressFormatted%"
                    Text(
                        text = if (book.isWebDav) "云端同步" else "本地导入",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF)
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(secondaryTextColor.copy(alpha = 0.4f))
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (isFinished) Color(0xFF10B981) else (if (!isUnread) (if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF)) else secondaryTextColor)
                    )
                }
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
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        0.6.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                if (book.coverImage != null && File(book.coverImage).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(book.coverImage))
                            .crossfade(false)
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
                            .background(Color.Black.copy(alpha = 0.18f))
                    ) {
                        Text("暂无封面", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }

                // Physical 3D Book Spine illusion on left edge
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.White.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Frosted Glass Progress Pill Badge on Cover
                val isCoverFinished = book.totalProgress >= 0.999f
                val isCoverUnread = book.totalProgress <= 0.0001f && (book.lastReadPosition.isNullOrEmpty() || book.lastReadPosition == "0_0_0")
                val coverProgressPercentFloat = (book.totalProgress * 100f).coerceIn(0.1f, 99.9f)
                val coverProgressFormatted = String.format(java.util.Locale.US, "%.1f", coverProgressPercentFloat)
                val progressText = if (isCoverFinished) "已读完" else if (isCoverUnread) "未读" else "$coverProgressFormatted%"
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.50f)
                                )
                            )
                        )
                        .border(
                            0.5.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.15f))
                            ),
                            RoundedCornerShape(7.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = progressText,
                        color = if (isCoverFinished) Color(0xFF10B981) else Color(0xFFF1F5F9),
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }

                // Micro Glowing Progress Bar along bottom edge
                if (book.totalProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(book.totalProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF),
                                            if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1)
                                        )
                                    )
                                )
                        )
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
    isHidden: Boolean = false,
    onPositioned: ((LayoutCoordinates) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isItemPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isItemPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "bookPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onPositioned?.invoke(coords)
            }
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(22.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                shadow = { 
                    Shadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.20f else 0.08f)
                    ) 
                },
                onDrawSurface = { 
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.14f else 0.24f),
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.10f)
                            )
                        )
                    ) 
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(10.dp)
    ) {
        BookItemContent(
            book = book,
            isListLayout = isListLayout,
            isDark = isDark,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesInnerBookRow(
    book: BookEntity,
    seriesTitle: String,
    volumeNumber: Int,
    isPressed: Boolean = false,
    isDark: Boolean = false,
    themeAccent: Color = Color(0xFF007AFF),
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866),
    isHidden: Boolean = false,
    onPositioned: ((LayoutCoordinates) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
        
    val interactionSource = remember { MutableInteractionSource() }
    val isItemPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isItemPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "seriesBookPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .onGloballyPositioned { coords ->
                onPositioned?.invoke(coords)
            }
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) (if (isPressed || isItemPressed) 0.16f else 0.10f) else (if (isPressed || isItemPressed) 0.26f else 0.20f)),
                        Color.White.copy(alpha = if (isDark) (if (isPressed || isItemPressed) 0.08f else 0.05f) else (if (isPressed || isItemPressed) 0.14f else 0.10f))
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.25f else 0.45f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.20f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val volumeColor = if (isDark) Color(0xFF38BDF8) else themeAccent
            val titleColor = if (isDark) Color(0xFFF8FAFC) else primaryTextColor
            val subtitleColor = if (isDark) Color(0xFF94A3B8) else secondaryTextColor

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
                            .crossfade(false)
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

                // Micro progress track on cover bottom
                if (book.totalProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(book.totalProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            if (isDark) Color(0xFF38BDF8) else Color(0xFF007AFF),
                                            if (isDark) Color(0xFF818CF8) else Color(0xFF5856D6)
                                        )
                                    )
                                )
                        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFinished = book.totalProgress >= 0.999f
                    val isUnread = book.totalProgress <= 0.0001f && (book.lastReadPosition.isNullOrEmpty() || book.lastReadPosition == "0_0_0")
                    val progressPercentFloat = (book.totalProgress * 100f).coerceIn(0.1f, 99.9f)
                    val progressFormatted = String.format(java.util.Locale.US, "%.1f", progressPercentFloat)
                    val statusText = if (isFinished) "已读完" else if (isUnread) "未读" else "进度 $progressFormatted%"
                    Text(
                        text = if (book.isWebDav) "云端分卷" else "本地已导入",
                        style = MaterialTheme.typography.labelMedium,
                        color = subtitleColor
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(subtitleColor.copy(alpha = 0.4f))
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (isFinished) Color(0xFF34C759) else (if (!isUnread) volumeColor else subtitleColor)
                    )
                }
            }
        }
    }
}
