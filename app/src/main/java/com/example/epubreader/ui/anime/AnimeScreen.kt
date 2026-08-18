package com.example.epubreader.ui.anime

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.db.AnimeWithEpisodes
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.components.liquid.LiquidSegmentedControl
import com.example.epubreader.ui.components.liquid.LiquidVerticalSegmentedControl
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType
import com.example.epubreader.ui.theme.ClaudeUIFontFamily
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    backdrop: Backdrop,
    themeGradient: Brush = Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))),
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    webDavClient: WebDavClient?,
    hanimeViewModel: com.example.epubreader.ui.hanime.HanimeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onPlayEpisode: (anime: AnimeEntity, episode: AnimeEpisodeEntity) -> Unit
) {
    val context = LocalContext.current
    val animes by viewModel.filteredAnimes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val sortMethod by viewModel.sortMethod.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var isHanimeActive by rememberSaveable { mutableStateOf(false) }
    var titleClickCount by remember { mutableIntStateOf(0) }
    var lastTitleClickTime by remember { mutableLongStateOf(0L) }
    var isOnlineSearchActive by rememberSaveable { mutableStateOf(false) }

    var isGridView by remember { mutableStateOf(true) }
    var selectedAnimeForAction by remember { mutableStateOf<AnimeEntity?>(null) }
    var showRematchDialog by remember { mutableStateOf(false) }
    var rematchKeyword by remember { mutableStateOf("") }
    
    // Sort Menu States
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }
    var sortButtonBounds by remember { mutableStateOf(Rect.Zero) }

    // Series Card Expansion States (100% identical to Bookshelf series expansion)
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardCoords = remember { mutableMapOf<Long, LayoutCoordinates>() }
    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    var selectedAnime by remember { mutableStateOf<AnimeWithEpisodes?>(null) }
    var displayAnime by remember { mutableStateOf<AnimeWithEpisodes?>(null) }
    var isAnimeExpanded by remember { mutableStateOf(false) }
    var inspectingEpisode by remember { mutableStateOf<AnimeEpisodeEntity?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val animeBackdrop = rememberLayerBackdrop {
        drawRect(brush = themeGradient)
        drawContent()
    }
    val sortDialogBackdrop = rememberLayerBackdrop()

    // Sort Menu Morph Animation
    val sortTransition = updateTransition(targetState = showSortMenu, label = "SortMorphTransition")
    val morphProgress by sortTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.78f, stiffness = 420f)
            } else {
                spring(dampingRatio = 0.86f, stiffness = 380f)
            }
        },
        label = "sortMenuMorph"
    ) { if (it) 1f else 0f }

    // Anime Expansion Morph Animation (Relaxed Visual Tempo Q-bounce spring)
    val seriesTransition = updateTransition(targetState = isAnimeExpanded, label = "AnimeSeriesMorphTransition")
    val seriesExpandProgress by seriesTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.58f, stiffness = 115f) },
        label = "seriesMorphProgress"
    ) { if (it) 1f else 0f }

    val isAnimeTransitioning = seriesTransition.currentState != seriesTransition.targetState || seriesTransition.currentState

    LaunchedEffect(isAnimeTransitioning, isAnimeExpanded) {
        if (!isAnimeExpanded && !isAnimeTransitioning && displayAnime != null) {
            displayAnime = null
            selectedAnime = null
        }
    }

    val handleAnimeClick: (AnimeWithEpisodes, Rect) -> Unit = { animeWithEps, bounds ->
        if (!isAnimeExpanded) {
            sourceBounds = bounds
            selectedAnime = animeWithEps
            displayAnime = animeWithEps
            isAnimeExpanded = true
        }
    }

    val handleAnimeDismiss: () -> Unit = {
        if (isAnimeExpanded) {
            isAnimeExpanded = false
        }
    }

    // Auto trigger background enrichment
    LaunchedEffect(animes.size) {
        if (animes.any { it.coverUrl.isNullOrBlank() }) {
            viewModel.triggerEnrichment()
        }
    }

    // Auto reload expanded anime when single-anime refresh finishes
    val isRefreshingAnimeId by viewModel.isRefreshingSingleAnime.collectAsState()
    LaunchedEffect(isRefreshingAnimeId) {
        val curId = displayAnime?.anime?.id
        if (isRefreshingAnimeId == null && curId != null && isAnimeExpanded) {
            val withEpisodes = viewModel.getAnimeWithEpisodes(curId)
            if (withEpisodes != null) {
                displayAnime = withEpisodes
                selectedAnime = withEpisodes
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoords = it }
    ) {
        var topHeaderHeightDp by remember { mutableStateOf(160.dp) }
        val sliderProgress by animateFloatAsState(
            targetValue = filterStatus.toFloat(),
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
            label = "filterSlider"
        )

        // Layer 1: Full-Screen Scrolling Anime Grid / List (Captured by animeBackdrop so top bar & dialogs can refract posters)
        if (!isHanimeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(animeBackdrop)
            ) {
                if (animes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topHeaderHeightDp, bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Tv,
                            contentDescription = null,
                            tint = secondaryTextColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "未找到匹配的番剧" else "番剧库空空如也",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "尝试更换搜索关键词" else "点击右上角「刷新」按钮扫描 WebDAV 媒体库",
                            fontSize = 12.sp,
                            color = secondaryTextColor.copy(alpha = 0.6f)
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        top = topHeaderHeightDp + 6.dp,
                        bottom = 100.dp,
                        start = 14.dp,
                        end = 14.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(animes, key = { it.id }) { anime ->
                        val isHidden = (displayAnime?.anime?.id == anime.id && isAnimeTransitioning)
                        AnimeGridCard(
                            anime = anime,
                            backdrop = backdrop,
                            isDark = isDark,
                            themeAccent = themeAccent,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            isHidden = isHidden,
                            onPositioned = { coords -> cardCoords[anime.id] = coords },
                            onClick = {
                                val coords = cardCoords[anime.id]
                                val bounds = if (coords != null && rootCoords != null) {
                                    try { rootCoords!!.localBoundingBoxOf(coords, clipBounds = false) } catch (e: Exception) { Rect.Zero }
                                } else Rect.Zero
                                val initialWithEps = viewModel.getAnimeWithEpisodesSync(anime.id) ?: AnimeWithEpisodes(anime = anime, episodes = emptyList())
                                handleAnimeClick(initialWithEps, bounds)
                                if (initialWithEps.episodes.isEmpty()) {
                                    coroutineScope.launch {
                                        val withEpisodes = viewModel.getAnimeWithEpisodes(anime.id)
                                        if (withEpisodes != null && isAnimeExpanded) {
                                            displayAnime = withEpisodes
                                            selectedAnime = withEpisodes
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                selectedAnimeForAction = anime
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = topHeaderHeightDp + 6.dp,
                        bottom = 100.dp,
                        start = 14.dp,
                        end = 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(animes, key = { it.id }) { anime ->
                        val isHidden = (displayAnime?.anime?.id == anime.id && isAnimeTransitioning)
                        AnimeListCard(
                            anime = anime,
                            backdrop = backdrop,
                            isDark = isDark,
                            themeAccent = themeAccent,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            isHidden = isHidden,
                            onPositioned = { coords -> cardCoords[anime.id] = coords },
                            onClick = {
                                val coords = cardCoords[anime.id]
                                val bounds = if (coords != null && rootCoords != null) {
                                    try { rootCoords!!.localBoundingBoxOf(coords, clipBounds = false) } catch (e: Exception) { Rect.Zero }
                                } else Rect.Zero
                                val initialWithEps = viewModel.getAnimeWithEpisodesSync(anime.id) ?: AnimeWithEpisodes(anime = anime, episodes = emptyList())
                                handleAnimeClick(initialWithEps, bounds)
                                if (initialWithEps.episodes.isEmpty()) {
                                    coroutineScope.launch {
                                        val withEpisodes = viewModel.getAnimeWithEpisodes(anime.id)
                                        if (withEpisodes != null && isAnimeExpanded) {
                                            displayAnime = withEpisodes
                                            selectedAnime = withEpisodes
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                selectedAnimeForAction = anime
                            }
                        )
                    }
                }
            }
        }
    } else {
        if (isOnlineSearchActive) {
            com.example.epubreader.ui.hanime.HanimeSearchScreen(
                viewModel = hanimeViewModel,
                onBack = { isOnlineSearchActive = false },
                onVideoClick = { video ->
                    hanimeViewModel.openVideoDetail(video.videoCode)
                },
                backdrop = backdrop,
                isDark = isDark,
                themeAccent = themeAccent
            )
        } else {
            com.example.epubreader.ui.hanime.HanimeHomeScreen(
                viewModel = hanimeViewModel,
                onSearchClick = { isOnlineSearchActive = true },
                onVideoClick = { video ->
                    hanimeViewModel.openVideoDetail(video.videoCode)
                },
                onCategoryMoreClick = { category ->
                    hanimeViewModel.performSearch(genre = category, isLoadMore = false)
                    isOnlineSearchActive = true
                },
                onExit = {
                    isHanimeActive = false
                },
                backdrop = backdrop,
                isDark = isDark,
                themeAccent = themeAccent
            )
        }
    }

        var isSearchExpanded by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(isSearchExpanded) {
            if (isSearchExpanded) {
                kotlinx.coroutines.delay(160)
                focusRequester.requestFocus()
                keyboardController?.show()
            } else {
                keyboardController?.hide()
            }
        }

        // Layer 2: Floating Transparent Top Frosted Glass Header (Only shown for normal Anime library)
        if (!isHanimeActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { coords ->
                        topHeaderHeightDp = with(density) { coords.size.height.toDp() }
                    }
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar Title & Actions Row (Matching Reading Interface with real animeBackdrop refraction)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Title "番剧" (5-click secret entrance to Hanime, fades out when search expands)
                        AnimatedVisibility(
                            visible = !isSearchExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Text(
                                text = "番剧",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = primaryTextColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastTitleClickTime < 1500L) {
                                            titleClickCount++
                                            if (titleClickCount >= 5) {
                                                titleClickCount = 0
                                                isHanimeActive = true
                                                android.widget.Toast.makeText(context, "已开启 Hanime 在线模式", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            titleClickCount = 1
                                        }
                                        lastTitleClickTime = now
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            )
                        }

                        if (!isSearchExpanded) {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // 1. Search Expandable Capsule Button (Takes remaining left space when expanded)
                        LiquidButton(
                            onClick = {
                                if (!isSearchExpanded) {
                                    isSearchExpanded = true
                                }
                            },
                            backdrop = animeBackdrop,
                            shape = RoundedCornerShape(22.dp),
                            modifier = if (isSearchExpanded) {
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            } else {
                                Modifier.size(44.dp)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isSearchExpanded) 12.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isSearchExpanded) Arrangement.Start else Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "搜索",
                                    tint = if (isSearchExpanded) themeAccent else primaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )

                                if (isSearchExpanded) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.5.sp,
                                            color = primaryTextColor,
                                            fontFamily = ClaudeUIFontFamily
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "搜索番剧...",
                                                    fontSize = 13.sp,
                                                    color = secondaryTextColor.copy(alpha = 0.5f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    IconButton(
                                        onClick = {
                                            if (searchQuery.isNotEmpty()) {
                                                viewModel.setSearchQuery("")
                                            } else {
                                                isSearchExpanded = false
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Close",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Refresh / Scan Button (Circular reading-style LiquidButton with animeBackdrop)
                        LiquidButton(
                            onClick = {
                                if (webDavClient != null) {
                                    viewModel.scanWebDav(webDavClient)
                                } else {
                                    GlobalToastManager.show(
                                        "请先在「配置」中填写番剧 WebDAV 链接",
                                        ToastType.Info
                                    )
                                }
                            },
                            backdrop = animeBackdrop,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = themeAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新媒体库",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 3. Grid / List View Toggle Button
                        LiquidButton(
                            onClick = { isGridView = !isGridView },
                            backdrop = animeBackdrop,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = if (isGridView) "切换为列表视图" else "切换为网格视图",
                                tint = primaryTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 4. Sort Button (Circular reading-style LiquidButton with animeBackdrop)
                        LiquidButton(
                            onClick = { showSortMenu = true },
                            backdrop = animeBackdrop,
                            shape = CircleShape,
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

                // Sub-header Row: Horizontal Category Selector [全部 | 在看 | 已看完]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidSegmentedControl(
                        selectedIndex = filterStatus,
                        onOptionSelected = { viewModel.setFilterStatus(it) },
                        options = listOf("全部", "在看", "已看完"),
                        backdrop = animeBackdrop,
                        fontSize = 12.5.sp,
                        accentColor = themeAccent,
                        modifier = Modifier
                            .width(220.dp)
                            .height(36.dp)
                    )

                    Text(
                        text = "共 ${animes.size} 部",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTextColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // --- 1. Liquid Morphing Sort Menu Container (100% Bookshelf Exact Match) ---
        if (morphProgress > 0.001f || showSortMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morphProgress * 0.15f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSortMenu = false }
                    )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val fallbackBtnBounds = Rect(
                    screenWidthPx - with(density) { 60.dp.toPx() },
                    with(density) { 44.dp.toPx() },
                    screenWidthPx - with(density) { 16.dp.toPx() },
                    with(density) { 88.dp.toPx() }
                )
                val btnBounds = if (sortButtonBounds != Rect.Zero) sortButtonBounds else fallbackBtnBounds

                val sortOptions = remember { listOf("最近观看", "首播年份", "最高评分", "番剧名称", "最新入库") }
                val itemHeightDp = 36.dp
                val itemSpacingDp = 2.dp
                val menuWidthPx = with(density) { 156.dp.toPx() }
                val menuHeightPx = with(density) { 310.dp.toPx() }
                val dialogLeft = (btnBounds.right - menuWidthPx).coerceAtLeast(with(density) { 16.dp.toPx() })
                val dialogTop = btnBounds.bottom + with(density) { 6.dp.toPx() }
                val dialogBounds = Rect(dialogLeft, dialogTop, dialogLeft + menuWidthPx, dialogTop + menuHeightPx)

                val currentLeft = lerp(btnBounds.left, dialogBounds.left, morphProgress)
                val currentTop = lerp(btnBounds.top, dialogBounds.top, morphProgress)
                val currentWidth = lerp(btnBounds.width, menuWidthPx, morphProgress).coerceAtLeast(1f)
                val currentHeight = lerp(btnBounds.height, menuHeightPx, morphProgress).coerceAtLeast(1f)
                val currentCornerRadius = lerp(btnBounds.height / 2f, with(density) { 24.dp.toPx() }, morphProgress).coerceAtLeast(0f)

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
                            backdrop = animeBackdrop,
                            shape = { RoundedCornerShape(with(density) { currentCornerRadius.coerceAtLeast(0f).toDp() }) },
                            effects = {
                                vibrancy()
                                blur(lerp(3f, 8f, morphProgress).coerceAtLeast(0.1f).dp.toPx())
                                lens(
                                    refractionHeight = lerp(14f, 24f, morphProgress).coerceAtLeast(0.1f).dp.toPx(),
                                    refractionAmount = lerp(28f, 48f, morphProgress).coerceAtLeast(0.1f).dp.toPx(),
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
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (morphProgress < 0.5f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = (1f - (morphProgress / 0.35f)).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "排序", tint = primaryTextColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    if (morphProgress > 0.15f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer { alpha = ((morphProgress - 0.20f) / 0.80f).coerceIn(0f, 1f) }
                        ) {
                            val itemSlotHeightPx = with(density) { (itemHeightDp + itemSpacingDp).toPx() }

                            var isDraggingSlider by remember { mutableStateOf(false) }
                            var localDragProgress by remember { mutableFloatStateOf(sortMethod.toFloat()) }

                            LaunchedEffect(sortMethod, isDraggingSlider) {
                                if (!isDraggingSlider) localDragProgress = sortMethod.toFloat()
                            }

                            val animatedThumbProgress by animateFloatAsState(
                                targetValue = if (isDraggingSlider) localDragProgress else sortMethod.toFloat(),
                                animationSpec = if (isDraggingSlider) spring(dampingRatio = 0.90f, stiffness = 800f) else spring(dampingRatio = 0.50f, stiffness = 340f),
                                label = "animeSortThumb"
                            )

                            Column(modifier = Modifier.fillMaxSize()) {
                                // 1. Sort Options List
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    sortOptions.forEachIndexed { index, label ->
                                        val isSelected = sortMethod == index
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) themeAccent.copy(alpha = if (isDark) 0.28f else 0.18f)
                                                    else Color.Transparent
                                                )
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(
                                                            0.8.dp,
                                                            themeAccent.copy(alpha = 0.50f),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                    } else Modifier
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.setSortMethod(index)
                                                }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) themeAccent else (if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B))
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = themeAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.6.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Order Toggle Pill
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = if (isDark) 0.25f else 0.08f))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(true to "正序 ↓", false to "倒序 ↑").forEach { (asc, title) ->
                                        val isChosen = sortAscending == asc
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isChosen) themeAccent.copy(alpha = if (isDark) 0.35f else 0.22f)
                                                    else Color.Transparent
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { viewModel.setSortAscending(asc) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) themeAccent else secondaryTextColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Clear All Anime Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF453A).copy(alpha = 0.10f))
                                        .border(0.6.dp, Color(0xFFFF453A).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            showDeleteAllConfirmDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "清空番剧",
                                            tint = Color(0xFFFF453A),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "清空全部番剧",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFF453A)
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

        if (showDeleteAllConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirmDialog = false },
                title = { Text("清空番剧库", fontWeight = FontWeight.Bold, color = primaryTextColor) },
                text = { Text("确定要删除全部番剧与播放记录吗？此操作无法撤销。", color = secondaryTextColor) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllAnimes()
                            showDeleteAllConfirmDialog = false
                            showSortMenu = false
                        }
                    ) {
                        Text("确认清空", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirmDialog = false }) {
                        Text("取消", color = primaryTextColor)
                    }
                },
                containerColor = if (isDark) Color(0xFF1E293B) else Color.White
            )
        }

        // --- 2. Anime Series Detail Dialog Expansion (100% Bookshelf Series Exact Match) ---
        if (displayAnime != null && sourceBounds != Rect.Zero) {
            val allWithEpisodes by viewModel.animesWithEpisodes.collectAsState()
            val withEps = allWithEpisodes.firstOrNull { it.anime.id == displayAnime?.anime?.id } ?: displayAnime!!
            val anime = withEps.anime
            val episodes = withEps.episodes

            val seasons = remember(episodes) {
                val sList = episodes.map { it.seasonName }.distinct()
                val sorted = sList.sortedWith(
                    compareBy<String> { com.example.epubreader.data.anime.AnimeFilenameParser.getSeasonSortWeight(it) }
                        .thenBy { it }
                )
                if (sorted.isEmpty()) listOf("正片") else sorted
            }
            var selectedSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: "正片") }
            val currentSeasonEpisodes = remember(episodes, selectedSeason) {
                episodes.filter { it.seasonName == selectedSeason }
                    .sortedWith(
                        compareBy<com.example.epubreader.data.model.AnimeEpisodeEntity> { it.episodeIndex }
                            .thenBy { it.episodeNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
                            .thenBy { it.title }
                    )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()
                val progress = seriesExpandProgress
                val boundedProgress = progress.coerceIn(0f, 1f)
                val liftOffsetPx = kotlin.math.sin(boundedProgress * Math.PI.toFloat()) * with(density) { 24.dp.toPx() }
                val popScale = 1f + kotlin.math.sin(boundedProgress * Math.PI.toFloat()) * 0.025f

                val expandedWidthPx = minOf(screenWidthPx * 0.90f, with(density) { 460.dp.toPx() })
                val expandedHeightPx = minOf(screenHeightPx * 0.76f, with(density) { 580.dp.toPx() })
                val targetLeft = (screenWidthPx - expandedWidthPx) / 2f
                val targetTop = (screenHeightPx - expandedHeightPx) / 2f

                val currentLeft = lerp(sourceBounds.left, targetLeft, progress)
                val currentTop = lerp(sourceBounds.top, targetTop, progress) - liftOffsetPx
                val currentWidth = lerp(sourceBounds.width, expandedWidthPx, progress).coerceAtLeast(1f)
                val currentHeight = lerp(sourceBounds.height, expandedHeightPx, progress).coerceAtLeast(1f)

                // Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { if (isAnimeTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                        .graphicsLayer { alpha = (0.28f * boundedProgress).coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = 0.30f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            handleAnimeDismiss()
                        }
                )

                // Morphing Glass Container (Exact Settings Styling)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { if (isAnimeTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                        .graphicsLayer { alpha = if (isAnimeTransitioning) 1f else 0f }
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { currentWidth.toDp() },
                                height = with(density) { currentHeight.toDp() }
                            )
                            .offset {
                                IntOffset(currentLeft.roundToInt(), currentTop.roundToInt())
                            }
                            .graphicsLayer {
                                scaleX = popScale
                                scaleY = popScale
                                cameraDistance = 18f * density.density
                                val normalizedSourceY = ((sourceBounds.top - screenHeightPx / 2f) / screenHeightPx).coerceIn(-1f, 1f)
                                rotationX = normalizedSourceY * (1f - progress) * 4f
                            }
                            .drawBackdrop(
                                backdrop = animeBackdrop,
                                shape = { RoundedCornerShape(with(density) { lerp(if (isGridView) 18.dp.toPx() else 16.dp.toPx(), 28.dp.toPx(), progress).toDp() }) },
                                effects = {
                                    vibrancy()
                                    blur(androidx.compose.ui.util.lerp(4f, 12f, progress).dp.toPx())
                                    lens(
                                        refractionHeight = androidx.compose.ui.util.lerp(16f, 32f, progress).dp.toPx(),
                                        refractionAmount = androidx.compose.ui.util.lerp(32f, 64f, progress).dp.toPx(),
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight.Plain },
                                shadow = {
                                    Shadow(
                                        radius = lerp(12f, 36f, progress).dp,
                                        color = Color.Black.copy(alpha = if (isDark) lerp(0.25f, 0.45f, progress) else lerp(0.10f, 0.25f, progress))
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = if (isDark) lerp(0.07f, 0.08f, progress) else lerp(0.35f, 0.12f, progress)))
                                }
                            )
                            .border(
                                width = lerp(0.6f, 0.8f, progress).dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) lerp(0.25f, 0.40f, progress) else lerp(0.60f, 0.75f, progress)),
                                        Color.White.copy(alpha = if (isDark) lerp(0.05f, 0.08f, progress) else lerp(0.15f, 0.20f, progress))
                                    )
                                ),
                                shape = RoundedCornerShape(with(density) { lerp(if (isGridView) 18.dp.toPx() else 16.dp.toPx(), 28.dp.toPx(), progress).toDp() })
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { },
                        contentAlignment = Alignment.TopStart
                    ) {
                            // Collapsed Card fading out (Zero padding to match sourceBounds exactly)
                            Box(
                                modifier = Modifier
                                    .requiredSize(with(density) { sourceBounds.width.toDp() }, with(density) { sourceBounds.height.toDp() })
                                    .graphicsLayer {
                                        alpha = (1f - progress * 2.8f).coerceIn(0f, 1f)
                                    },
                                contentAlignment = Alignment.TopStart
                            ) {
                                AnimeCardContent(
                                    anime = anime,
                                    isGridView = isGridView,
                                    isDark = isDark,
                                    themeAccent = themeAccent,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }

                            // Expanded Dialog fading in (Matching user wireframe with upward drift)
                            if (seriesExpandProgress > 0.15f) {
                                val contentAlpha = ((progress - 0.18f) / 0.82f).coerceIn(0f, 1f)
                                val contentScale = lerp(0.94f, 1f, contentAlpha)
                                val contentSlideY = lerp(16f, 0f, contentAlpha)
                                val childBackdrop = backdrop
                                var isSummaryExpanded by remember { mutableStateOf(false) }

                                Column(
                                    modifier = Modifier
                                        .requiredSize(with(density) { expandedWidthPx.toDp() }, with(density) { expandedHeightPx.toDp() })
                                        .graphicsLayer {
                                            alpha = contentAlpha
                                            scaleX = contentScale
                                            scaleY = contentScale
                                            translationY = contentSlideY * density.density
                                        }
                                        .padding(18.dp)
                                ) {
                                    // 1. Top Section: Poster (Left) + Title & Summary (Right)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Poster Thumbnail (Left)
                                        Box(
                                            modifier = Modifier
                                                .width(96.dp)
                                                .aspectRatio(0.72f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.20f))
                                                .border(
                                                    0.8.dp,
                                                    Color.White.copy(alpha = if (isDark) 0.35f else 0.50f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                        ) {
                                            val coverFile = if (!anime.localCoverPath.isNullOrBlank()) File(anime.localCoverPath) else null
                                            if (coverFile?.exists() == true || !anime.coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(if (coverFile?.exists() == true) coverFile else anime.coverUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Cover",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Tv,
                                                        contentDescription = null,
                                                        tint = secondaryTextColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }

                                            if (anime.score > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(6.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.Black.copy(alpha = 0.70f))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "★ ${String.format("%.1f", anime.score)}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFFFD60A)
                                                    )
                                                }
                                            }
                                        }

                                        // Title, Info & Summary (Right)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = anime.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryTextColor,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // Close Button (✕)
                                                LiquidButton(
                                                    onClick = { handleAnimeDismiss() },
                                                    backdrop = childBackdrop,
                                                    surfaceColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.20f),
                                                    shape = CircleShape,
                                                    isCrystal = true,
                                                    themeAccent = themeAccent,
                                                    isDark = isDark,
                                                    modifier = Modifier
                                                        .padding(start = 8.dp)
                                                        .requiredSize(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Close",
                                                        tint = primaryTextColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Subtitle info (Year + Episode Count)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (anime.airDate?.isNotBlank() == true) {
                                                    Text("${anime.airDate.take(4)}年", fontSize = 12.sp, color = secondaryTextColor)
                                                }
                                                Text("全 ${episodes.size} 集", fontSize = 12.sp, color = secondaryTextColor)
                                                if (seasons.size > 1) {
                                                    Text("${seasons.size} 季全套", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = themeAccent)
                                                }
                                            }

                                            // Expandable Bangumi Summary
                                            if (!anime.summary.isNullOrBlank()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateContentSize()
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) { isSummaryExpanded = !isSummaryExpanded }
                                                ) {
                                                    Text(
                                                        text = anime.summary,
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 16.sp,
                                                        color = secondaryTextColor.copy(alpha = 0.90f),
                                                        maxLines = if (isSummaryExpanded) 8 else 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (anime.summary.length > 40) {
                                                        Text(
                                                            text = if (isSummaryExpanded) "收起 ▲" else "展开更多 ▼",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = themeAccent,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action Row: [Refresh Sync] and [Scrape Bangumi]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isRefreshingAnimeId by viewModel.isRefreshingSingleAnime.collectAsState()
                                        val isCurrentAnimeRefreshing = isRefreshingAnimeId == anime.id

                                        // 1. Refresh Sync Button (Series Inner Book Crystal Translucent Style)
                                        LiquidButton(
                                            onClick = {
                                                webDavClient?.let { client ->
                                                    viewModel.refreshSingleAnime(anime.id, client)
                                                } ?: run {
                                                    GlobalToastManager.show("WebDAV 未配置或未连接", ToastType.Error)
                                                }
                                            },
                                            backdrop = childBackdrop,
                                            isCrystal = true,
                                            themeAccent = themeAccent,
                                            isDark = isDark,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp),
                                            isInteractive = !isCurrentAnimeRefreshing
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (isCurrentAnimeRefreshing) {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.dp,
                                                        color = themeAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text("同步中...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeAccent)
                                                } else {
                                                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                                                    Text("刷新同步", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeAccent)
                                                }
                                            }
                                        }

                                        // 2. Re-Scrape Button (Series Inner Book Crystal Translucent Style)
                                        LiquidButton(
                                            onClick = {
                                                rematchKeyword = anime.title
                                                showRematchDialog = true
                                            },
                                            backdrop = childBackdrop,
                                            isCrystal = true,
                                            themeAccent = themeAccent,
                                            isDark = isDark,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Filled.Search, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                                                Text("重新刮削", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeAccent)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Dedicated Full-Width WebDAV Source Path Banner
                                    val fullCleanPath = remember(anime.webdavPath) {
                                        val raw = if (anime.webdavPath != "root" && anime.webdavPath.isNotBlank()) anime.webdavPath else "根目录"
                                        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                                        decoded.replace(Regex("^https?://[^/]+"), "").ifBlank { decoded }
                                    }
                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = if (isDark) 0.06f else 0.12f))
                                            .border(0.6.dp, Color.White.copy(alpha = if (isDark) 0.12f else 0.22f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullCleanPath))
                                                GlobalToastManager.show("路径已复制", ToastType.Success)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.FolderOpen,
                                                contentDescription = null,
                                                tint = themeAccent.copy(alpha = 0.9f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = fullCleanPath,
                                                fontSize = 11.5.sp,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = "复制",
                                            tint = secondaryTextColor.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(13.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // 2. Middle Section: Folders / Seasons Selector
                                    if (seasons.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            seasons.forEach { sName ->
                                                val isSelected = selectedSeason == sName
                                                val epCount = episodes.count { it.seasonName == sName }
                                                LiquidButton(
                                                    onClick = { selectedSeason = sName },
                                                    backdrop = childBackdrop,
                                                    isCrystal = true,
                                                    themeAccent = themeAccent,
                                                    isDark = isDark,
                                                    surfaceColor = if (isSelected) themeAccent.copy(alpha = if (isDark) 0.30f else 0.18f) else Color.Transparent,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text(
                                                        text = if (epCount > 0) "$sName ($epCount)" else sName,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) themeAccent else primaryTextColor
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    if (episodes.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.VideoLibrary,
                                                    contentDescription = null,
                                                    tint = primaryTextColor.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Text(
                                                    "暂未解析到剧集文件",
                                                    fontSize = 13.sp,
                                                    color = secondaryTextColor.copy(alpha = 0.8f)
                                                )
                                                LiquidButton(
                                                    onClick = {
                                                        webDavClient?.let { client ->
                                                            viewModel.refreshSingleAnime(anime.id, client)
                                                        }
                                                    },
                                                    backdrop = childBackdrop,
                                                    isCrystal = true,
                                                    themeAccent = themeAccent,
                                                    isDark = isDark,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("点击重新同步此番剧", fontSize = 12.sp, color = themeAccent, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    } else {
                                        // 3. Bottom Section: Pure Number Episode Grid (4-columns)
                                        val chunkedEpisodes = remember(currentSeasonEpisodes) { currentSeasonEpisodes.chunked(4) }
                                        val episodeListState = androidx.compose.foundation.lazy.rememberLazyListState()

                                        LazyColumn(
                                            state = episodeListState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = 6.dp)
                                        ) {
                                            items(chunkedEpisodes) { rowItems ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                rowItems.forEachIndexed { _, ep ->
                                                    val isCurrentPlaying = ep.id == anime.lastWatchEpisodeId
                                                    val numText = if (ep.episodeNumber.isNotBlank()) ep.episodeNumber else "${ep.episodeIndex + 1}"

                                                    LiquidButton(
                                                        onClick = {
                                                            handleAnimeDismiss()
                                                            onPlayEpisode(anime, ep)
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            inspectingEpisode = ep
                                                        },
                                                        backdrop = childBackdrop,
                                                        isCrystal = true,
                                                        themeAccent = themeAccent,
                                                        isDark = isDark,
                                                        surfaceColor = if (isCurrentPlaying) themeAccent.copy(alpha = if (isDark) 0.30f else 0.18f) else Color.Transparent,
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(1.25f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(
                                                                    text = numText,
                                                                    fontSize = if (numText.length > 4) 11.sp else if (numText.length > 2) 12.5.sp else 15.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = if (isCurrentPlaying) themeAccent else primaryTextColor,
                                                                    maxLines = 1
                                                                )
                                                                if (ep.resolution.isNotBlank()) {
                                                                    Text(
                                                                        text = ep.resolution,
                                                                        fontSize = 9.sp,
                                                                        color = if (isCurrentPlaying) themeAccent.copy(alpha = 0.8f) else secondaryTextColor.copy(alpha = 0.7f)
                                                                    )
                                                                }
                                                            }

                                                            if (ep.isWatched) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopEnd)
                                                                        .padding(5.dp)
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF10B981))
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                repeat(4 - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
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

        // --- 3. Long-Press Frosted Glass Context Modal ---
        selectedAnimeForAction?.let { anime ->
            val actionDialogBackdrop = rememberLayerBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { selectedAnimeForAction = null },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .drawBackdrop(
                            backdrop = animeBackdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    refractionHeight = 20.dp.toPx(),
                                    refractionAmount = 36.dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            shadow = {
                                Shadow(
                                    radius = 24.dp,
                                    color = Color.Black.copy(alpha = if (isDark) 0.40f else 0.20f)
                                )
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isDark) 0.10f else 0.16f))
                            },
                            exportedBackdrop = actionDialogBackdrop
                        )
                        .border(
                            0.8.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.65f),
                                    Color.White.copy(alpha = 0.20f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* prevent dismissal */ }
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header: Cover + Title + Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.25f))
                            ) {
                                val coverImage = anime.localCoverPath ?: anime.coverUrl
                                if (!coverImage.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(coverImage)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Tv,
                                        contentDescription = null,
                                        tint = secondaryTextColor.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = anime.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "共 ${anime.totalEpisodes} 集 · ${if (anime.seasonCount > 1) "${anime.seasonCount} 季全套" else "单季"}",
                                    fontSize = 11.5.sp,
                                    color = secondaryTextColor
                                )
                                val cleanActionPath = remember(anime.webdavPath) {
                                    val raw = if (anime.webdavPath.isNotBlank() && anime.webdavPath != "root") anime.webdavPath else "根目录"
                                    val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                                    decoded.replace(Regex("^https?://[^/]+"), "").ifBlank { decoded }
                                }
                                Text(
                                    text = "来源: $cleanActionPath",
                                    fontSize = 10.5.sp,
                                    color = secondaryTextColor.copy(alpha = 0.80f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = if (isDark) 0.12f else 0.25f),
                            thickness = 0.6.dp
                        )

                        // Action Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Single Anime Sync / Refresh
                            LiquidButton(
                                onClick = {
                                    val client = webDavClient
                                    val targetId = anime.id
                                    selectedAnimeForAction = null
                                    if (client != null) {
                                        viewModel.refreshSingleAnime(targetId, client)
                                    } else {
                                        GlobalToastManager.show("WebDAV 未连接", ToastType.Error)
                                    }
                                },
                                backdrop = actionDialogBackdrop,
                                surfaceColor = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                                    Text("单独刷新同步 (WebDAV)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                }
                            }

                            // 2. Re-Scrape Metadata
                            LiquidButton(
                                onClick = {
                                    rematchKeyword = anime.title
                                    showRematchDialog = true
                                    selectedAnimeForAction = null
                                },
                                backdrop = actionDialogBackdrop,
                                surfaceColor = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                                    Text("重新匹配元数据 (Bangumi/豆瓣)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                }
                            }

                            // 3. Remove from Database
                            LiquidButton(
                                onClick = {
                                    viewModel.deleteAnime(anime.id)
                                    selectedAnimeForAction = null
                                    GlobalToastManager.show("已从番剧库移除", ToastType.Success)
                                },
                                backdrop = actionDialogBackdrop,
                                surfaceColor = Color(0xFFFF453A).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                                    Text("从番剧库移除", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF453A))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Bangumi / Douban Rematch Dialog ---
        if (showRematchDialog) {
            val targetAnime = displayAnime?.anime ?: animes.find { it.title == rematchKeyword || it.id == selectedAnimeForAction?.id }
            val animeId = targetAnime?.id ?: 0
            AnimeRescrapeDialog(
                initialKeyword = rematchKeyword.ifBlank { targetAnime?.title ?: "" },
                backdrop = animeBackdrop,
                isDark = isDark,
                themeAccent = themeAccent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onDismiss = { showRematchDialog = false },
                onSelectCandidate = { candidate ->
                    if (animeId > 0) {
                        viewModel.applyScrapedMetadata(animeId, candidate)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(600L)
                            displayAnime = viewModel.getAnimeWithEpisodes(animeId)
                        }
                    }
                    showRematchDialog = false
                }
            )
        }
        // --- 5. Episode Long-Press File Details Dialog ---
        if (inspectingEpisode != null) {
            val ep = inspectingEpisode!!
            val fullUrl = ep.videoUrl
            val decodedUrl = try { android.net.Uri.decode(fullUrl) } catch (e: Exception) { fullUrl }
            val filename = decodedUrl.substringAfterLast("/")
            val folderPath = decodedUrl.substringBeforeLast("/")
            val ext = filename.substringAfterLast(".").uppercase()
            val sizeText = when {
                ep.fileSize > 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", ep.fileSize.toDouble() / (1024 * 1024 * 1024))
                ep.fileSize > 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", ep.fileSize.toDouble() / (1024 * 1024))
                else -> "${ep.fileSize} B"
            }

            EpisodeDetailDialog(
                episode = ep,
                filename = filename,
                folderPath = folderPath,
                ext = ext,
                sizeText = sizeText,
                fullUrl = fullUrl,
                themeAccent = themeAccent,
                onDismiss = { inspectingEpisode = null }
            )
        }

        // --- 6. Hanime Online Video Detail Sheet ---
        com.example.epubreader.ui.hanime.HanimeDetailSheet(
            viewModel = hanimeViewModel,
            onPlayClick = { video, res ->
                hanimeViewModel.startPlaying(video, res)
            },
            onTagClick = { tag ->
                hanimeViewModel.performSearch(tags = setOf(tag), isLoadMore = false)
                isOnlineSearchActive = true
                hanimeViewModel.closeVideoDetail()
            },
            onEpisodeClick = { vCode, idx ->
                hanimeViewModel.playPlaylistEpisode(vCode, idx)
            },
            onDismiss = {
                hanimeViewModel.closeVideoDetail()
            },
            backdrop = backdrop,
            isDark = isDark,
            themeAccent = themeAccent
        )

        // --- 7. Hanime Fullscreen Online Video Player Overlay ---
        hanimeViewModel.activePlayingVideo?.let { playingVideo ->
            com.example.epubreader.ui.hanime.HanimeOnlinePlayerOverlay(
                video = playingVideo,
                initialResolution = hanimeViewModel.activePlayingResolution,
                currentEpIndex = hanimeViewModel.currentEpisodeIndex,
                onExit = {
                    hanimeViewModel.exitPlaying()
                },
                onNextEpisode = {
                    hanimeViewModel.playNextEpisode()
                },
                onSelectEpisode = { vCode, idx ->
                    hanimeViewModel.playPlaylistEpisode(vCode, idx)
                },
                backdrop = backdrop,
                themeAccent = themeAccent
            )
        }
    }
}

// --- Anime Card Content (Used in Grid/List and Morphing Collapsed Transition) ---
@Composable
fun AnimeCardContent(
    anime: AnimeEntity,
    isGridView: Boolean,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    if (isGridView) {
        AnimeGridCardContent(
            anime = anime,
            isDark = isDark,
            themeAccent = themeAccent,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            AnimeListCardContent(
                anime = anime,
                isDark = isDark,
                themeAccent = themeAccent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )
        }
    }
}

@Composable
fun AnimeGridCardContent(
    anime: AnimeEntity,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            val coverFile = if (!anime.localCoverPath.isNullOrBlank()) File(anime.localCoverPath) else null
            if (coverFile?.exists() == true || !anime.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(if (coverFile?.exists() == true) coverFile else anime.coverUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .crossfade(100)
                        .build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.Tv,
                    contentDescription = null,
                    tint = themeAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(42.dp)
                )
            }

            if (anime.score > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", anime.score)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD60A)
                    )
                }
            }

            if (anime.isMultiSeason && anime.seasonCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeAccent.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${anime.seasonCount} 季",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = anime.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (anime.totalEpisodes > 0) {
                    Text(
                        text = "${anime.totalEpisodes} 集",
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                } else {
                    Text(
                        text = anime.airDate?.take(4) ?: "",
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                }

                val watchStatus = if (anime.isFinished) "已看完全部" else if (!anime.lastWatchEpisodeName.isNullOrBlank()) anime.lastWatchEpisodeName else "未观看"
                Text(
                    text = watchStatus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (anime.isFinished) Color(0xFF10B981) else if (!anime.lastWatchEpisodeName.isNullOrBlank()) themeAccent else secondaryTextColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Anime Grid Card ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeGridCard(
    anime: AnimeEntity,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isHidden: Boolean = false,
    onPositioned: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "gridPressScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { onPositioned(it) }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (isDark) 0.07f else 0.35f))
            .border(
                width = 0.6.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.25f else 0.60f),
                        Color.White.copy(alpha = if (isDark) 0.05f else 0.15f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AnimeGridCardContent(
            anime = anime,
            isDark = isDark,
            themeAccent = themeAccent,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor
        )
    }
}

@Composable
fun AnimeListCardContent(
    anime: AnimeEntity,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 86.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            val coverFile = if (!anime.localCoverPath.isNullOrBlank()) File(anime.localCoverPath) else null
            if (coverFile?.exists() == true || !anime.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(if (coverFile?.exists() == true) coverFile else anime.coverUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .crossfade(100)
                        .build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.Tv,
                    contentDescription = null,
                    tint = themeAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = anime.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!anime.originalTitle.isNullOrBlank() && anime.originalTitle != anime.title) {
                Text(
                    text = anime.originalTitle,
                    fontSize = 11.5.sp,
                    color = secondaryTextColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anime.score > 0f) {
                    Text(
                        text = "★ ${String.format("%.1f", anime.score)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500)
                    )
                }

                if (anime.isMultiSeason && anime.seasonCount > 1) {
                    Text(
                        text = "${anime.seasonCount} 季全套",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeAccent
                    )
                } else if (anime.totalEpisodes > 0) {
                    Text(
                        text = "${anime.totalEpisodes} 集全",
                        fontSize = 11.5.sp,
                        color = secondaryTextColor
                    )
                }

                val watchText = if (anime.isFinished) "已看完" else if (!anime.lastWatchEpisodeName.isNullOrBlank()) anime.lastWatchEpisodeName else "未观看"
                Text(
                    text = watchText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (anime.isFinished) Color(0xFF10B981) else if (!anime.lastWatchEpisodeName.isNullOrBlank()) themeAccent else secondaryTextColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// --- Anime List Card ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeListCard(
    anime: AnimeEntity,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isHidden: Boolean = false,
    onPositioned: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "listPressScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { onPositioned(it) }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isDark) 0.07f else 0.35f))
            .border(
                width = 0.6.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.25f else 0.60f),
                        Color.White.copy(alpha = if (isDark) 0.05f else 0.15f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        AnimeListCardContent(
            anime = anime,
            isDark = isDark,
            themeAccent = themeAccent,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor
        )
    }
}
