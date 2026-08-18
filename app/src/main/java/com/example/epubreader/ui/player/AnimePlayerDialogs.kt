package com.example.epubreader.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.components.liquid.LiquidSlider
import com.kyant.backdrop.Backdrop

@Composable
fun EpisodeSelectorDialogContent(
    allEpisodes: List<AnimeEpisodeEntity>,
    currentEpisodeId: Long,
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onEpisodeClick: (AnimeEpisodeEntity) -> Unit,
    onClose: () -> Unit
) {
    val seasons = remember(allEpisodes) {
        val sList = allEpisodes.map { it.seasonName }.distinct()
        val sorted = sList.sortedWith(
            compareBy<String> { com.example.epubreader.data.anime.AnimeFilenameParser.getSeasonSortWeight(it) }
                .thenBy { it }
        )
        if (sorted.isEmpty()) listOf("正片") else sorted
    }
    var selectedSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: "正片") }
    val seasonEpisodes = remember(allEpisodes, selectedSeason) {
        allEpisodes.filter { it.seasonName == selectedSeason }
            .sortedWith(
                compareBy<AnimeEpisodeEntity> { it.episodeIndex }
                    .thenBy { it.episodeNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
                    .thenBy { it.title }
            )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("选集", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("共 ${allEpisodes.size} 集", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f))
            }

            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Multi-Season Tabs
        if (seasons.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                seasons.forEach { sName ->
                    val isSeasonActive = sName == selectedSeason
                    LiquidButton(
                        onClick = { selectedSeason = sName },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = sName,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSeasonActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSeasonActive) themeAccent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        // Episode Grid
        val chunkedEpisodes = remember(seasonEpisodes) { seasonEpisodes.chunked(6) }
        val episodeListState = androidx.compose.foundation.lazy.rememberLazyListState()

        LazyColumn(
            state = episodeListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items(chunkedEpisodes) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowItems.forEach { ep ->
                        val isCurrentPlaying = ep.id == currentEpisodeId
                        val numText = if (ep.episodeNumber.isNotBlank()) ep.episodeNumber else "${ep.episodeIndex + 1}"

                        LiquidButton(
                            onClick = {
                                onEpisodeClick(ep)
                                onClose()
                            },
                            backdrop = playerBackdrop,
                            isCrystal = true,
                            themeAccent = themeAccent,
                            isDark = true,
                            surfaceColor = if (isCurrentPlaying) themeAccent.copy(alpha = 0.35f) else Color.Unspecified,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.15f)
                                .then(
                                    if (isCurrentPlaying) {
                                        Modifier.border(
                                            width = 1.2.dp,
                                            brush = Brush.linearGradient(
                                                listOf(
                                                    Color.White,
                                                    themeAccent.copy(alpha = 0.95f),
                                                    Color.White.copy(alpha = 0.60f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    } else Modifier
                                )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = numText,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrentPlaying) FontWeight.Black else FontWeight.Bold,
                                    color = if (isCurrentPlaying) Color.White else Color.White.copy(alpha = 0.75f)
                                )
                                if (isCurrentPlaying) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 2.5.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                    val emptySlots = 6 - rowItems.size
                    if (emptySlots > 0) {
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterSelectorDialogContent(
    availableChapters: List<com.example.epubreader.data.anime.SubtitleHelper.PlayerChapter>,
    currentPositionMs: Long,
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onChapterClick: (Long) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = themeAccent, modifier = Modifier.size(20.dp))
                Text("章节列表", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("共 ${availableChapters.size} 个章节", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f))
            }

            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (availableChapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("当前媒体源未包含章节标记", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(availableChapters) { chapter ->
                    val isActive = currentPositionMs >= chapter.startMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) themeAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                0.8.dp,
                                if (isActive) themeAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onChapterClick(chapter.startMs)
                                onClose()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chapter.title.ifBlank { "章节标记" },
                            fontSize = 12.5.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        val totalSec = chapter.startMs / 1000
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        Text(
                            text = String.format(java.util.Locale.US, "%02d:%02d", min, sec),
                            fontSize = 11.sp,
                            color = if (isActive) themeAccent else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

data class ExternalSubtitleItem(
    val name: String,
    val path: String,
    val folderName: String = "",
    val isSelected: Boolean = false
)

@Composable
fun TrackSelectorDialogContent(
    availableSubtitles: List<PlayerTrackInfo>,
    availableExternalSubtitles: List<ExternalSubtitleItem>,
    selectedExternalSubtitlePath: String?,
    isLoadingExternalSubs: Boolean,
    isSubtitleDisabled: Boolean,
    subtitleDelayMs: Long = 0L,
    onSubtitleDelayChange: (Long) -> Unit = {},
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onSelectExternalSubtitle: (ExternalSubtitleItem) -> Unit,
    onSelectInternalSubtitle: (PlayerTrackInfo) -> Unit,
    onDisableSubtitles: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.ClosedCaption, contentDescription = null, tint = themeAccent, modifier = Modifier.size(20.dp))
                Text("字幕轨与外挂字幕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Subtitle Delay Offset Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "字幕时间轴微调: ${String.format("%.2f", subtitleDelayMs / 1000f)}s",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidButton(
                    onClick = { onSubtitleDelayChange(subtitleDelayMs - 50L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("-0.05s", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
                LiquidButton(
                    onClick = { onSubtitleDelayChange(0L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("重置", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
                LiquidButton(
                    onClick = { onSubtitleDelayChange(subtitleDelayMs + 50L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("+0.05s", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        val isSubDisabled = isSubtitleDisabled && selectedExternalSubtitlePath == null

        // Close Subtitles Option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSubDisabled) themeAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        if (isSubDisabled) listOf(themeAccent.copy(alpha = 0.9f), Color.White.copy(alpha = 0.6f))
                        else listOf(Color.White.copy(alpha = 0.50f), Color(0xFFE0E7FF).copy(alpha = 0.30f), Color.White.copy(alpha = 0.15f))
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onDisableSubtitles() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("关闭字幕", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (isSubDisabled) Color.White else Color.White.copy(alpha = 0.85f))
            if (isSubDisabled) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // 1. Embedded Subtitles (MKV Internal Tracks)
        if (availableSubtitles.isNotEmpty()) {
            Text("内嵌字幕轨", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
            availableSubtitles.forEach { subTrack ->
                val isTrackActive = selectedExternalSubtitlePath == null && subTrack.isSelected && !isSubtitleDisabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isTrackActive) themeAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                if (isTrackActive) listOf(themeAccent.copy(alpha = 0.9f), Color.White.copy(alpha = 0.6f))
                                else listOf(Color.White.copy(alpha = 0.50f), Color(0xFFE0E7FF).copy(alpha = 0.30f), Color.White.copy(alpha = 0.15f))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectInternalSubtitle(subTrack) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subTrack.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isTrackActive) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "${subTrack.language.ifBlank { "未指定语言" }} · ${subTrack.mime.uppercase()}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    if (isTrackActive) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 2. External Subtitles (Directory & Subs Folders)
        if (availableExternalSubtitles.isNotEmpty()) {
            Text("📂 外挂字幕文件 (点击选择载入)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = themeAccent.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
            availableExternalSubtitles.forEach { extSub ->
                val isExtActive = selectedExternalSubtitlePath == extSub.path
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isExtActive) themeAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                if (isExtActive) listOf(themeAccent.copy(alpha = 0.9f), Color.White.copy(alpha = 0.6f))
                                else listOf(Color.White.copy(alpha = 0.50f), Color(0xFFE0E7FF).copy(alpha = 0.30f), Color.White.copy(alpha = 0.15f))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectExternalSubtitle(extSub) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = extSub.name,
                            fontSize = 12.sp,
                            fontWeight = if (isExtActive) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (extSub.folderName.isNotBlank() && extSub.folderName != "同级目录") {
                            Text(
                                text = "目录: ${extSub.folderName}",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    if (isExtActive) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else if (isLoadingExternalSubs) {
            Text("正在检索外挂字幕目录...", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))
        } else if (availableSubtitles.isEmpty()) {
            Text("未检测到内嵌字幕轨或外挂字幕文件", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun AudioSelectorDialogContent(
    availableAudioTracks: List<PlayerTrackInfo>,
    audioDelayMs: Long = 0L,
    onAudioDelayChange: (Long) -> Unit = {},
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onSelectAudioTrack: (PlayerTrackInfo) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = themeAccent, modifier = Modifier.size(20.dp))
                Text("音频流与音轨切换", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Audio Delay Offset Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "声音同步微调: ${String.format("%.2f", audioDelayMs / 1000f)}s",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidButton(
                    onClick = { onAudioDelayChange(audioDelayMs - 50L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("-0.05s", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
                LiquidButton(
                    onClick = { onAudioDelayChange(0L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("重置", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
                LiquidButton(
                    onClick = { onAudioDelayChange(audioDelayMs + 50L) },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("+0.05s", fontSize = 10.5.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        Text("可用音轨列表", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))

        if (availableAudioTracks.isEmpty()) {
            Text("默认主音轨 (立体声)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        } else {
            availableAudioTracks.forEach { audioTrack ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (audioTrack.isSelected) themeAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                if (audioTrack.isSelected) listOf(themeAccent.copy(alpha = 0.9f), Color.White.copy(alpha = 0.6f))
                                else listOf(Color.White.copy(alpha = 0.50f), Color(0xFFE0E7FF).copy(alpha = 0.30f), Color.White.copy(alpha = 0.15f))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectAudioTrack(audioTrack) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audioTrack.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (audioTrack.isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "${audioTrack.language.ifBlank { "主声道" }} · ${audioTrack.mime.uppercase()}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    if (audioTrack.isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DanmakuSettingsDialogContent(
    danmakuConfig: DanmakuConfig,
    currentDanmakuCount: Int,
    currentMatchedTitle: String,
    initialSearchKeyword: String = "",
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onConfigChange: (DanmakuConfig) -> Unit,
    onSelectEpisodeDanmaku: (episodeId: Long, animeTitle: String, episodeTitle: String) -> Unit = { _, _, _ -> },
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf(initialSearchKeyword) }
    var searchResults by remember { mutableStateOf<List<com.example.epubreader.data.anime.DandanAnimeResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val doSearch: () -> Unit = {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            hasSearched = true
            coroutineScope.launch {
                val results = com.example.epubreader.data.anime.DandanplayApiClient.searchAnimeEpisodes(searchQuery.trim())
                searchResults = results
                isSearching = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && !hasSearched && searchQuery.isNotBlank()) {
            doSearch()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabLabels = listOf("弹幕参数", "换源匹配 (${if (currentDanmakuCount > 0) "${currentDanmakuCount}条" else "无"})")
                tabLabels.forEachIndexed { index, title ->
                    val isActive = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isActive) themeAccent.copy(alpha = 0.85f) else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.70f)
                        )
                    }
                }
            }

            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        0.8.dp,
                        Brush.linearGradient(listOf(themeAccent.copy(alpha = 0.5f), Color.White.copy(alpha = 0.2f))),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentMatchedTitle.isNotBlank()) "当前源: $currentMatchedTitle" else "来源: 弹弹play官方数据库",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "已载入 $currentDanmakuCount 条弹幕",
                        fontSize = 11.5.sp,
                        color = themeAccent
                    )
                }

                LiquidButton(
                    onClick = { selectedTab = 1 },
                    backdrop = playerBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("换源", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            val areaLabel = when {
                danmakuConfig.displayAreaRatio <= 0.30f -> "1/4 屏 (25%)"
                danmakuConfig.displayAreaRatio <= 0.55f -> "半屏 (50%)"
                danmakuConfig.displayAreaRatio <= 0.80f -> "3/4 屏 (75%)"
                else -> "全屏 (100%)"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "显示区域: $areaLabel", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                LiquidSlider(
                    value = { danmakuConfig.displayAreaRatio },
                    onValueChange = { onConfigChange(danmakuConfig.copy(displayAreaRatio = it)) },
                    valueRange = 0.25f..1.0f,
                    visibilityThreshold = 0.05f,
                    backdrop = playerBackdrop,
                    accentColor = themeAccent,
                    modifier = Modifier.width(170.dp).height(28.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "不透明度: ${(danmakuConfig.opacity * 100).toInt()}%", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                LiquidSlider(
                    value = { danmakuConfig.opacity },
                    onValueChange = { onConfigChange(danmakuConfig.copy(opacity = it)) },
                    valueRange = 0.2f..1.0f,
                    visibilityThreshold = 0.05f,
                    backdrop = playerBackdrop,
                    accentColor = themeAccent,
                    modifier = Modifier.width(170.dp).height(28.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "字体大小: ${danmakuConfig.fontSizeSp.toInt()}sp", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                LiquidSlider(
                    value = { danmakuConfig.fontSizeSp },
                    onValueChange = { onConfigChange(danmakuConfig.copy(fontSizeSp = it)) },
                    valueRange = 12f..26f,
                    visibilityThreshold = 1f,
                    backdrop = playerBackdrop,
                    accentColor = themeAccent,
                    modifier = Modifier.width(170.dp).height(28.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "滚动速度: ${String.format("%.1f", danmakuConfig.speedFactor)}x", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                LiquidSlider(
                    value = { danmakuConfig.speedFactor },
                    onValueChange = { onConfigChange(danmakuConfig.copy(speedFactor = it)) },
                    valueRange = 0.5f..2.0f,
                    visibilityThreshold = 0.1f,
                    backdrop = playerBackdrop,
                    accentColor = themeAccent,
                    modifier = Modifier.width(170.dp).height(28.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "时间轴微调: ${String.format("%.1f", danmakuConfig.timeOffsetMs / 1000f)}s", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidButton(
                        onClick = { onConfigChange(danmakuConfig.copy(timeOffsetMs = danmakuConfig.timeOffsetMs - 500L)) },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("-0.5s", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                    LiquidButton(
                        onClick = { onConfigChange(danmakuConfig.copy(timeOffsetMs = danmakuConfig.timeOffsetMs + 500L)) },
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+0.5s", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
        } else {
            // Tab 1: 换源搜索
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索动画名称...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = themeAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
                ),
                trailingIcon = {
                    LiquidButton(
                        onClick = doSearch,
                        backdrop = playerBackdrop,
                        isCrystal = true,
                        themeAccent = themeAccent,
                        isDark = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(30.dp).padding(end = 4.dp)
                    ) {
                        Text("搜索", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            )

            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeAccent, modifier = Modifier.size(28.dp))
                }
            } else if (hasSearched && searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("未找到相关动画弹幕源", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
                }
            } else {
                searchResults.forEach { animeItem ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = animeItem.animeTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = animeItem.typeDescription,
                                fontSize = 10.5.sp,
                                color = themeAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(themeAccent.copy(alpha = 0.20f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            animeItem.episodes.forEach { ep ->
                                val isSelected = currentMatchedTitle.contains(ep.episodeTitle)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) themeAccent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.10f))
                                        .border(
                                            width = 0.8.dp,
                                            color = if (isSelected) themeAccent else Color.White.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            onSelectEpisodeDanmaku(ep.episodeId, animeItem.animeTitle, ep.episodeTitle)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = ep.episodeTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
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
fun QualitySpecsDialogContent(
    isHdr: Boolean,
    hdrType: String,
    resolutionStr: String,
    aspectRatioStr: String = "16:9",
    colorSpaceStr: String = "BT.709",
    bitDepthStr: String = "8-bit",
    frameRateStr: String = "23.976 fps",
    hwDecoderStr: String = "MediaCodec (GPU 硬解)",
    vCodecStr: String,
    aCodecStr: String,
    audioDetailsStr: String,
    bitrateStr: String,
    fileSizeStr: String,
    cacheReadaheadStr: String = "150 MB (60s 预读)",
    playerBackdrop: Backdrop? = null,
    themeAccent: Color,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isHdr) Icons.Filled.HdrOn else Icons.Filled.HighQuality,
                    contentDescription = null,
                    tint = if (isHdr) themeAccent else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text("画质与片源全规格档案", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            LiquidButton(
                onClick = onClose,
                backdrop = playerBackdrop,
                isCrystal = true,
                themeAccent = themeAccent,
                isDark = true,
                shape = CircleShape,
                modifier = Modifier.requiredSize(34.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // HDR & Gamut Status Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.40f), Color(0xFFE0E7FF).copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f))
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("色彩空间与动态范围", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$hdrType · $colorSpaceStr · $bitDepthStr",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                if (isHdr) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeAccent.copy(alpha = 0.25f))
                            .border(0.8.dp, themeAccent.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("HDR 生效中", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Specs Grid Items
        QualitySpecRow(icon = Icons.Filled.Tv, label = "视频分辨率与比例", value = "$resolutionStr ($aspectRatioStr)")
        QualitySpecRow(icon = Icons.Filled.SlowMotionVideo, label = "视频原生帧率", value = frameRateStr)
        QualitySpecRow(icon = Icons.Filled.Movie, label = "视频编码与解码器", value = "$vCodecStr · $hwDecoderStr")
        QualitySpecRow(icon = Icons.Filled.Speed, label = "实时传输码率", value = bitrateStr)
        QualitySpecRow(icon = Icons.Filled.Audiotrack, label = "主音频流编码", value = aCodecStr)
        if (audioDetailsStr.isNotBlank()) {
            QualitySpecRow(icon = Icons.Filled.GraphicEq, label = "音频声道与采样率", value = audioDetailsStr)
        }
        QualitySpecRow(icon = Icons.Filled.CloudDownload, label = "流媒体解复用缓冲", value = cacheReadaheadStr)
        QualitySpecRow(icon = Icons.Filled.Storage, label = "单集文件规格与大小", value = fileSizeStr)
    }
}

@Composable
fun QualitySpecRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(label, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
