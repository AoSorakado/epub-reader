package com.example.epubreader.ui.novelia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.epubreader.data.novelia.NoveliaDownloadTask
import com.example.epubreader.data.novelia.NoveliaVolume
import com.example.epubreader.data.novelia.NoveliaWebNovel
import com.example.epubreader.data.novelia.NoveliaWenkuNovel
import com.example.epubreader.data.novelia.TranslationEngine
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoveliaWenkuDetailDialog(
    novel: NoveliaWenkuNovel,
    activeTask: NoveliaDownloadTask?,
    onDismiss: () -> Unit,
    onDownloadVolume: (NoveliaWenkuNovel, NoveliaVolume, TranslationEngine) -> Unit,
    onDownloadAll: (NoveliaWenkuNovel, TranslationEngine) -> Unit,
    onToggleFavorite: (NoveliaWenkuNovel) -> Unit = {}
) {
    var selectedEngine by remember { mutableStateOf(TranslationEngine.SAKURA) }
    val isDark = isSystemInDarkTheme()
    val currentToast by GlobalToastManager.currentToast.collectAsState()

    val bgCard = if (isDark) Color(0xFF1E1E2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgCard)
                    .padding(18.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "文库小说详情",
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggleFavorite(novel) }) {
                            Icon(
                                imageVector = if (novel.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (novel.isFavorited) Color(0xFFEF4444) else subTextColor
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = subTextColor)
                        }
                    }
                }

                // In-Dialog Top Download Status Notification Banner
                if (activeTask != null && activeTask.novelId == novel.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (activeTask.error != null) Color(0xFFEF4444).copy(alpha = 0.15f)
                                else if (activeTask.isCompleted) Color(0xFF10B981).copy(alpha = 0.15f)
                                else Color(0xFF3B82F6).copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (activeTask.error != null) Color(0xFFEF4444).copy(alpha = 0.4f)
                                else if (activeTask.isCompleted) Color(0xFF10B981).copy(alpha = 0.4f)
                                else Color(0xFF3B82F6).copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (activeTask.error != null) Icons.Default.Close else if (activeTask.isCompleted) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (activeTask.error != null) Color(0xFFEF4444) else if (activeTask.isCompleted) Color(0xFF10B981) else Color(0xFF3B82F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeTask.statusText,
                                        color = if (activeTask.error != null) Color(0xFFEF4444) else if (activeTask.isCompleted) Color(0xFF10B981) else textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!activeTask.isCompleted && activeTask.progress > 0f) {
                                    Text(
                                        text = "${(activeTask.progress * 100).toInt()}%",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!activeTask.isCompleted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { activeTask.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF3B82F6),
                                    trackColor = if (isDark) Color(0xFF3F3F5A) else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // Novel Header Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF28283B) else Color(0xFFE2E8F0))
                    ) {
                        if (novel.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = novel.coverUrl,
                                contentDescription = novel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = subTextColor,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel.title,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (novel.japaneseTitle.isNotBlank() && novel.japaneseTitle != novel.title) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = novel.japaneseTitle,
                                color = subTextColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (novel.author.isNotBlank() && novel.author != "未知作者") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = subTextColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = novel.author, color = subTextColor, fontSize = 12.sp)
                            }
                        }

                        if (novel.publisher.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${novel.publisher} ${novel.imprint}".trim(), color = subTextColor, fontSize = 11.sp)
                        }
                    }
                }

                // Description
                if (novel.description.isNotBlank()) {
                    Text(
                        text = novel.description,
                        color = subTextColor,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Tags
                if (novel.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        novel.tags.take(6).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF28283B) else Color(0xFFE2E8F0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = tag, color = subTextColor, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Translation Engine Selector
                Text(
                    text = "选择机翻引擎",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TranslationEngine.SAKURA,
                        TranslationEngine.GPT,
                        TranslationEngine.YOUDAO
                    ).forEach { eng ->
                        val isSelected = selectedEngine == eng
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF3B82F6) else (if (isDark) Color(0xFF28283B) else Color(0xFFE2E8F0)))
                                .clickable { selectedEngine = eng }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else subTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = eng.displayName,
                                    color = if (isSelected) Color.White else subTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Download All Button
                if (novel.volumes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
                                )
                            )
                            .clickable { onDownloadAll(novel, selectedEngine) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "下载全系列至 WebDAV (${novel.volumes.size} 卷)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Volume List Header
                Text(
                    text = "分卷列表 (共 ${novel.volumes.size} 卷)",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Volume List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(novel.volumes) { vol ->
                        val isThisDownloading = activeTask != null &&
                                activeTask.novelId == novel.id &&
                                activeTask.volumeOrChapterTitle.contains(vol.volumeName) &&
                                !activeTask.isCompleted

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF28283B) else Color(0xFFF1F5F9))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vol.volumeName,
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "总计 ${vol.totalChapters} 章 · Sakura ${vol.sakuraChapters} · GPT ${vol.gptChapters}",
                                    color = subTextColor,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isThisDownloading) {
                                CircularProgressIndicator(
                                    progress = { activeTask?.progress ?: 0f },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = Color(0xFF3B82F6)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                                        .clickable { onDownloadVolume(novel, vol, selectedEngine) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "下载",
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "下载",
                                            color = Color(0xFF3B82F6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Active Download HUD
                if (activeTask != null && activeTask.novelId == novel.id) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF2B2B40) else Color(0xFFEEF2FF))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeTask.statusText,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(activeTask.progress * 100).toInt()}%",
                                color = Color(0xFF3B82F6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { activeTask.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF3B82F6),
                            trackColor = if (isDark) Color(0xFF3F3F5A) else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoveliaWebNovelDetailDialog(
    novel: NoveliaWebNovel,
    activeTask: NoveliaDownloadTask?,
    onDismiss: () -> Unit,
    onDownloadNovel: (NoveliaWebNovel, TranslationEngine) -> Unit,
    onToggleFavorite: (NoveliaWebNovel) -> Unit = {}
) {
    var selectedEngine by remember { mutableStateOf(TranslationEngine.SAKURA) }
    val isDark = isSystemInDarkTheme()
    val currentToast by GlobalToastManager.currentToast.collectAsState()

    val bgCard = if (isDark) Color(0xFF1E1E2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgCard)
                    .padding(18.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "网络小说详情",
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggleFavorite(novel) }) {
                            Icon(
                                imageVector = if (novel.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (novel.isFavorited) Color(0xFFEF4444) else subTextColor
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = subTextColor)
                        }
                    }
                }

                // In-Dialog Top Download Status Notification Banner
                if (activeTask != null && activeTask.novelId == novel.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (activeTask.error != null) Color(0xFFEF4444).copy(alpha = 0.15f)
                                else if (activeTask.isCompleted) Color(0xFF10B981).copy(alpha = 0.15f)
                                else Color(0xFF3B82F6).copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (activeTask.error != null) Color(0xFFEF4444).copy(alpha = 0.4f)
                                else if (activeTask.isCompleted) Color(0xFF10B981).copy(alpha = 0.4f)
                                else Color(0xFF3B82F6).copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (activeTask.error != null) Icons.Default.Close else if (activeTask.isCompleted) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (activeTask.error != null) Color(0xFFEF4444) else if (activeTask.isCompleted) Color(0xFF10B981) else Color(0xFF3B82F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeTask.statusText,
                                        color = if (activeTask.error != null) Color(0xFFEF4444) else if (activeTask.isCompleted) Color(0xFF10B981) else textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!activeTask.isCompleted && activeTask.progress > 0f) {
                                    Text(
                                        text = "${(activeTask.progress * 100).toInt()}%",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!activeTask.isCompleted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { activeTask.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF3B82F6),
                                    trackColor = if (isDark) Color(0xFF3F3F5A) else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // Novel Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF28283B) else Color(0xFFE2E8F0))
                    ) {
                        if (novel.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = novel.coverUrl,
                                contentDescription = novel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = subTextColor,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel.title,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (novel.japaneseTitle.isNotBlank() && novel.japaneseTitle != novel.title) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = novel.japaneseTitle,
                                color = subTextColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "作者: ${novel.author} · ${novel.status}",
                            color = subTextColor,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = novel.sourcePlatform,
                                    color = Color(0xFF3B82F6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "共 ${novel.totalChapters} 章",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Description
                if (novel.description.isNotBlank()) {
                    Text(
                        text = novel.description,
                        color = subTextColor,
                        fontSize = 12.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Translation Engine Selector
                Text(
                    text = "选择机翻引擎",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TranslationEngine.SAKURA,
                        TranslationEngine.GPT,
                        TranslationEngine.YOUDAO
                    ).forEach { eng ->
                        val isSelected = selectedEngine == eng
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF3B82F6) else (if (isDark) Color(0xFF28283B) else Color(0xFFE2E8F0)))
                                .clickable { selectedEngine = eng }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else subTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = eng.displayName,
                                    color = if (isSelected) Color.White else subTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Download Button
                val isDownloading = activeTask != null &&
                        activeTask.novelId == novel.id &&
                        !activeTask.isCompleted

                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { activeTask?.progress ?: 0f },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF3B82F6)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = activeTask?.statusText ?: "下载中...",
                                color = Color(0xFF3B82F6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                                )
                            )
                            .clickable { onDownloadNovel(novel, selectedEngine) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "打包并下载至 WebDAV [${selectedEngine.displayName}]",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Download Progress
                if (activeTask != null && activeTask.novelId == novel.id) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF2B2B40) else Color(0xFFEEF2FF))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeTask.statusText,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(activeTask.progress * 100).toInt()}%",
                                color = Color(0xFF3B82F6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { activeTask.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF3B82F6),
                            trackColor = if (isDark) Color(0xFF3F3F5A) else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}


