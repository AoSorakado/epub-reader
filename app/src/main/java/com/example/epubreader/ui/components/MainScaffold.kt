package com.example.epubreader.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Bookshelf : Screen("bookshelf", "书架", Icons.Filled.Book)
    object Stats : Screen("stats", "统计", Icons.Filled.BarChart)
    object Settings : Screen("settings", "配置", Icons.Filled.Settings)
}

val LocalContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val bottomNavItems = listOf(
    Screen.Bookshelf,
    Screen.Stats,
    Screen.Settings
)

@Composable
fun MainScaffold(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val settingsViewModel: SettingsViewModel = viewModel()
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

    // Layer 1 backdrop (background gradient for pages inside NavHost)
    val backgroundBackdrop = rememberLayerBackdrop()

    // Layer 2 backdrop: OPAQUE backdrop capturing background + NavHost content (books, text, covers)
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(brush = themeGradient)
        drawContent()
    }

    var isReaderActive by remember { mutableStateOf(false) }

    val hideBottomBarRoutes = listOf("reader/{bookId}")
    val showBottomBar = currentRoute != null && !isReaderActive && !hideBottomBarRoutes.any { currentRoute.startsWith(it.substringBefore("/")) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Background with pure harmonious theme gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = themeGradient)
                .layerBackdrop(backgroundBackdrop)
        )

        // Layer 2: Navigation content - captured by contentBackdrop with full background
        NavHost(
            navController = navController,
            startDestination = Screen.Bookshelf.route,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) }
        ) {
            composable(Screen.Bookshelf.route) { 
                BookshelfScreen(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    globalBackdrop = backgroundBackdrop,
                    onReaderActiveChanged = { isReaderActive = it }
                ) 
            }
            composable(Screen.Stats.route) { 
                StatsScreen(navController, settingsViewModel, backgroundBackdrop) 
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(navController, settingsViewModel, backgroundBackdrop) 
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
                com.example.epubreader.ui.reader.ReaderScreen(navController = navController, bookId = bookId, backgroundBackdrop = backgroundBackdrop)
            }
        }

        // Layer 3: Unified Glass Bottom bar - draws contentBackdrop (refracting book covers, text & background)
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomBar,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedIndex = bottomNavItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
            
            LiquidBottomTabs(
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    val screen = bottomNavItems[index]
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Bookshelf.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                bottomNavItems.forEachIndexed { _, screen ->
                    val isDarkTheme = appTheme == com.example.epubreader.ui.theme.AppTheme.MIDNIGHT_GLASS
                    val defaultTabColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF2B173A)

                    LiquidBottomTab(
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Bookshelf.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
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
    }
}
