package com.example.epubreader.ui.anime

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.db.AnimeWithEpisodes
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.network.WebDavClient
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    webDavClient: WebDavClient?,
    onPlayEpisode: (anime: AnimeEntity, episode: AnimeEpisodeEntity) -> Unit
) {
    val animes by viewModel.filteredAnimes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    var isGridView by remember { mutableStateOf(true) }
    var selectedAnimeForDetail by remember { mutableStateOf<AnimeWithEpisodes?>(null) }
    var selectedAnimeForAction by remember { mutableStateOf<AnimeEntity?>(null) }
    var showRematchDialog by remember { mutableStateOf(false) }
    var rematchKeyword by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Bar
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "番剧",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryTextColor
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh / Scan Button
                    IconButton(
                        onClick = {
                            if (webDavClient != null) {
                                viewModel.scanWebDav(webDavClient)
                            } else {
                                com.example.epubreader.ui.components.toast.GlobalToastManager.show("请先在「配置」中填写番剧 WebDAV 链接", com.example.epubreader.ui.components.toast.ToastType.Info)
                            }
                        },
                        enabled = !isScanning,
                        modifier = Modifier.size(38.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = themeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh Library",
                                tint = primaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Layout Switch Button
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle Layout",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.40f))
                    .border(
                        0.8.dp,
                        Color.White.copy(alpha = if (isDark) 0.20f else 0.60f),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = secondaryTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp,
                            color = primaryTextColor,
                            fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索番剧名称 / 原名...",
                                    fontSize = 13.5.sp,
                                    color = secondaryTextColor.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = secondaryTextColor, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (全部 / 在看 / 已看完)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("全部", "在看", "已看完").forEachIndexed { idx, label ->
                    val isSelected = filterStatus == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) themeAccent.copy(alpha = if (isDark) 0.25f else 0.18f)
                                else Color.White.copy(alpha = if (isDark) 0.06f else 0.25f)
                            )
                            .border(
                                0.6.dp,
                                if (isSelected) themeAccent else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.setFilterStatus(idx) }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) themeAccent else secondaryTextColor
                        )
                    }
                }
            }

            // Scanning Status Banner
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = scanProgress,
                        fontSize = 11.5.sp,
                        color = themeAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Anime Content List / Grid
            if (animes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Tv,
                            contentDescription = null,
                            tint = secondaryTextColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "未搜索到相关番剧" else "番剧库空空如也",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "请前往「设置 - 番剧 WebDAV」配置目录后点击同步",
                            fontSize = 12.sp,
                            color = secondaryTextColor.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(animes, key = { it.id }) { anime ->
                            AnimeGridCard(
                                anime = anime,
                                isDark = isDark,
                                themeAccent = themeAccent,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = {
                                    coroutineScope.launch {
                                        selectedAnimeForDetail = viewModel.getAnimeWithEpisodes(anime.id)
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(animes, key = { it.id }) { anime ->
                            AnimeListCard(
                                anime = anime,
                                isDark = isDark,
                                themeAccent = themeAccent,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = {
                                    coroutineScope.launch {
                                        selectedAnimeForDetail = viewModel.getAnimeWithEpisodes(anime.id)
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
        }

        // Anime Detail Sheet Modal
        selectedAnimeForDetail?.let { detail ->
            AnimeDetailSheet(
                animeWithEpisodes = detail,
                backdrop = backdrop,
                isDark = isDark,
                themeAccent = themeAccent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onDismiss = { selectedAnimeForDetail = null },
                onPlayEpisode = { ep ->
                    selectedAnimeForDetail = null
                    onPlayEpisode(detail.anime, ep)
                }
            )
        }

        // Long Press Action Sheet Modal
        selectedAnimeForAction?.let { anime ->
            ModalBottomSheet(
                onDismissRequest = { selectedAnimeForAction = null },
                containerColor = if (isDark) Color(0xFF1E1A29) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = anime.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("重新刮削 / 手动匹配 Bangumi") },
                        leadingContent = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = themeAccent) },
                        modifier = Modifier.clickable {
                            rematchKeyword = anime.title
                            showRematchDialog = true
                            selectedAnimeForAction = null
                        }
                    )

                    ListItem(
                        headlineContent = { Text("从番剧库移除") },
                        leadingContent = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFFF3B30)) },
                        modifier = Modifier.clickable {
                            viewModel.deleteAnime(anime.id)
                            selectedAnimeForAction = null
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Rematch Dialog
        if (showRematchDialog) {
            AlertDialog(
                onDismissRequest = { showRematchDialog = false },
                title = { Text("手动搜索 Bangumi") },
                text = {
                    Column {
                        Text("输入准确的中文或日文番剧名称进行重新刮削：", fontSize = 13.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rematchKeyword,
                            onValueChange = { rematchKeyword = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedAnimeForAction?.let { anime ->
                                viewModel.rematchBangumi(anime.id, rematchKeyword)
                            }
                            showRematchDialog = false
                        }
                    ) {
                        Text("搜索并匹配", color = themeAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRematchDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeGridCard(
    anime: AnimeEntity,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Poster Cover Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.71f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.30f))
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.40f else 0.70f),
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.20f)
                        )
                    ),
                    RoundedCornerShape(14.dp)
                )
        ) {
            val coverModel = if (!anime.localCoverPath.isNullOrBlank() && File(anime.localCoverPath).exists()) {
                File(anime.localCoverPath)
            } else anime.coverUrl

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverModel)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Score Badge (Top Right)
            if (anime.score > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.5.dp, Color(0xFFFF9500).copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", anime.score)}",
                        color = Color(0xFFFF9500),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Multi-season Badge (Top Left)
            if (anime.isMultiSeason && anime.seasonCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(themeAccent.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${anime.seasonCount} 季",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress Pill (Bottom Center)
            val watchText = if (anime.isFinished) "已看完" else if (anime.lastWatchEpisodeName != null) anime.lastWatchEpisodeName else "未观看"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = watchText ?: "未观看",
                    color = if (anime.isFinished) Color(0xFF10B981) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = anime.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeListCard(
    anime: AnimeEntity,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.35f))
            .border(
                0.8.dp,
                Color.White.copy(alpha = if (isDark) 0.20f else 0.50f),
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .aspectRatio(0.71f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
            ) {
                val coverModel = if (!anime.localCoverPath.isNullOrBlank() && File(anime.localCoverPath).exists()) {
                    File(anime.localCoverPath)
                } else anime.coverUrl

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                        fontSize = 12.sp,
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

                    val watchText = if (anime.isFinished) "已看完" else if (anime.lastWatchEpisodeName != null) anime.lastWatchEpisodeName else "未观看"
                    Text(
                        text = watchText ?: "未观看",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (anime.isFinished) Color(0xFF10B981) else if (anime.lastWatchEpisodeName != null) themeAccent else secondaryTextColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
