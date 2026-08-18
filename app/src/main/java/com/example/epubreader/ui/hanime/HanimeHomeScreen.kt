package com.example.epubreader.ui.hanime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubreader.data.hanime.HanimeHomePage
import com.example.epubreader.data.hanime.HanimeInfo
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.example.epubreader.ui.hanime.components.HanimeBannerCarousel
import com.example.epubreader.ui.hanime.components.HanimeCategorySection
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanimeHomeScreen(
    viewModel: HanimeViewModel,
    onSearchClick: () -> Unit,
    onVideoClick: (HanimeInfo) -> Unit,
    onCategoryMoreClick: (String) -> Unit,
    onExit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isDark: Boolean = true,
    themeAccent: Color = Color(0xFF6366F1)
) {
    if (onExit != null) {
        BackHandler {
            onExit()
        }
    }

    val homePageState by viewModel.homePageState.collectAsState()
    val isRefreshing by viewModel.isRefreshingHome.collectAsState()

    val textColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val searchBarBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.8f)

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top Search Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onExit != null) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(searchBarBg)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回媒体库",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(searchBarBg)
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = themeAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "搜索 Hanime 在线番剧、标签...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    fontSize = 14.sp
                )
            }

            IconButton(
                onClick = { viewModel.loadHomePage(forceRefresh = true) },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(searchBarBg)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "refresh",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Body Content
        when (val state = homePageState) {
            is HanimePageState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = themeAccent, strokeWidth = 3.dp)
                        Text(
                            text = "正在连接 Hanime...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor
                        )
                    }
                }
            }
            is HanimePageState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "网络解析异常",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        LiquidButton(
                            onClick = { viewModel.loadHomePage(forceRefresh = true) },
                            themeAccent = themeAccent
                        ) {
                            Text(text = "重试加载", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is HanimePageState.Success -> {
                val data = state.data
                val refreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadHomePage(forceRefresh = true) },
                    state = refreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        // 1. Featured Spotlight Banner
                        item {
                            HanimeBannerCarousel(
                                banner = data.banner,
                                onBannerClick = { vCode ->
                                    viewModel.openVideoDetail(vCode)
                                },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 2. Latest Releases (最新上市)
                        item {
                            HanimeCategorySection(
                                title = "最新上市",
                                icon = Icons.Default.NewReleases,
                                videos = data.latestRelease,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("最新上市") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 3. Latest Uploads (最新上传)
                        item {
                            HanimeCategorySection(
                                title = "最新上传",
                                icon = Icons.Default.Upload,
                                videos = data.latestUpload,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("最新上傳") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 4. Hentai Anime (里番精选)
                        item {
                            HanimeCategorySection(
                                title = "里番精选",
                                icon = Icons.Default.Tv,
                                videos = data.hentaiAnime,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("裏番") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 5. 3DCG Anime (3D动画)
                        item {
                            HanimeCategorySection(
                                title = "3DCG 专区",
                                icon = Icons.Default.Videocam,
                                videos = data.threeDCG,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("3DCG") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 6. Short Anime (泡面番)
                        item {
                            HanimeCategorySection(
                                title = "泡面番",
                                icon = Icons.Default.Movie,
                                videos = data.shortAnime,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("泡麵番") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 7. Trending Now (他们在看)
                        item {
                            HanimeCategorySection(
                                title = "他们在看",
                                icon = Icons.Default.TrendingUp,
                                videos = data.watchingNow,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("他們在看") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 8. 2D Anime
                        item {
                            HanimeCategorySection(
                                title = "2D 动画",
                                icon = Icons.Default.Tv,
                                videos = data.twoDAnime,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("2D動畫") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 9. Cosplay
                        item {
                            HanimeCategorySection(
                                title = "Cosplay",
                                icon = Icons.Default.CameraAlt,
                                videos = data.cosplay,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("Cosplay") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }

                        // 10. AI Generated
                        item {
                            HanimeCategorySection(
                                title = "AI 生成",
                                icon = Icons.Default.AutoAwesome,
                                videos = data.aiGenerated,
                                onVideoClick = onVideoClick,
                                onMoreClick = { onCategoryMoreClick("AI生成") },
                                backdrop = backdrop,
                                isDark = isDark,
                                themeAccent = themeAccent
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
