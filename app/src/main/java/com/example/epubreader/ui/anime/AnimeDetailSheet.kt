package com.example.epubreader.ui.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.example.epubreader.data.db.AnimeWithEpisodes
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailSheet(
    animeWithEpisodes: AnimeWithEpisodes,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onPlayEpisode: (episode: AnimeEpisodeEntity) -> Unit
) {
    val anime = animeWithEpisodes.anime
    val episodes = animeWithEpisodes.episodes

    val seasons = remember(episodes) {
        episodes.map { it.seasonName }.distinct()
    }
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull() ?: "正片") }

    val currentSeasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonName == selectedSeason }
    }

    var isSummaryExpanded by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    shadow = {
                        Shadow(
                            radius = 24.dp,
                            color = Color.Black.copy(alpha = if (isDark) 0.40f else 0.20f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.16f else 0.45f),
                                    Color.White.copy(alpha = if (isDark) 0.08f else 0.25f)
                                )
                            )
                        )
                    }
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.50f else 0.80f),
                            Color.White.copy(alpha = if (isDark) 0.15f else 0.35f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primaryTextColor.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // Header Banner Info
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Anime Poster
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(0.71f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        0.8.dp,
                                        Color.White.copy(alpha = 0.40f),
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
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = anime.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!anime.originalTitle.isNullOrBlank() && anime.originalTitle != anime.title) {
                                    Text(
                                        text = anime.originalTitle,
                                        fontSize = 12.5.sp,
                                        color = secondaryTextColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (anime.score > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFF9500).copy(alpha = 0.15f))
                                                .border(0.5.dp, Color(0xFFFF9500).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "★ ${String.format("%.1f", anime.score)}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF9500)
                                            )
                                        }
                                    }

                                    if (anime.airDate?.isNotBlank() == true) {
                                        Text(
                                            text = anime.airDate.take(4) + " 年",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    }

                                    Text(
                                        text = "全 ${episodes.size} 集",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Play Button
                                val firstUnwatched = episodes.firstOrNull { !it.isWatched } ?: episodes.firstOrNull()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(19.dp))
                                        .background(themeAccent)
                                        .clickable {
                                            if (firstUnwatched != null) onPlayEpisode(firstUnwatched)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (anime.lastWatchEpisodeName != null) "继续观看 ${anime.lastWatchEpisodeName}" else "立即播放第 1 集",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Summary
                        if (!anime.summary.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = if (isDark) 0.06f else 0.20f))
                                    .clickable { isSummaryExpanded = !isSummaryExpanded }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = anime.summary,
                                        fontSize = 12.5.sp,
                                        color = primaryTextColor.copy(alpha = 0.85f),
                                        maxLines = if (isSummaryExpanded) 100 else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isSummaryExpanded) "收起" else "展开详情 ▾",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeAccent
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Season Tabs (if multi-season)
                    if (seasons.size > 1) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                seasons.forEach { sName ->
                                    val isSelected = sName == selectedSeason
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) themeAccent.copy(alpha = 0.18f)
                                                else Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isSelected) themeAccent else Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedSeason = sName }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = sName,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) themeAccent else primaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episode List Section Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "剧集列表 (${currentSeasonEpisodes.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { isGridView = !isGridView },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Filled.List else Icons.Filled.GridView,
                                        contentDescription = "Toggle View",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isGridView) {
                        item {
                            val chunked = currentSeasonEpisodes.chunked(4)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                chunked.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { ep ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.25f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (ep.isWatched) Color.Black.copy(alpha = 0.20f)
                                                        else Color.White.copy(alpha = if (isDark) 0.10f else 0.35f)
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        if (ep.id == anime.lastWatchEpisodeId) themeAccent
                                                        else Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { onPlayEpisode(ep) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = ep.episodeNumber,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (ep.id == anime.lastWatchEpisodeId) themeAccent else primaryTextColor
                                                    )
                                                    if (ep.resolution.isNotBlank()) {
                                                        Text(
                                                            text = ep.resolution,
                                                            fontSize = 9.sp,
                                                            color = secondaryTextColor.copy(alpha = 0.7f)
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
                                        // Fill remaining slots in row if less than 4
                                        repeat(4 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Detailed List View
                        items(currentSeasonEpisodes) { ep ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (ep.id == anime.lastWatchEpisodeId) themeAccent.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = if (isDark) 0.08f else 0.30f)
                                    )
                                    .border(
                                        0.8.dp,
                                        if (ep.id == anime.lastWatchEpisodeId) themeAccent
                                        else Color.White.copy(alpha = if (isDark) 0.20f else 0.45f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onPlayEpisode(ep) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(if (ep.id == anime.lastWatchEpisodeId) themeAccent else Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ep.episodeNumber,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (ep.id == anime.lastWatchEpisodeId) Color.White else primaryTextColor
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = ep.title,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (ep.id == anime.lastWatchEpisodeId) themeAccent else primaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(text = "${ep.resolution} · ${ep.videoCodec}", fontSize = 11.sp, color = secondaryTextColor)
                                                if (ep.subtitleUrl != null) {
                                                    Text(text = "外挂字幕", fontSize = 11.sp, color = themeAccent)
                                                }
                                            }
                                        }
                                    }

                                    Icon(
                                        imageVector = if (ep.isWatched) Icons.Filled.CheckCircle else Icons.Filled.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = if (ep.isWatched) Color(0xFF10B981) else themeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}
