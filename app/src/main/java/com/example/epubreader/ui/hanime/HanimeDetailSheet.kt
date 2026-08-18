package com.example.epubreader.ui.hanime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.epubreader.data.hanime.HanimeComment
import com.example.epubreader.data.hanime.HanimeInfo
import com.example.epubreader.data.hanime.HanimeVideo
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.hanime.components.HanimeVideoCard
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HanimeDetailSheet(
    viewModel: HanimeViewModel,
    onPlayClick: (HanimeVideo, String) -> Unit,
    onTagClick: (String) -> Unit,
    onEpisodeClick: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
    isDark: Boolean = true,
    themeAccent: Color = Color(0xFF6366F1)
) {
    val selectedVideoCode by viewModel.selectedVideoCode.collectAsState()
    val videoDetail by viewModel.selectedVideoDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()

    if (selectedVideoCode == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val surfaceColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.94f) else Color(0xFFF8FAFC).copy(alpha = 0.96f)
    val textColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    var resolutionMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.15f))
            )
        }
    ) {
        if (isLoadingDetail || videoDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = themeAccent, strokeWidth = 3.dp)
                    Text(
                        text = "正在解析番剧详情...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
            }
        } else {
            val video = videoDetail!!
            val availableResolutions = video.videoUrls.keys.toList().ifEmpty { listOf("默认") }
            var currentResolution by remember { mutableStateOf(viewModel.activePlayingResolution.ifBlank { availableResolutions.first() }) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // 1. Header Poster Card & Gradient Hero
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        // Blurred Background Backdrop Image
                        AsyncImage(
                            model = video.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                                .background(Color.Black.copy(alpha = 0.4f))
                        )

                        // Dark Gradient Fade to bottom
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            surfaceColor
                                        )
                                    )
                                )
                        )

                        // Main Poster & Title Row
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // High-res Poster Box
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = video.coverUrl,
                                    contentDescription = video.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Titles & Meta
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = video.chineseTitle ?: video.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        lineHeight = 22.sp
                                    ),
                                    color = textColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!video.chineseTitle.isNullOrBlank() && video.chineseTitle != video.title) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!video.views.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = secondaryTextColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = video.views,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secondaryTextColor
                                            )
                                        }
                                    }

                                    if (!video.uploadTime.isNullOrBlank()) {
                                        Text(
                                            text = video.uploadTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                            }
                        }

                        // Close button at top right
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Play Action & Resolution Selection Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play Button
                        LiquidButton(
                            onClick = {
                                onPlayClick(video, currentResolution)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            themeAccent = themeAccent
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "立即在线播放",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Resolution Dropdown Selector
                        Box {
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                    .clickable { resolutionMenuExpanded = true }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = themeAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = currentResolution,
                                    color = textColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = resolutionMenuExpanded,
                                onDismissRequest = { resolutionMenuExpanded = false }
                            ) {
                                availableResolutions.forEach { res ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = res,
                                                fontWeight = if (res == currentResolution) FontWeight.Bold else FontWeight.Normal,
                                                color = if (res == currentResolution) themeAccent else textColor
                                            )
                                        },
                                        onClick = {
                                            currentResolution = res
                                            viewModel.activePlayingResolution = res
                                            resolutionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Artist / Author Card
                if (!video.artistName.isNullOrBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFF1F5F9))
                                .clickable {
                                    onTagClick(video.artistName)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!video.artistAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = video.artistAvatarUrl,
                                    contentDescription = video.artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(themeAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = themeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.artistName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = textColor
                                )
                                if (!video.artistGenre.isNullOrBlank()) {
                                    Text(
                                        text = video.artistGenre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor
                                    )
                                }
                            }

                            Text(
                                text = "查看TA的作品 ›",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                // 4. Series / Playlist Episodes (If series has multiple episodes)
                val episodes = video.playlist?.episodes
                if (!episodes.isNullOrEmpty() && episodes.size > 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                        ) {
                            Text(
                                text = "系列选集 (${episodes.size} 集)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(episodes.size) { index ->
                                    val ep = episodes[index]
                                    val isCurrent = ep.videoCode == video.videoCode

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isCurrent) themeAccent
                                                else if (isDark) Color(0xFF1E293B)
                                                else Color(0xFFE2E8F0)
                                            )
                                            .clickable {
                                                onEpisodeClick(ep.videoCode, index)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "第 ${index + 1} 集" + if (ep.title.isNotBlank()) " · ${ep.title.take(10)}" else "",
                                            color = if (isCurrent) Color.White else textColor,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Tags Flow
                if (video.tags.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "番剧标签",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = textColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                video.tags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                            .clickable { onTagClick(tag) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "# $tag",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.sp,
                                                color = themeAccent,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Introduction / Synopsis
                if (!video.introduction.isNullOrBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "番剧简介",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = textColor,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Text(
                                text = video.introduction,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                // 7. Related Recommendations
                if (video.relatedHanimes.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                text = "相关番剧推荐",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(video.relatedHanimes, key = { index, related -> "${related.videoCode}_$index" }) { _, related ->
                                    HanimeVideoCard(
                                        video = related,
                                        onClick = { viewModel.openVideoDetail(related.videoCode) },
                                        backdrop = backdrop,
                                        isDark = isDark,
                                        themeAccent = themeAccent,
                                        cardWidth = 145f
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Comments Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "番剧评论",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = textColor
                            )
                            if (comments.isNotEmpty()) {
                                Text(
                                    text = "(${comments.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        if (isLoadingComments) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = themeAccent, modifier = Modifier.size(24.dp))
                            }
                        } else if (comments.isEmpty()) {
                            Text(
                                text = "暂无评论",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                comments.take(15).forEach { comment ->
                                    HanimeCommentItem(
                                        comment = comment,
                                        isDark = isDark,
                                        themeAccent = themeAccent,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor
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
private fun HanimeCommentItem(
    comment: HanimeComment,
    isDark: Boolean,
    themeAccent: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // User Avatar
        if (comment.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.name.take(1).uppercase(),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Text(
                    text = comment.date,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor
                )
            }

            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = textColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 3.dp)
            )

            if (comment.likesCount > 0) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "likes",
                        tint = themeAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${comment.likesCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = themeAccent
                    )
                }
            }
        }
    }
}
