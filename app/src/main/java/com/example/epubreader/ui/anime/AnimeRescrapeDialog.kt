package com.example.epubreader.ui.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.anime.AnimeMetadataScraper
import com.example.epubreader.data.anime.ScrapedAnimeInfo
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

@Composable
fun AnimeRescrapeDialog(
    initialKeyword: String,
    backdrop: Backdrop,
    isDark: Boolean,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onSelectCandidate: (ScrapedAnimeInfo) -> Unit
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var candidates by remember { mutableStateOf<List<ScrapedAnimeInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun performSearch(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        coroutineScope.launch {
            isLoading = true
            hasSearched = true
            try {
                candidates = AnimeMetadataScraper.searchMultiple(query.trim())
            } catch (e: Exception) {
                candidates = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    // Auto search initial keyword on opening
    LaunchedEffect(Unit) {
        if (initialKeyword.isNotBlank()) {
            performSearch(initialKeyword)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(top = 44.dp, bottom = 96.dp, start = 14.dp, end = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
                            radius = 24.dp,
                            color = Color.Black.copy(alpha = if (isDark) 0.40f else 0.20f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = if (isDark) 0.12f else 0.18f))
                    }
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color(0xFFE0E7FF).copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Bar: Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "联网刮削番剧信息",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Text(
                            text = "聚合检索 Bangumi & 豆瓣 多条结果，点击即可应用",
                            fontSize = 11.5.sp,
                            color = secondaryTextColor
                        )
                    }

                    LiquidButton(
                        onClick = onDismiss,
                        backdrop = backdrop,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = primaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Search Bar Row with Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = {
                            Text(
                                "输入准确番剧名称或日文原名...",
                                fontSize = 13.sp,
                                color = secondaryTextColor.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                            focusedContainerColor = Color.White.copy(alpha = if (isDark) 0.08f else 0.15f),
                            unfocusedContainerColor = Color.White.copy(alpha = if (isDark) 0.05f else 0.10f),
                            focusedBorderColor = themeAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = themeAccent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(keyword) }),
                        modifier = Modifier.weight(1f).height(50.dp)
                    )

                    LiquidButton(
                        onClick = { performSearch(keyword) },
                        backdrop = backdrop,
                        shape = RoundedCornerShape(14.dp),
                        tint = themeAccent,
                        modifier = Modifier.height(50.dp).padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("搜索", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Results Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = themeAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "正在联网检索 Bangumi & 豆瓣...",
                                    fontSize = 12.5.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        hasSearched && candidates.isEmpty() -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    tint = secondaryTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "未找到相关番剧信息",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor
                                )
                                Text(
                                    text = "请尝试更换更简短的中文名或日文原名搜索",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        candidates.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(candidates) { candidate ->
                                    ScrapedCandidateItem(
                                        candidate = candidate,
                                        backdrop = backdrop,
                                        isDark = isDark,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        themeAccent = themeAccent,
                                        onSelect = { onSelectCandidate(candidate) }
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
private fun ScrapedCandidateItem(
    candidate: ScrapedAnimeInfo,
    backdrop: Backdrop,
    isDark: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    themeAccent: Color,
    onSelect: () -> Unit
) {
    LiquidButton(
        onClick = onSelect,
        backdrop = backdrop,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster Thumbnail
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                if (candidate.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(candidate.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Tv, contentDescription = null, tint = secondaryTextColor.copy(0.4f), modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = candidate.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Original Title (if different)
                if (candidate.originalTitle.isNotBlank() && candidate.originalTitle != candidate.title) {
                    Text(
                        text = candidate.originalTitle,
                        fontSize = 11.5.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tags: Source Badge, Year, Score, Episodes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source Tag
                    val isDouban = candidate.source.contains("douban", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDouban) Color(0xFF007722).copy(alpha = 0.8f) else Color(0xFFE91E63).copy(alpha = 0.8f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isDouban) "豆瓣" else "Bangumi",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (candidate.airDate.isNotBlank()) {
                        Text(
                            text = candidate.airDate.take(4) + "年",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }

                    if (candidate.totalEpisodes > 0) {
                        Text(
                            text = "全${candidate.totalEpisodes}话",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }

                    if (candidate.score > 0f) {
                        Text(
                            text = "★ ${String.format("%.1f", candidate.score)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD60A)
                        )
                    }
                }

                // Summary snippet
                if (candidate.summary.isNotBlank()) {
                    Text(
                        text = candidate.summary,
                        fontSize = 11.sp,
                        color = secondaryTextColor.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Selection Action Indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(themeAccent.copy(alpha = 0.25f))
                    .border(0.8.dp, themeAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Select",
                    tint = themeAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
