package com.example.epubreader.ui.stats

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.ui.components.liquid.LiquidGlassSegmentedTabs
import com.example.epubreader.ui.components.liquid.LiquidSegmentedControl
import com.example.epubreader.ui.theme.getThemeAccentColor
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    navController: NavController,
    settingsViewModel: com.example.epubreader.ui.settings.SettingsViewModel,
    globalBackdrop: com.kyant.backdrop.Backdrop,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val bookDao = db.bookDao()
    val statDao = db.statDao()
    val animeDao = db.animeDao()
    val animeStatDao = db.animeStatDao()
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModelFactory(bookDao, statDao, animeDao, animeStatDao))

    val appTheme by settingsViewModel.appTheme.collectAsState()
    val customColors by settingsViewModel.customColors.collectAsState()
    val isCustomThemeThreeColors by settingsViewModel.isCustomThemeThreeColors.collectAsState()

    val isDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF543866).copy(alpha = 0.85f)
    val themeAccent = getThemeAccentColor(appTheme, if (isCustomThemeThreeColors) customColors else customColors.take(2))

    val state by viewModel.stats.collectAsState()
    val animeState by viewModel.animeStats.collectAsState()

    var statsCategory by remember { mutableIntStateOf(0) } // 0: 📚 小说阅读, 1: 🎬 番剧观看

    // Smooth count-up & entrance animation driver triggered whenever user enters the Stats tab OR switches category
    val animTrigger = remember { Animatable(0f) }
    LaunchedEffect(isVisible, statsCategory) {
        if (isVisible) {
            animTrigger.snapTo(0f)
            animTrigger.animateTo(
                1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
    }

    val animFactor = animTrigger.value
    val animTotalMinutes = (state.totalReadingMinutes * animFactor).roundToInt()
    val animTodayMinutes = (state.todayReadingMinutes * animFactor).roundToInt()
    val animTotalBooks = (state.totalBooks * animFactor).roundToInt()
    val animTotalSeries = (state.totalSeries * animFactor).roundToInt()

    val animAnimeCount = (animeState.totalAnimes * animFactor).roundToInt()
    val animAnimeMinutes = (animeState.totalWatchMinutes * animFactor).roundToInt()
    val animAnimeTodayMinutes = (animeState.todayWatchMinutes * animFactor).roundToInt()
    val animAnimeEpisodes = (animeState.totalEpisodesWatched * animFactor).roundToInt()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp)
        ) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp))

            // Header Title
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "数据统计",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (statsCategory == 0) "记录每一次指尖沉浸的阅读时光" else "追踪每一部触动心弦的精彩番剧",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Novel vs Anime Segmented Switcher (Modern Liquid Glass Segmented Tabs)
                LiquidGlassSegmentedTabs(
                    selectedIndex = statsCategory,
                    onOptionSelected = { statsCategory = it },
                    options = listOf("小说", "番剧"),
                    backdrop = globalBackdrop,
                    fontSize = 13.sp,
                    themeAccent = themeAccent,
                    isDark = isDark
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (statsCategory == 0) {
                // ==================== NOVEL STATS ====================
                // 1. Reading Time Hero Highlights (2x2 Grid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "累计阅读",
                        value = "$animTotalMinutes",
                        unit = "分钟",
                        subtitle = if (animTotalMinutes >= 60) "${animTotalMinutes / 60}小时${animTotalMinutes % 60}分" else "累计阅读时长",
                        icon = Icons.Filled.Timer,
                        iconColor = themeAccent,
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "今日专注",
                        value = "$animTodayMinutes",
                        unit = "分钟",
                        subtitle = if (animTodayMinutes > 0) "保持好习惯" else "待开始阅读",
                        icon = Icons.Filled.LocalFireDepartment,
                        iconColor = Color(0xFFFF5722),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "藏书总数",
                        value = "$animTotalBooks",
                        unit = "本",
                        subtitle = "本地及云端",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconColor = Color(0xFF3B82F6),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "系列丛书",
                        value = "$animTotalSeries",
                        unit = "部",
                        subtitle = "收录系列",
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        iconColor = Color(0xFF8B5CF6),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Annual Reading Activity Heatmap (GitHub 风格 52 周年度阅读热力图)
                AnnualHeatmapCard(
                    heatmapWeeks = state.heatmapWeeks,
                    activeDaysCount = state.activeDaysCount,
                    currentStreakDays = state.currentStreakDays,
                    maxStreakDays = state.maxStreakDays,
                    backdrop = globalBackdrop,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark,
                    isVisible = isVisible,
                    animProgress = animFactor
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Weekly Reading Trend Line Chart (近 7 天阅读趋势)
                WeeklyTrendChartCard(
                    weeklyTrend = state.weeklyTrend,
                    backdrop = globalBackdrop,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark,
                    isVisible = isVisible,
                    animProgress = animFactor
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Reading Progress & Status Distribution (阅读完成度环形图)
                ProgressRingCard(
                    total = state.totalBooks,
                    finished = state.finishedBooks,
                    reading = state.readingBooks,
                    backdrop = globalBackdrop,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark,
                    isVisible = isVisible,
                    animProgress = animFactor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Recent Books Activity (最近阅读 & 最新导入)
                if (state.recentlyRead != null || state.recentlyAdded != null) {
                    Text(
                        text = "最近动态",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        state.recentlyRead?.let { book ->
                            item {
                                RecentBookLiquidCard(
                                    tag = "最近在读",
                                    tagColor = themeAccent,
                                    book = book,
                                    backdrop = globalBackdrop,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDark = isDark,
                                    onClick = { navController.navigate("reader/${book.id}") }
                                )
                            }
                        }
                        state.recentlyAdded?.let { book ->
                            if (book.id != state.recentlyRead?.id) {
                                item {
                                    RecentBookLiquidCard(
                                        tag = "最新导入",
                                        tagColor = Color(0xFF10B981),
                                        book = book,
                                        backdrop = globalBackdrop,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        isDark = isDark,
                                        onClick = { navController.navigate("reader/${book.id}") }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== ANIME STATS ====================
                // 1. Anime Time & Status Hero Highlights (2x2 Grid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "累计看番",
                        value = "$animAnimeMinutes",
                        unit = "分钟",
                        subtitle = if (animAnimeMinutes >= 60) "${animAnimeMinutes / 60}小时${animAnimeMinutes % 60}分" else "累计观看时长",
                        icon = Icons.Filled.Tv,
                        iconColor = themeAccent,
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "今日追番",
                        value = "$animAnimeTodayMinutes",
                        unit = "分钟",
                        subtitle = if (animAnimeTodayMinutes > 0) "今日沉浸中" else "今日待看番",
                        icon = Icons.Filled.PlayCircleFilled,
                        iconColor = Color(0xFFFF9500),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "追番总数",
                        value = "$animAnimeCount",
                        unit = "部",
                        subtitle = "在看 ${animeState.watchingAnimes} · 完结 ${animeState.finishedAnimes}",
                        icon = Icons.Filled.VideoLibrary,
                        iconColor = Color(0xFF3B82F6),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                    LiquidStatCard(
                        modifier = Modifier.weight(1f),
                        title = "已看集数",
                        value = "$animAnimeEpisodes",
                        unit = "集",
                        subtitle = "共 ${animeState.totalEpisodesCount} 集已录入",
                        icon = Icons.Filled.ConfirmationNumber,
                        iconColor = Color(0xFF8B5CF6),
                        backdrop = globalBackdrop,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Anime 7-Day Watch Trend
                AnimeWeeklyTrendChartCard(
                    weeklyTrend = animeState.weeklyTrend,
                    backdrop = globalBackdrop,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark,
                    isVisible = isVisible,
                    animProgress = animFactor
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Anime Progress Ring & Score Overview
                AnimeProgressRingCard(
                    total = animeState.totalAnimes,
                    watching = animeState.watchingAnimes,
                    finished = animeState.finishedAnimes,
                    unwatched = animeState.unwatchedAnimes,
                    averageScore = animeState.averageScore,
                    backdrop = globalBackdrop,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    isDark = isDark,
                    isVisible = isVisible,
                    animProgress = animFactor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Recent Anime Activity (最近观看 & 最新导入)
                if (animeState.recentlyWatched != null || animeState.recentlyAdded != null) {
                    Text(
                        text = "最近动态",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        animeState.recentlyWatched?.let { anime ->
                            item {
                                RecentAnimeLiquidCard(
                                    tag = "最近在追",
                                    tagColor = themeAccent,
                                    anime = anime,
                                    backdrop = globalBackdrop,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDark = isDark,
                                    onClick = { navController.navigate("anime") }
                                )
                            }
                        }
                        animeState.recentlyAdded?.let { anime ->
                            if (anime.id != animeState.recentlyWatched?.id) {
                                item {
                                    RecentAnimeLiquidCard(
                                        tag = "最新收录",
                                        tagColor = Color(0xFF10B981),
                                        anime = anime,
                                        backdrop = globalBackdrop,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        isDark = isDark,
                                        onClick = { navController.navigate("anime") }
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



@Composable
fun LiquidStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconColor: Color,
    backdrop: com.kyant.backdrop.Backdrop,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean
) {
    Box(
        modifier = modifier
            .height(130.dp)
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                iconColor.copy(alpha = if (isDark) 0.18f else 0.24f),
                                iconColor.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        iconColor.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        iconColor.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = subtitle ?: "",
                    fontSize = 11.sp,
                    color = secondaryTextColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AnimeWeeklyTrendChartCard(
    weeklyTrend: List<DayWatchingStat>,
    backdrop: com.kyant.backdrop.Backdrop,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    isVisible: Boolean = true,
    animProgress: Float = 1f
) {
    val maxMinutes = remember(weeklyTrend) {
        (weeklyTrend.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)
    }
    val weekTotal = remember(weeklyTrend) { weeklyTrend.sumOf { it.minutes } }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    val path = remember { Path() }
    val fillPath = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.16f else 0.22f),
                                themeAccent.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeAccent.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        themeAccent.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp, 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(themeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "近 7 天追番时长趋势",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Text(
                    text = "近7天合计 ${weekTotal} 分钟",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (weeklyTrend.isEmpty()) return@Canvas

                    val anim = animProgress.coerceIn(0f, 1f)
                    val width = size.width
                    val height = size.height
                    val stepX = width / (weeklyTrend.size - 1).coerceAtLeast(1)

                    val points = weeklyTrend.mapIndexed { index, stat ->
                        val ratio = (stat.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                        val animatedRatio = ratio * anim
                        val x = index * stepX
                        val y = height - (animatedRatio * (height - 24.dp.toPx())) - 12.dp.toPx()
                        Offset(x, y)
                    }

                    // Build smooth cubic bezier path
                    path.reset()
                    if (points.isNotEmpty()) {
                        path.moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    // Build area fill path
                    fillPath.reset()
                    fillPath.addPath(path)
                    fillPath.lineTo(points.last().x, height)
                    fillPath.lineTo(points.first().x, height)
                    fillPath.close()

                    // 1. Draw gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.35f else 0.25f),
                                themeAccent.copy(alpha = 0f)
                            )
                        )
                    )

                    // 2. Draw smooth stroke line
                    drawPath(
                        path = path,
                        color = themeAccent,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 3. Draw node points
                    points.forEachIndexed { idx, point ->
                        val stat = weeklyTrend[idx]
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = if (stat.minutes > 0) themeAccent else Color.Gray.copy(alpha = 0.5f),
                            radius = 3.dp.toPx(),
                            center = point
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Day Labels & Minutes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyTrend.forEach { stat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            text = if (stat.minutes > 0) "${stat.minutes}m" else "-",
                            fontSize = 11.sp,
                            fontWeight = if (stat.minutes > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (stat.minutes > 0) themeAccent else secondaryTextColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stat.dayLabel,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeProgressRingCard(
    total: Int,
    watching: Int,
    finished: Int,
    unwatched: Int,
    averageScore: Double,
    backdrop: com.kyant.backdrop.Backdrop,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    isVisible: Boolean = true,
    animProgress: Float = 1f
) {
    val progressRate = if (total > 0) finished.toFloat() / total.toFloat() else 0f

    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(isVisible, progressRate) {
        if (isVisible) {
            ringProgress.snapTo(0f)
            ringProgress.animateTo(
                progressRate,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 160f)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.16f else 0.22f),
                                themeAccent.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeAccent.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        themeAccent.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp, 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(themeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "追番状态分布与评分",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                if (averageScore > 0.0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "★ ${String.format(Locale.getDefault(), "%.1f", averageScore)} 均分",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Ring Chart
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)

                        // Background Track
                        drawArc(
                            color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Progress Arc
                        val currentSweep = 360f * ringProgress.value
                        if (currentSweep > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(themeAccent.copy(alpha = 0.6f), themeAccent, Color(0xFF10B981))
                                ),
                                startAngle = -90f,
                                sweepAngle = currentSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val percentage = (progressRate * 100).roundToInt()
                        Text(
                            text = "$percentage%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "完结率",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                // Legend Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimeLegendItem(
                        color = Color(0xFF3B82F6),
                        label = "正在追番",
                        count = "$watching 部",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    AnimeLegendItem(
                        color = Color(0xFF10B981),
                        label = "已看完结",
                        count = "$finished 部",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    AnimeLegendItem(
                        color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Gray.copy(alpha = 0.5f),
                        label = "待开播/未看",
                        count = "$unwatched 部",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeLegendItem(
    color: Color,
    label: String,
    count: String,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = secondaryTextColor,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = count,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor
        )
    }
}

@Composable
fun RecentAnimeLiquidCard(
    tag: String,
    tagColor: Color,
    anime: AnimeEntity,
    backdrop: com.kyant.backdrop.Backdrop,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(230.dp)
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tagColor.copy(alpha = if (isDark) 0.18f else 0.24f),
                                tagColor.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tagColor.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        tagColor.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tagColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tagColor
                    )
                }

                if (anime.score > 0.0) {
                    Text(
                        text = "★ ${String.format(Locale.getDefault(), "%.1f", anime.score)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val coverPath = anime.localCoverPath
            val coverUrl = anime.coverUrl
            val model = when {
                coverPath != null && java.io.File(coverPath).exists() -> java.io.File(coverPath)
                !coverUrl.isNullOrBlank() -> coverUrl
                else -> null
            }

            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Anime Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            0.6.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.15f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = anime.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (!anime.lastWatchEpisodeName.isNullOrBlank()) "看至 ${anime.lastWatchEpisodeName}" else "共 ${anime.totalEpisodes} 集",
                fontSize = 11.sp,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WeeklyTrendChartCard(
    weeklyTrend: List<DayReadingStat>,
    backdrop: com.kyant.backdrop.Backdrop,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    isVisible: Boolean = true,
    animProgress: Float = 1f
) {
    val maxMinutes = remember(weeklyTrend) {
        (weeklyTrend.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)
    }
    val weekTotal = remember(weeklyTrend) { weeklyTrend.sumOf { it.minutes } }
    
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    val path = remember { Path() }
    val fillPath = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.16f else 0.22f),
                                themeAccent.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeAccent.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        themeAccent.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp, 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(themeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "近 7 天阅读趋势",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Text(
                    text = "近7天合计 ${weekTotal} 分钟",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (weeklyTrend.isEmpty()) return@Canvas

                    val anim = animProgress.coerceIn(0f, 1f)
                    val width = size.width
                    val height = size.height
                    val stepX = width / (weeklyTrend.size - 1).coerceAtLeast(1)

                    val points = weeklyTrend.mapIndexed { index, stat ->
                        val ratio = (stat.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                        val animatedRatio = ratio * anim
                        val x = index * stepX
                        val y = height - (animatedRatio * (height - 24.dp.toPx())) - 12.dp.toPx()
                        Offset(x, y)
                    }

                    // Build smooth cubic bezier path
                    path.reset()
                    if (points.isNotEmpty()) {
                        path.moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    // Build area fill path
                    fillPath.reset()
                    fillPath.addPath(path)
                    fillPath.lineTo(points.last().x, height)
                    fillPath.lineTo(points.first().x, height)
                    fillPath.close()

                    // 1. Draw gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.35f else 0.25f),
                                themeAccent.copy(alpha = 0f)
                            )
                        )
                    )

                    // 2. Draw smooth stroke line
                    drawPath(
                        path = path,
                        color = themeAccent,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 3. Draw node points
                    points.forEachIndexed { idx, point ->
                        val stat = weeklyTrend[idx]
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = if (stat.minutes > 0) themeAccent else Color.Gray.copy(alpha = 0.5f),
                            radius = 3.dp.toPx(),
                            center = point
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Day Labels & Minutes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyTrend.forEach { stat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            text = if (stat.minutes > 0) "${stat.minutes}m" else "-",
                            fontSize = 11.sp,
                            fontWeight = if (stat.minutes > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (stat.minutes > 0) themeAccent else secondaryTextColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stat.dayLabel,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressRingCard(
    total: Int,
    finished: Int,
    reading: Int,
    backdrop: com.kyant.backdrop.Backdrop,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    isVisible: Boolean = true,
    animProgress: Float = 1f
) {
    val unread = (total - finished - reading).coerceAtLeast(0)
    val progressRate = if (total > 0) finished.toFloat() / total.toFloat() else 0f

    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(isVisible, progressRate) {
        if (isVisible) {
            ringProgress.snapTo(0f)
            ringProgress.animateTo(
                progressRate,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 160f)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.16f else 0.22f),
                                themeAccent.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeAccent.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        themeAccent.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp, 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(themeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "阅读完成度与状态",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Ring Chart
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)

                        // Background Track
                        drawArc(
                            color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Progress Arc
                        val currentSweep = 360f * ringProgress.value
                        if (currentSweep > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(themeAccent.copy(alpha = 0.6f), themeAccent, Color(0xFF10B981))
                                ),
                                startAngle = -90f,
                                sweepAngle = currentSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val percentage = (progressRate * 100).roundToInt()
                        Text(
                            text = "$percentage%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "完读率",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                // Legend Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimeLegendItem(
                        color = Color(0xFF3B82F6),
                        label = "正在阅读",
                        count = "$reading 本",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    AnimeLegendItem(
                        color = Color(0xFF10B981),
                        label = "已读完结",
                        count = "$finished 本",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    AnimeLegendItem(
                        color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Gray.copy(alpha = 0.5f),
                        label = "尚未阅读",
                        count = "$unread 本",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun RecentBookLiquidCard(
    tag: String,
    tagColor: Color,
    book: BookEntity,
    backdrop: com.kyant.backdrop.Backdrop,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(230.dp)
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tagColor.copy(alpha = if (isDark) 0.18f else 0.24f),
                                tagColor.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tagColor.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        tagColor.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tagColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val coverPath = book.coverImage
            if (coverPath != null && java.io.File(coverPath).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(coverPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            0.6.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.15f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "进度: ${(book.totalProgress * 100).roundToInt()}%",
                fontSize = 11.sp,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class HeatmapSelection(
    val day: HeatmapDay,
    val centerOffset: Offset
)

@Composable
fun AnnualHeatmapCard(
    heatmapWeeks: List<List<HeatmapDay>>,
    activeDaysCount: Int,
    currentStreakDays: Int,
    maxStreakDays: Int,
    backdrop: com.kyant.backdrop.Backdrop,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    isVisible: Boolean = true,
    animProgress: Float = 1f
) {
    val scrollState = rememberScrollState()

    // Automatically scroll to the right (latest current week) upon loading
    LaunchedEffect(heatmapWeeks.size, isVisible) {
        if (heatmapWeeks.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    var selectedDayInfo by remember { mutableStateOf<HeatmapSelection?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
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
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.16f else 0.22f),
                                themeAccent.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeAccent.copy(alpha = if (isDark) 0.50f else 0.70f),
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                        themeAccent.copy(alpha = if (isDark) 0.25f else 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp, 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(themeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "年度阅读热力图",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔥 ", fontSize = 11.sp)
                        Text(
                            text = "连续 ${currentStreakDays} 天",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    }
                    Text(text = "·", color = secondaryTextColor)
                    Text(
                        text = "活跃 ${activeDaysCount} 天",
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable 52-Week Matrix Canvas
            val density = LocalDensity.current
            val cellSizePx = with(density) { 13.dp.toPx() }
            val cellSpacingPx = with(density) { 3.5.dp.toPx() }
            val cornerRadiusPx = with(density) { 3.dp.toPx() }

            val totalCols = heatmapWeeks.size
            val canvasWidthDp = with(density) { ((cellSizePx + cellSpacingPx) * totalCols).toDp() }
            val canvasHeightDp = with(density) { ((cellSizePx + cellSpacingPx) * 7).toDp() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(canvasHeightDp)
                        .pointerInput(heatmapWeeks) {
                            detectTapGestures { tapOffset ->
                                val col = (tapOffset.x / (cellSizePx + cellSpacingPx)).toInt()
                                val row = (tapOffset.y / (cellSizePx + cellSpacingPx)).toInt()
                                if (col in heatmapWeeks.indices) {
                                    val week = heatmapWeeks[col]
                                    if (row in week.indices) {
                                        val day = week[row]
                                        val cellCenterX = col * (cellSizePx + cellSpacingPx) + cellSizePx / 2f
                                        val cellCenterY = row * (cellSizePx + cellSpacingPx) + cellSizePx / 2f
                                        selectedDayInfo = HeatmapSelection(day, Offset(cellCenterX, cellCenterY))
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        heatmapWeeks.forEachIndexed { colIdx, week ->
                            val x = colIdx * (cellSizePx + cellSpacingPx)
                            week.forEachIndexed { rowIdx, day ->
                                val y = rowIdx * (cellSizePx + cellSpacingPx)

                                val cellColor = when (day.level) {
                                    0 -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                    1 -> themeAccent.copy(alpha = 0.35f)
                                    2 -> themeAccent.copy(alpha = 0.60f)
                                    3 -> themeAccent.copy(alpha = 0.85f)
                                    else -> themeAccent
                                }

                                drawRoundRect(
                                    color = cellColor,
                                    topLeft = Offset(x, y),
                                    size = Size(cellSizePx, cellSizePx),
                                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                )

                                if (selectedDayInfo?.day?.dateTimestamp == day.dateTimestamp) {
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(x - 1.5f, y - 1.5f),
                                        size = Size(cellSizePx + 3f, cellSizePx + 3f),
                                        cornerRadius = CornerRadius(cornerRadiusPx + 1.5f, cornerRadiusPx + 1.5f),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    // Floating Tooltip for Selected Day
                    selectedDayInfo?.let { sel ->
                        val tooltipXDp = with(density) { (sel.centerOffset.x - 70.dp.toPx()).coerceAtLeast(0f).toDp() }
                        val tooltipYDp = with(density) {
                            if (sel.centerOffset.y > 60.dp.toPx()) {
                                (sel.centerOffset.y - 36.dp.toPx()).toDp()
                            } else {
                                (sel.centerOffset.y + 16.dp.toPx()).toDp()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = tooltipXDp, y = tooltipYDp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E2E).copy(alpha = 0.95f))
                                .border(
                                    width = 0.9.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.55f),
                                            themeAccent.copy(alpha = 0.70f),
                                            Color.White.copy(alpha = 0.20f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${sel.day.fullDateStr} (${sel.day.dayLabel})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                                Text(
                                    text = "·",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.40f)
                                )
                                Text(
                                    text = if (sel.day.minutes > 0) "${sel.day.minutes}分钟 🔥" else "未阅读 ☕",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (sel.day.minutes > 0) themeAccent else Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "近 52 周阅读记录",
                    fontSize = 11.sp,
                    color = secondaryTextColor
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = "少", fontSize = 10.sp, color = secondaryTextColor)
                    listOf(0, 1, 2, 3, 4).forEach { lvl ->
                        val c = when (lvl) {
                            0 -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            1 -> themeAccent.copy(alpha = 0.35f)
                            2 -> themeAccent.copy(alpha = 0.60f)
                            3 -> themeAccent.copy(alpha = 0.85f)
                            else -> themeAccent
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c)
                        )
                    }
                    Text(text = "多", fontSize = 10.sp, color = secondaryTextColor)
                }
            }
        }
    }
}
