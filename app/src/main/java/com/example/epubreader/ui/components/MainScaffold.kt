package com.example.epubreader.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.BlendMode
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

import com.example.epubreader.ui.bookshelf.BookshelfScreen
import com.example.epubreader.ui.components.liquid.LiquidBottomTab
import com.example.epubreader.ui.components.liquid.LiquidBottomTabs
import com.example.epubreader.ui.settings.SettingsScreen
import com.example.epubreader.ui.settings.SettingsViewModel
import com.example.epubreader.ui.stats.StatsScreen
import com.example.epubreader.ui.theme.getThemeGradient
import com.example.epubreader.ui.theme.getThemeAccentColor
import com.example.epubreader.ui.theme.getThemeAccentGradient
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import androidx.compose.material.icons.filled.Tv
import com.example.epubreader.ui.anime.AnimeScreen
import com.example.epubreader.ui.anime.AnimeViewModel
import com.example.epubreader.ui.player.AnimePlayerScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Bookshelf : Screen("bookshelf", "书架", Icons.Filled.Book)
    object Anime : Screen("anime", "番剧", Icons.Filled.Tv)
    object Stats : Screen("stats", "统计", Icons.Filled.BarChart)
    object Settings : Screen("settings", "配置", Icons.Filled.Settings)
}

val LocalContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val bottomNavItems = listOf(
    Screen.Bookshelf,
    Screen.Anime,
    Screen.Stats,
    Screen.Settings
)

@Composable
fun MainScaffold(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val settingsViewModel: SettingsViewModel = viewModel()
    val animeViewModel: AnimeViewModel = viewModel()
    val hanimeViewModel: com.example.epubreader.ui.hanime.HanimeViewModel = viewModel()

    val appTheme by settingsViewModel.appTheme.collectAsState()
    val isCustomThemeThreeColors by settingsViewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by settingsViewModel.customColors.collectAsState()
    
    val currentCustomColors = if (isCustomThemeThreeColors) customColors else customColors.take(2)
    val themeGradient = getThemeGradient(
        theme = appTheme,
        customColors = currentCustomColors
    )
    val themeAccent = getThemeAccentColor(
        theme = appTheme,
        customColors = currentCustomColors
    )

    val isDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF2B173A)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Layer 1 backdrop (background gradient for pages inside NavHost)
    val backgroundBackdrop = rememberLayerBackdrop()

    // Layer 2 backdrop: OPAQUE backdrop capturing background + NavHost content
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(brush = themeGradient)
        drawContent()
    }

    var isReaderActive by remember { mutableStateOf(false) }
    val activePlayingPair = animeViewModel.activePlayingPair
    val isHanimePlaying = hanimeViewModel.activePlayingVideo != null

    LaunchedEffect(Unit) {
        settingsViewModel.checkDailyStatus()
    }

    val isReaderRoute = currentRoute?.startsWith("reader") == true
    val isNoveliaRoute = currentRoute == "novelia"

    var isBottomBarVisibleByScroll by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -8f) {
                    // Finger swiping up (scrolling down to read more) -> hide bottom bar
                    isBottomBarVisibleByScroll = false
                } else if (delta > 8f) {
                    // Finger swiping down (scrolling up towards top) -> show bottom bar
                    isBottomBarVisibleByScroll = true
                }
                return Offset.Zero
            }
        }
    }

    var selectedTabIndex by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedTabIndex, currentRoute) {
        isBottomBarVisibleByScroll = true
    }

    val showBottomBar = !isReaderActive && !isReaderRoute && !isNoveliaRoute && activePlayingPair == null && !isHanimePlaying && isBottomBarVisibleByScroll

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Background with pure harmonious theme gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = themeGradient)
                .layerBackdrop(backgroundBackdrop)
        )

        // Layer 2: Preloaded 4 Main Screens (Instantly loaded from app start)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .layerBackdrop(contentBackdrop)
        ) {
            // Tab 0: Bookshelf (Preloaded)
            val isBookshelfVisible = selectedTabIndex == 0 && !isReaderRoute && !isNoveliaRoute
            val bookshelfAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isBookshelfVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(150),
                label = "bookshelfAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = bookshelfAlpha
                        translationX = if (isBookshelfVisible) 0f else -10000f
                    }
            ) {
                BookshelfScreen(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    globalBackdrop = backgroundBackdrop,
                    onReaderActiveChanged = { isReaderActive = it }
                )
            }

            // Tab 1: Anime (Preloaded)
            val isAnimeVisible = selectedTabIndex == 1 && !isReaderRoute && !isNoveliaRoute
            val animeAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isAnimeVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(150),
                label = "animeAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = animeAlpha
                        translationX = if (isAnimeVisible) 0f else -10000f
                    }
            ) {
                val animeWebDavClient = settingsViewModel.getEffectiveAnimeWebDavClient()
                AnimeScreen(
                    viewModel = animeViewModel,
                    hanimeViewModel = hanimeViewModel,
                    backdrop = backgroundBackdrop,
                    themeGradient = themeGradient,
                    isDark = isDark,
                    themeAccent = themeAccent,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    webDavClient = animeWebDavClient,
                    onPlayEpisode = { anime, ep ->
                        animeViewModel.activePlayingPair = Pair(anime, ep)
                    }
                )
            }

            // Tab 2: Stats (Preloaded)
            val isStatsVisible = selectedTabIndex == 2 && !isReaderRoute && !isNoveliaRoute
            val statsAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isStatsVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(150),
                label = "statsAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = statsAlpha
                        translationX = if (isStatsVisible) 0f else -10000f
                    }
            ) {
                StatsScreen(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    globalBackdrop = backgroundBackdrop,
                    isVisible = isStatsVisible
                )
            }

            // Tab 3: Settings (Preloaded)
            val isSettingsVisible = selectedTabIndex == 3 && !isReaderRoute && !isNoveliaRoute
            val settingsAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSettingsVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(150),
                label = "settingsAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = settingsAlpha
                        translationX = if (isSettingsVisible) 0f else -10000f
                    }
            ) {
                SettingsScreen(
                    navController = navController,
                    viewModel = settingsViewModel,
                    backgroundBackdrop = backgroundBackdrop
                )
            }

            // NavHost for deep destinations like ReaderScreen
            NavHost(
                navController = navController,
                startDestination = "main_root",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
                popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) }
            ) {
                composable("main_root") {
                    // Pre-composed screens live underneath
                }
                composable(
                    route = "reader/{bookId}",
                    arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.LongType }),
                    enterTransition = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220))
                    },
                    exitTransition = {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                    },
                    popEnterTransition = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150))
                    },
                    popExitTransition = {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
                    }
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
                    com.example.epubreader.ui.reader.ReaderScreen(
                        navController = navController,
                        bookId = bookId,
                        settingsViewModel = settingsViewModel,
                        backgroundBackdrop = backgroundBackdrop
                    )
                }
                composable(
                    route = "novelia",
                    enterTransition = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220))
                    },
                    exitTransition = {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                    },
                    popEnterTransition = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150))
                    },
                    popExitTransition = {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
                    }
                ) {
                    com.example.epubreader.ui.novelia.NoveliaScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Active Full-Screen Anime Player Overlay
        activePlayingPair?.let { (anime, episode) ->
            val allEpisodes by animeViewModel.animes.collectAsState()
            var currentEpList by remember(anime.id) { mutableStateOf<List<com.example.epubreader.data.model.AnimeEpisodeEntity>>(emptyList()) }
            LaunchedEffect(anime.id) {
                currentEpList = animeViewModel.getAnimeWithEpisodes(anime.id)?.episodes ?: listOf(episode)
            }

            val animeWebDavUser by settingsViewModel.animeWebDavUser.collectAsState()
            val animeWebDavPass by settingsViewModel.animeWebDavPass.collectAsState()
            val webDavUser = animeWebDavUser.ifBlank { settingsViewModel.getSavedWebDavUser() }
            val webDavPass = if (animeWebDavPass.isNotBlank()) animeWebDavPass else settingsViewModel.getSavedWebDavPass()

            val themeAccentGradient = getThemeAccentGradient(
                theme = appTheme,
                customColors = currentCustomColors
            )

            AnimePlayerScreen(
                anime = anime,
                episode = episode,
                allEpisodes = currentEpList,
                backdrop = backgroundBackdrop,
                themeAccent = themeAccent,
                themeGradient = themeGradient,
                themeAccentGradient = themeAccentGradient,
                webDavAuth = if (webDavUser.isNotBlank()) Pair(webDavUser, webDavPass) else null,
                onProgressUpdate = { pos, dur ->
                    animeViewModel.updateWatchProgress(anime.id, episode.id, episode.title, pos, dur)
                },
                onExit = { pos, dur ->
                    animeViewModel.updateWatchProgress(anime.id, episode.id, episode.title, pos, dur)
                    animeViewModel.activePlayingPair = null
                },
                onNextEpisode = { nextEp ->
                    animeViewModel.updateWatchProgress(anime.id, episode.id, episode.title, episode.durationMs, episode.durationMs)
                    animeViewModel.activePlayingPair = Pair(anime, nextEp)
                }
            )
        }

        // Layer 3: Unified Glass Bottom bar - draws contentBackdrop (refracting book covers, text & background)
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomBar,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LiquidBottomTabs(
                selectedIndex = selectedTabIndex,
                onTabSelected = { index ->
                    selectedTabIndex = index
                },
                backdrop = contentBackdrop,
                tabsCount = bottomNavItems.size,
                accentColor = themeAccent,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                bottomNavItems.forEachIndexed { index, screen ->
                    val isDarkTheme = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
                    val defaultTabColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF2B173A)

                    LiquidBottomTab(
                        onClick = {
                            selectedTabIndex = index
                        }
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = defaultTabColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = screen.title,
                            color = defaultTabColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        )
                    }
                }
            }
        }

        // Layer 4: Global Floating Liquid Toast Notification Capsule
        val isDark = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
        com.example.epubreader.ui.components.toast.GlobalLiquidToast(
            backdrop = contentBackdrop,
            isDark = isDark,
            themeAccent = themeAccent,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Layer 5: Global Real-Time Performance & Diagnostics HUD
        val isPerfMonitorEnabled by settingsViewModel.isPerfMonitorEnabled.collectAsState()
        com.example.epubreader.ui.components.perf.GlobalPerformanceMonitorHud(
            isEnabled = isPerfMonitorEnabled,
            backdrop = contentBackdrop,
            isDark = isDark,
            themeAccent = themeAccent
        )
    }
}
