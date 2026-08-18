package com.example.epubreader.ui.settings

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tv
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.ui.bookshelf.BookshelfViewModel
import com.example.epubreader.ui.bookshelf.BookshelfViewModelFactory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.example.epubreader.ui.components.liquid.LiquidToggle
import com.example.epubreader.ui.components.liquid.LiquidSlider
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.theme.AppTheme
import com.example.epubreader.ui.theme.getThemeGradient
import com.example.epubreader.ui.theme.getThemeAccentColor
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    backgroundBackdrop: Backdrop
) {
    val context = LocalContext.current

    var isWebDavExpanded by remember { mutableStateOf(false) }
    
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    var themeButtonBounds by remember { mutableStateOf(Rect.Zero) }
    val themeCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val webDavCoordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val syncState by viewModel.syncState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val autoNightMode by viewModel.autoNightMode.collectAsState()
    val immersiveStatusBar by viewModel.immersiveStatusBar.collectAsState()
    val pageTurnMode by viewModel.pageTurnMode.collectAsState()
    val pageAnimStyle by viewModel.pageAnimStyle.collectAsState()
    val isCustomThemeThreeColors by viewModel.isCustomThemeThreeColors.collectAsState()
    val customColors by viewModel.customColors.collectAsState()
    val isProgressSyncing by viewModel.isProgressSyncing.collectAsState()
    val lastProgressSyncTime by viewModel.lastProgressSyncTime.collectAsState()

    var isCustomThemeExpanded by remember { mutableStateOf(false) }
    var isThemeExpanded by remember { mutableStateOf(false) }

    val transition = updateTransition(targetState = isWebDavExpanded, label = "WebDavExpansion")
    val expandProgressState = transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.6f, stiffness = 250f) },
        label = "expandProgress"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    val isTransitioning = transition.currentState != transition.targetState || transition.currentState

    val themeTransition = updateTransition(targetState = isThemeExpanded, label = "ThemeExpansion")
    val themeExpandProgressState = themeTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.6f, stiffness = 250f) },
        label = "themeExpandProgress"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    val isThemeTransitioning = themeTransition.currentState != themeTransition.targetState || themeTransition.currentState

    val dao = remember { AppDatabase.getDatabase(context).bookDao() }
    val bookshelfViewModel: BookshelfViewModel = viewModel(factory = BookshelfViewModelFactory(dao, context.applicationContext as android.app.Application))
    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            bookshelfViewModel.importLocalBook(it, context)
        }
    }

    val isDark = appTheme == AppTheme.MIDNIGHT_GLASS
    val primaryTextColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF2B173A)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF543866).copy(alpha = 0.85f)

    val settingsGradient = getThemeGradient(appTheme, if (isCustomThemeThreeColors) customColors else customColors.take(2))
    val settingsBackdrop = rememberLayerBackdrop {
        drawRect(brush = settingsGradient)
        drawContent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoords = it }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = settingsGradient)
                .layerBackdrop(settingsBackdrop),
            topBar = {
                TopAppBar(
                    title = { Text("配置", color = primaryTextColor, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = primaryTextColor
                    )
                )
            },
            containerColor = Color.Transparent,
            contentColor = primaryTextColor
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val themeAccent = getThemeAccentColor(appTheme, if (isCustomThemeThreeColors) customColors else customColors.take(2))

                // THEME SELECTION SECTION
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("界面与外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .onGloballyPositioned { coords ->
                                themeCoordsRef[0] = coords
                            }
                            .graphicsLayer {
                                alpha = if (isThemeTransitioning) 0f else 1f
                            }
                            .drawBackdrop(
                                backdrop = backgroundBackdrop,
                                shape = { RoundedCornerShape(24.dp) },
                                effects = {
                                    vibrancy()
                                    blur(8f.dp.toPx())
                                    lens(14f.dp.toPx(), 28f.dp.toPx(), chromaticAberration = true)
                                },
                                highlight = { Highlight.Plain },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                if (!isThemeTransitioning) {
                                    rootCoords?.let { root ->
                                        themeCoordsRef[0]?.let { coords ->
                                            if (coords.isAttached) {
                                                themeButtonBounds = root.localBoundingBoxOf(coords, clipBounds = false)
                                            }
                                        }
                                    }
                                    isThemeExpanded = true
                                }
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CollapsedThemeButton(appTheme = appTheme, settingsGradient = settingsGradient, primaryTextColor = primaryTextColor, secondaryTextColor = secondaryTextColor)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动夜间模式", fontSize = 16.sp, color = primaryTextColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("19:00 - 07:00 自动切换至暗夜护眼主题", fontSize = 12.sp, color = secondaryTextColor)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        LiquidToggle(
                            selected = { autoNightMode },
                            onSelect = { viewModel.setAutoNightMode(it) },
                            backdrop = backgroundBackdrop,
                            accentColor = themeAccent
                        )
                    }

                    // Real-time Performance HUD Toggle
                    val isPerfMonitorEnabled by viewModel.isPerfMonitorEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("实时性能监控浮窗 (HUD)", fontSize = 16.sp, color = primaryTextColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("在各界面悬浮显示实时刷新率 (FPS)、CPU 占用、内存消耗与电池温度", fontSize = 12.sp, color = secondaryTextColor)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        LiquidToggle(
                            selected = { isPerfMonitorEnabled },
                            onSelect = { viewModel.setPerfMonitorEnabled(it) },
                            backdrop = backgroundBackdrop,
                            accentColor = themeAccent
                        )
                    }
                }

                // READER SETTINGS SECTION
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("阅读设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("沉浸状态栏", fontSize = 16.sp, color = primaryTextColor)
                        LiquidToggle(
                            selected = { immersiveStatusBar },
                            onSelect = { viewModel.setImmersiveStatusBar(it) },
                            backdrop = backgroundBackdrop,
                            accentColor = themeAccent
                        )
                    }

                    // Page Turn Mode Switcher
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("翻页模式", fontSize = 15.sp, color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidButton(
                                onClick = { viewModel.setPageTurnMode(0) },
                                backdrop = backgroundBackdrop,
                                surfaceColor = if (pageTurnMode == 0) themeAccent.copy(alpha = 0.35f) else Color.White.copy(0.08f),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    "上下连续滚动",
                                    fontSize = 14.sp,
                                    fontWeight = if (pageTurnMode == 0) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (pageTurnMode == 0) Color.White else primaryTextColor
                                )
                            }
                            LiquidButton(
                                onClick = { viewModel.setPageTurnMode(1) },
                                backdrop = backgroundBackdrop,
                                surfaceColor = if (pageTurnMode == 1) themeAccent.copy(alpha = 0.35f) else Color.White.copy(0.08f),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    "左右分页翻页",
                                    fontSize = 14.sp,
                                    fontWeight = if (pageTurnMode == 1) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (pageTurnMode == 1) Color.White else primaryTextColor
                                )
                            }
                        }
                    }

                    // Page Turn Animation Switcher (Only visible when pageTurnMode == 1)
                    AnimatedVisibility(
                        visible = pageTurnMode == 1,
                        enter = expandVertically(spring(0.78f, 320f)) + fadeIn(),
                        exit = shrinkVertically(spring(0.82f, 340f)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("翻页动画效果", fontSize = 15.sp, color = primaryTextColor, fontWeight = FontWeight.SemiBold)
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
                                animOptions.forEach { (styleIndex, label) ->
                                    val isSelected = pageAnimStyle == styleIndex
                                    LiquidButton(
                                        onClick = { viewModel.setPageAnimStyle(styleIndex) },
                                        backdrop = backgroundBackdrop,
                                        surfaceColor = if (isSelected) themeAccent.copy(alpha = 0.35f) else Color.White.copy(0.08f),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else primaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // BOOKSHELF MANAGEMENT SECTION
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("书架管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { localImportLauncher.launch(arrayOf("application/epub+zip")) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本地导入书籍", fontSize = 16.sp, color = primaryTextColor)
                        Icon(Icons.Filled.Folder, contentDescription = "Import", tint = getThemeAccentColor(appTheme))
                    }
                }

                // WEBDAV SYNC SECTION
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("云端同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .onGloballyPositioned { coords ->
                                webDavCoordsRef[0] = coords
                            }
                        .graphicsLayer {
                            // Completely hidden when morphing takes over
                            alpha = if (isTransitioning) 0f else 1f
                        }
                        .drawBackdrop(
                            backdrop = backgroundBackdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = {
                                vibrancy()
                                blur(3f.dp.toPx())
                                lens(
                                    refractionHeight = 14f.dp.toPx(),
                                    refractionAmount = 28f.dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.10f))
                            }
                        )
                        .clickable {
                            if (!isTransitioning) {
                                rootCoords?.let { root ->
                                    webDavCoordsRef[0]?.let { coords ->
                                        if (coords.isAttached) {
                                            buttonBounds = root.localBoundingBoxOf(coords, clipBounds = false)
                                        }
                                    }
                                }
                                isWebDavExpanded = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CollapsedWebDavButton(primaryTextColor = primaryTextColor)
                }

                // 2. Reading Progress Cloud Sync Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBackdrop(
                            backdrop = backgroundBackdrop,
                            shape = { RoundedCornerShape(22.dp) },
                            effects = {
                                vibrancy()
                                blur(3f.dp.toPx())
                                lens(8f.dp.toPx(), 16f.dp.toPx(), chromaticAberration = true)
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                            }
                        )
                        .border(
                            width = 0.6.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val syncRotation by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isProgressSyncing) 360f else 0f,
                                animationSpec = if (isProgressSyncing) {
                                    androidx.compose.animation.core.infiniteRepeatable(
                                        animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)
                                    )
                                } else {
                                    androidx.compose.animation.core.spring()
                                },
                                label = "syncRotation"
                            )

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(themeAccent.copy(alpha = if (isDark) 0.22f else 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Sync Progress",
                                    tint = themeAccent,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { rotationZ = syncRotation }
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "阅读进度云同步",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (lastProgressSyncTime > 0) {
                                        "上次同步: ${formatSyncTime(lastProgressSyncTime)} · 多端实时互通"
                                    } else {
                                        "同步至 WebDAV 云端，支持多设备跨端续读"
                                    },
                                    fontSize = 12.sp,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        LiquidButton(
                            onClick = { viewModel.syncReadingProgress() },
                            backdrop = backgroundBackdrop,
                            surfaceColor = themeAccent.copy(alpha = if (isDark) 0.30f else 0.18f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .defaultMinSize(minWidth = 72.dp)
                        ) {
                            Text(
                                text = if (isProgressSyncing) "同步中..." else "立即同步",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeAccent
                            )
                        }
                    }
                }

                // 3. Anime WebDAV Media Library Card
                val animeWebDavUrl by viewModel.animeWebDavUrl.collectAsState()
                val animeWebDavUser by viewModel.animeWebDavUser.collectAsState()
                val animeWebDavPass by viewModel.animeWebDavPass.collectAsState()

                var inputAnimeUrl by remember(animeWebDavUrl) { mutableStateOf(animeWebDavUrl) }
                var inputAnimeUser by remember(animeWebDavUser) { mutableStateOf(animeWebDavUser) }
                var inputAnimePass by remember(animeWebDavPass) { mutableStateOf(animeWebDavPass) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBackdrop(
                            backdrop = backgroundBackdrop,
                            shape = { RoundedCornerShape(22.dp) },
                            effects = {
                                vibrancy()
                                blur(3f.dp.toPx())
                                lens(8f.dp.toPx(), 16f.dp.toPx(), chromaticAberration = true)
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = if (isDark) 0.08f else 0.12f))
                            }
                        )
                        .border(
                            width = 0.6.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.35f else 0.70f),
                                    Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(themeAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null, tint = themeAccent, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("番剧 WebDAV 媒体库", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                Text("填入番剧媒体库链接、账号与密码", fontSize = 11.5.sp, color = secondaryTextColor)
                            }
                        }

                        OutlinedTextField(
                            value = inputAnimeUrl,
                            onValueChange = { inputAnimeUrl = it },
                            label = { Text("WebDAV 链接") },
                            placeholder = { Text("例如 http://192.168.1.100:5244/dav/4K fan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputAnimeUser,
                                onValueChange = { inputAnimeUser = it },
                                label = { Text("账号") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputAnimePass,
                                onValueChange = { inputAnimePass = it },
                                label = { Text("密码") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            LiquidButton(
                                onClick = {
                                    viewModel.saveAnimeWebDavConfig(
                                        url = inputAnimeUrl,
                                        user = inputAnimeUser,
                                        pass = inputAnimePass
                                    )
                                    com.example.epubreader.ui.components.toast.GlobalToastManager.show("✨ 番剧 WebDAV 配置已保存", com.example.epubreader.ui.components.toast.ToastType.Success)
                                },
                                backdrop = backgroundBackdrop,
                                surfaceColor = themeAccent.copy(alpha = if (isDark) 0.30f else 0.18f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("保存配置", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeAccent, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(140.dp))
            }
        }
        }

        // Overlay for Dim Background. Always in tree to avoid inflation stutter.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Move off-screen instantly when not transitioning to prevent touch interception
                .offset { if (isTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                .graphicsLayer { alpha = expandProgressState.value }
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) {
                    detectTapGestures { isWebDavExpanded = false }
                }
        )

        // Extreme GPU-Accelerated Fluid Liquid Morphing Component
        // ALWAYS in tree to pre-inflate heavy OutlinedTextFields. This completely eliminates frame 0 stutter!
        if (buttonBounds != Rect.Zero) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    // Snap off-screen when collapsed
                    .offset { if (isTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                    .graphicsLayer { alpha = if (isTransitioning) 1f else 0f }
            ) {
                val density = LocalDensity.current
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val expandedWidthPx = with(density) { (maxWidth - 48.dp).toPx() }
                val expandedHeightPx = with(density) { 460.dp.toPx() }
                val expandedLeft = (screenWidthPx - expandedWidthPx) / 2f
                val expandedTop = (screenHeightPx - expandedHeightPx) / 2f
                val expandedRect = Rect(expandedLeft, expandedTop, expandedLeft + expandedWidthPx, expandedTop + expandedHeightPx)

                // True Geometry Morphing container
                Box(
                    modifier = Modifier
                        // Morph Layout Size
                        .layout { measurable, c ->
                            val progress = expandProgressState.value
                            val currentWidth = lerp(buttonBounds.width, expandedWidthPx, progress).coerceAtLeast(0f)
                            val currentHeight = lerp(buttonBounds.height, expandedHeightPx, progress).coerceAtLeast(0f)
                            val placeable = measurable.measure(
                                Constraints.fixed(currentWidth.roundToInt(), currentHeight.roundToInt())
                            )
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        // Morph Layout Position
                        .offset {
                            val progress = expandProgressState.value
                            val currentLeft = lerp(buttonBounds.left, expandedRect.left, progress)
                            val currentTop = lerp(buttonBounds.top, expandedRect.top, progress)
                            IntOffset(currentLeft.roundToInt(), currentTop.roundToInt())
                        }
                        .drawBackdrop(
                            backdrop = settingsBackdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = {
                                val progress = expandProgressState.value
                                vibrancy()
                                blur(androidx.compose.ui.util.lerp(3f, 8f, progress).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, progress).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, progress).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.10f))
                            }
                        )
                        .pointerInput(Unit) { detectTapGestures {} },
                    contentAlignment = Alignment.Center
                ) {
                    val progress = expandProgressState.value

                    // Static Content Overlay: Collapsed Button (Fades out)
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { buttonBounds.width.toDp() },
                                height = with(density) { buttonBounds.height.toDp() }
                            )
                            .graphicsLayer {
                                // Fade out fast
                                alpha = (1f - progress * 4f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CollapsedWebDavButton(primaryTextColor = primaryTextColor)
                    }

                    // Static Content Overlay: Expanded Dialog (Fades in)
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { expandedWidthPx.toDp() },
                                height = with(density) { expandedHeightPx.toDp() }
                            )
                            .graphicsLayer {
                                val alphaValue = ((progress - 0.2f) * 1.25f).coerceIn(0f, 1f)
                                alpha = alphaValue
                                
                                val scale = lerp(0.9f, 1f, alphaValue)
                                scaleX = scale
                                scaleY = scale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ExpandedWebDavForm(
                            initialUrl = viewModel.getSavedWebDavUrl(),
                            initialUser = viewModel.getSavedWebDavUser(),
                            initialPass = viewModel.getSavedWebDavPass(),
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onDismiss = { isWebDavExpanded = false },
                            onConnect = { url, user, pass ->
                                isWebDavExpanded = false
                                viewModel.syncWebDav(url, user, pass)
                            }
                        )
                    }
                }
            }
        }

        // Dim Background for Theme Morphing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { if (isThemeTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                .graphicsLayer { alpha = themeExpandProgressState.value }
                .background(Color.Black.copy(alpha = 0.18f))
                .pointerInput(Unit) {
                    detectTapGestures { isThemeExpanded = false }
                }
        )

        // Fluid Liquid Morphing Theme Picker Component
        if (themeButtonBounds != Rect.Zero) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { if (isThemeTransitioning) IntOffset.Zero else IntOffset(100000, 0) }
                    .graphicsLayer { alpha = if (isThemeTransitioning) 1f else 0f }
            ) {
                val progress = themeExpandProgressState.value
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()
                val density = LocalDensity.current

                val expandedWidthPx = (screenWidthPx - with(density) { 32.dp.toPx() }).coerceAtLeast(1f)
                val expandedHeightPx = with(density) { 268.dp.toPx() }

                val expandedRect = remember(screenWidthPx, screenHeightPx, expandedWidthPx, expandedHeightPx, themeButtonBounds) {
                    val left = (screenWidthPx - expandedWidthPx) / 2f
                    val top = (themeButtonBounds.top - with(density) { 30.dp.toPx() }).coerceIn(
                        with(density) { 70.dp.toPx() },
                        screenHeightPx - expandedHeightPx - with(density) { 70.dp.toPx() }
                    )
                    Rect(left, top, left + expandedWidthPx, top + expandedHeightPx)
                }

                Box(
                    modifier = Modifier
                        .requiredSize(
                            width = with(density) {
                                lerp(themeButtonBounds.width, expandedWidthPx, progress).toDp()
                            },
                            height = with(density) {
                                lerp(themeButtonBounds.height, expandedHeightPx, progress).toDp()
                            }
                        )
                        .offset {
                            val currentLeft = lerp(themeButtonBounds.left, expandedRect.left, progress)
                            val currentTop = lerp(themeButtonBounds.top, expandedRect.top, progress)
                            IntOffset(currentLeft.roundToInt(), currentTop.roundToInt())
                        }
                        .drawBackdrop(
                            backdrop = settingsBackdrop,
                            shape = { RoundedCornerShape(with(density) { lerp(24.dp.toPx(), 28.dp.toPx(), progress).toDp() }) },
                            effects = {
                                vibrancy()
                                blur(androidx.compose.ui.util.lerp(3f, 8f, progress).dp.toPx())
                                lens(
                                    refractionHeight = androidx.compose.ui.util.lerp(14f, 24f, progress).dp.toPx(),
                                    refractionAmount = androidx.compose.ui.util.lerp(28f, 48f, progress).dp.toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.10f))
                            }
                        )
                        .pointerInput(Unit) { detectTapGestures {} },
                    contentAlignment = Alignment.Center
                ) {
                    // Collapsed Button (Fades out)
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { themeButtonBounds.width.toDp() },
                                height = with(density) { themeButtonBounds.height.toDp() }
                            )
                            .graphicsLayer {
                                alpha = (1f - progress * 3f).coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CollapsedThemeButton(appTheme = appTheme, settingsGradient = settingsGradient, primaryTextColor = primaryTextColor, secondaryTextColor = secondaryTextColor)
                    }

                    // Expanded Dialog (Fades in)
                    Box(
                        modifier = Modifier
                            .requiredSize(
                                width = with(density) { expandedWidthPx.toDp() },
                                height = with(density) { expandedHeightPx.toDp() }
                            )
                            .graphicsLayer {
                                val alphaValue = ((progress - 0.2f) * 1.25f).coerceIn(0f, 1f)
                                alpha = alphaValue
                                
                                val scale = lerp(0.92f, 1f, alphaValue)
                                scaleX = scale
                                scaleY = scale
                            }
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpandedThemePickerContent(
                            appTheme = appTheme,
                            customColors = customColors,
                            isCustomThemeThreeColors = isCustomThemeThreeColors,
                            isDark = isDark,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onSelectTheme = { viewModel.setAppTheme(it) },
                            onDismiss = { isThemeExpanded = false }
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun CollapsedWebDavButton(primaryTextColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Cloud, contentDescription = "WebDAV", tint = primaryTextColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text("连接 WebDAV 服务器", fontSize = 16.sp, color = primaryTextColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ExpandedWebDavForm(
    initialUrl: String,
    initialUser: String,
    initialPass: String,
    isDark: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onConnect: (String, String, String) -> Unit
) {
    var url by remember { mutableStateOf(if (initialUrl.isEmpty()) "http://" else initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor       = primaryTextColor,
        unfocusedTextColor     = primaryTextColor,
        focusedLabelColor      = primaryTextColor,
        unfocusedLabelColor    = secondaryTextColor,
        focusedBorderColor     = primaryTextColor.copy(alpha = 0.8f),
        unfocusedBorderColor   = secondaryTextColor.copy(alpha = 0.4f),
        cursorColor            = primaryTextColor,
        focusedContainerColor  = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.20f),
        unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)
    )

    CompositionLocalProvider(
        LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "WebDAV 配置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL", fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                    fontSize = 15.sp,
                    color = primaryTextColor
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username", fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                    fontSize = 15.sp,
                    color = primaryTextColor
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password", fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                    fontSize = 15.sp,
                    color = primaryTextColor
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons — iOS-style: hairline divider + text-only
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(secondaryTextColor.copy(alpha = 0.25f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDismiss() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", color = secondaryTextColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(0.6.dp)
                    .background(secondaryTextColor.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onConnect(url, user, pass) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("同步书库", color = if (isDark) Color(0xFF38BDF8) else Color(0xFF9C27B0), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable
fun CollapsedThemeButton(
    appTheme: AppTheme,
    settingsGradient: androidx.compose.ui.graphics.Brush,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(brush = settingsGradient)
                    .border(1.2.dp, Color.White.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                "全局主题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = appTheme.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Open",
                tint = secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = -90f }
            )
        }
    }
}

@Composable
fun ExpandedThemePickerContent(
    appTheme: AppTheme,
    customColors: List<Color>,
    isCustomThemeThreeColors: Boolean,
    isDark: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onSelectTheme: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Dialog Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "全局主题",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "选择应用的主题氛围色彩",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = primaryTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Theme Grid (4 columns x 2 rows)
        val availableThemes = AppTheme.values().filter { it != AppTheme.CUSTOM }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            availableThemes.chunked(4).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowThemes.forEach { theme ->
                        val isSelected = theme == appTheme
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "themeScale"
                        )

                        Column(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { 
                                    onSelectTheme(theme)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        getThemeGradient(
                                            theme,
                                            if (isCustomThemeThreeColors) customColors else customColors.take(2)
                                        )
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = if (isDark) Color.White else Color(0xFF2B173A),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 0.6.dp,
                                                color = Color.White.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color.White.copy(alpha = 0.65f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = if (isDark) Color(0xFF0F172A) else Color(0xFF2B173A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = theme.shortTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) primaryTextColor else secondaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    if (timestamp <= 0) return "未同步"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60_000) return "刚刚"
    if (diff < 3600_000) return "${diff / 60_000} 分钟前"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

