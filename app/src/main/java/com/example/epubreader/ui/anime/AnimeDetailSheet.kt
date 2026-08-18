package com.example.epubreader.ui.anime

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.anime.ScrapedAnimeInfo
import com.example.epubreader.data.db.AnimeWithEpisodes
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType
import java.io.File

@Composable
fun AnimeDetailSheet(
    animeWithEpisodes: AnimeWithEpisodes,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onPlayEpisode: (episode: AnimeEpisodeEntity) -> Unit,
    onRematch: (animeId: Long, keyword: String) -> Unit = { _, _ -> },
    onRematchCandidate: ((animeId: Long, candidate: ScrapedAnimeInfo) -> Unit)? = null,
    onRefreshSingle: ((animeId: Long) -> Unit)? = null
) {
    val anime = animeWithEpisodes.anime
    val episodes = animeWithEpisodes.episodes

    val seasons = remember(episodes) {
        val sList = episodes.map { it.seasonName }.distinct()
        if (sList.isEmpty()) listOf("正片") else sList
    }
    var selectedSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: "正片") }

    val currentSeasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonName == selectedSeason }
    }

    var isGridView by remember { mutableStateOf(true) }
    var isSummaryExpanded by remember { mutableStateOf(false) }
    var showScrapeDialog by remember { mutableStateOf(false) }
    var inspectingEpisode by remember { mutableStateOf<AnimeEpisodeEntity?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.65f else 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Centered Glass Dialog Card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click inside dialog
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(26.dp) },
                    effects = {
                        vibrancy()
                        blur(24.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 48.dp.toPx(),
                            depthEffect = false,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Plain },
                    shadow = {
                        Shadow(
                            radius = 28.dp,
                            color = Color.Black.copy(alpha = if (isDark) 0.50f else 0.25f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isDark) Color(0xFF14171A).copy(alpha = 0.88f)
                            else Color(0xFF202327).copy(alpha = 0.90f)
                        )
                    }
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (Cover + Title + Meta + Rescrape)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Poster Cover with Ambient Shadow
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(138.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        ) {
                            val coverModel = anime.localCoverPath?.let { File(it) } ?: anime.coverUrl
                            if (coverModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(coverModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = anime.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Movie,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        // Right Info Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = anime.title,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Close",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (!anime.originalTitle.isNullOrBlank() && anime.originalTitle != anime.title) {
                                Text(
                                    text = anime.originalTitle ?: "",
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Tags / Meta row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (anime.score > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFFB800).copy(alpha = 0.25f))
                                            .border(0.6.dp, Color(0xFFFFB800).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "★ ${String.format(java.util.Locale.US, "%.1f", anime.score)}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD166)
                                        )
                                    }
                                }

                                if (anime.airDate?.isNotBlank() == true) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = anime.airDate ?: "",
                                            fontSize = 10.5.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(themeAccent.copy(alpha = 0.20f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${episodes.size} 集全",
                                            fontSize = 10.5.sp,
                                            color = themeAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                            }

                            // Summary / Synopsis Accordion
                            if (!anime.summary.isNullOrBlank()) {
                                Column(modifier = Modifier.padding(top = 2.dp)) {
                                    Text(
                                        text = anime.summary ?: "",
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp,
                                        color = Color.White.copy(alpha = 0.68f),
                                        maxLines = if (isSummaryExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if ((anime.summary?.length ?: 0) > 40) {
                                        Text(
                                            text = if (isSummaryExpanded) "收起 ▲" else "展开更多 ▼",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeAccent,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clickable { isSummaryExpanded = !isSummaryExpanded }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Row: [Refresh Sync] and [Scrape Bangumi]
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Refresh Sync Button
                        LiquidButton(
                            onClick = {
                                onRefreshSingle?.invoke(anime.id)
                            },
                            backdrop = backdrop,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                                Text("刷新同步", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // 2. Re-Scrape Button
                        LiquidButton(
                            onClick = {
                                showScrapeDialog = true
                            },
                            backdrop = backdrop,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                                Text("重新刮削", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Season Selector Tabs (if multi-season)
                if (seasons.size > 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "剧季与版本 (${seasons.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                seasons.forEach { sName ->
                                    val isSelected = sName == selectedSeason
                                    val seasonEpCount = episodes.count { it.seasonName == sName }
                                    LiquidButton(
                                        onClick = { selectedSeason = sName },
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(36.dp),
                                        tint = if (isSelected) themeAccent else Color.Unspecified
                                    ) {
                                        Text(
                                            text = "$sName ($seasonEpCount)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Episodes Header & Layout Switch
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$selectedSeason (${currentSeasonEpisodes.size} 集)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = "Toggle Layout",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Episodes Content (Grid vs List)
                if (isGridView) {
                    item {
                        val chunked = currentSeasonEpisodes.chunked(4)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunked.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { ep ->
                                        val isCurrentPlaying = ep.id == anime.lastWatchEpisodeId
                                        val numText = if (ep.episodeNumber.isNotBlank()) ep.episodeNumber else "${ep.episodeIndex + 1}"

                                        LiquidButton(
                                            onClick = { onPlayEpisode(ep) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                inspectingEpisode = ep
                                            },
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1.25f),
                                            tint = if (isCurrentPlaying) themeAccent else Color.Unspecified
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
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCurrentPlaying) Color.White else Color.White.copy(alpha = 0.95f),
                                                        maxLines = 1
                                                    )
                                                    if (ep.resolution.isNotBlank()) {
                                                        Text(
                                                            text = ep.resolution,
                                                            fontSize = 9.sp,
                                                            color = Color.White.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }

                                                if (ep.isWatched) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
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
                } else {
                    items(currentSeasonEpisodes, key = { it.id }) { ep ->
                        val isCurrentPlaying = ep.id == anime.lastWatchEpisodeId
                        LiquidButton(
                            onClick = { onPlayEpisode(ep) },
                            onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                inspectingEpisode = ep
                            },
                            backdrop = backdrop,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            tint = if (isCurrentPlaying) themeAccent else Color.Unspecified
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = ep.episodeNumber,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentPlaying) Color.White else Color.White.copy(alpha = 0.95f)
                                    )
                                    Text(
                                        text = ep.title,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (ep.resolution.isNotBlank()) {
                                        Text(ep.resolution, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f))
                                    }
                                    if (ep.isWatched) {
                                        Text("已看", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Episode Long-Press Detail Dialog
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

        // Rescrape Liquid Frosted Dialog with Multi-Candidate Selection
        if (showScrapeDialog) {
            AnimeRescrapeDialog(
                initialKeyword = anime.title,
                backdrop = backdrop,
                isDark = isDark,
                themeAccent = themeAccent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onDismiss = { showScrapeDialog = false },
                onSelectCandidate = { candidate ->
                    onRematchCandidate?.invoke(anime.id, candidate) ?: onRematch(anime.id, candidate.title)
                    showScrapeDialog = false
                }
            )
        }
    }
}

@Composable
fun EpisodeDetailDialog(
    episode: AnimeEpisodeEntity,
    filename: String,
    folderPath: String,
    ext: String,
    sizeText: String,
    fullUrl: String,
    themeAccent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = themeAccent, modifier = Modifier.size(22.dp))
                Text("单集文件全名与路径", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filename & format
                EpisodeDetailCard(label = "文件全名及格式", value = filename, tag = ext, copyText = filename)
                // Folder path
                EpisodeDetailCard(label = "所在文件夹路径", value = folderPath, copyText = folderPath)
                // Specifications
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (episode.resolution.isNotBlank()) {
                        EpisodeMiniSpecTag(label = "分辨率", value = episode.resolution, modifier = Modifier.weight(1f))
                    }
                    if (episode.videoCodec.isNotBlank()) {
                        EpisodeMiniSpecTag(label = "视频编码", value = episode.videoCodec, modifier = Modifier.weight(1f))
                    }
                    if (episode.fileSize > 0) {
                        EpisodeMiniSpecTag(label = "文件大小", value = sizeText, modifier = Modifier.weight(1f))
                    }
                }
                if (!episode.subtitleUrl.isNullOrBlank()) {
                    val subDecoded = try { android.net.Uri.decode(episode.subtitleUrl) } catch (e: Exception) { episode.subtitleUrl }
                    val subFilename = subDecoded.substringAfterLast("/")
                    EpisodeDetailCard(label = "匹配外挂字幕", value = subFilename, tag = subFilename.substringAfterLast(".").uppercase(), copyText = subDecoded)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Media URL", fullUrl)
                    clipboard.setPrimaryClip(clip)
                    GlobalToastManager.show("已复制完整媒体路径", ToastType.Success)
                    onDismiss()
                }
            ) {
                Text("复制完整路径", color = themeAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E2024),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun EpisodeDetailCard(
    label: String,
    value: String,
    tag: String? = null,
    copyText: String? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .clickable {
                if (copyText != null) {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(label, copyText)
                    clipboard.setPrimaryClip(clip)
                    GlobalToastManager.show("已复制 $label", ToastType.Success)
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
            if (tag != null && tag.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.25f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(tag, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                }
            }
        }
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun EpisodeMiniSpecTag(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.5f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}
