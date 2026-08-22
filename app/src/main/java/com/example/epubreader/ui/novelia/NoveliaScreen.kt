package com.example.epubreader.ui.novelia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.epubreader.data.linovelib.LinovelibNovel
import com.example.epubreader.data.novelia.NoveliaCategory
import com.example.epubreader.data.novelia.NoveliaViewMode
import com.example.epubreader.data.novelia.NoveliaWebNovel
import com.example.epubreader.data.novelia.NoveliaWenkuNovel
import com.example.epubreader.ui.linovelib.LinovelibBrowserDialog
import com.example.epubreader.ui.linovelib.LinovelibDetailDialog

@Composable
fun NoveliaScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoveliaViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF12121A) else Color(0xFFF8FAFC)
    val cardColor = if (isDark) Color(0xFF1E1E2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val viewMode by viewModel.viewMode.collectAsState()
    val filter by viewModel.searchFilter.collectAsState()
    val wenkuNovels by viewModel.wenkuNovels.collectAsState()
    val webNovels by viewModel.webNovels.collectAsState()
    val linovelibNovels by viewModel.linovelibNovels.collectAsState()
    val favoredWenku by viewModel.favoredWenkuNovels.collectAsState()
    val favoredWeb by viewModel.favoredWebNovels.collectAsState()
    val favoriteFolders by viewModel.favoriteFolders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val selectedWenku by viewModel.selectedWenkuNovel.collectAsState()
    val selectedWeb by viewModel.selectedWebNovel.collectAsState()
    val selectedLinovelib by viewModel.selectedLinovelibNovel.collectAsState()
    val showLinovelibBrowser by viewModel.showLinovelibBrowser.collectAsState()
    val activeTask by viewModel.activeDownloadTask.collectAsState()
    val userSession by viewModel.userSession.collectAsState()
    val linovelibSubCategory by viewModel.linovelibSubCategory.collectAsState()
    val linovelibUsername by viewModel.linovelibUsername.collectAsState()

    var searchInput by remember { mutableStateOf(filter.keyword) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (filter.category == NoveliaCategory.LINOVELIB) "哔哩轻小说" else "Novelia 在线书库",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Browse vs Favorites Segment
                    val isBrowse = viewMode == NoveliaViewMode.BROWSE
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isBrowse) Color(0xFF3B82F6) else Color.Transparent)
                                .clickable { viewModel.setViewMode(NoveliaViewMode.BROWSE) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "书库",
                                color = if (isBrowse) Color.White else subTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (isBrowse) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (!isBrowse) Color(0xFFEC4899) else Color.Transparent)
                                .clickable { viewModel.setViewMode(NoveliaViewMode.FAVORITES) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (!isBrowse) Color.White else subTextColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "我的收藏",
                                    color = if (!isBrowse) Color.White else subTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (!isBrowse) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (filter.category == NoveliaCategory.LINOVELIB) {
                        // Linovelib Account / Browser Entry
                        val hasLinovelibUser = linovelibUsername.isNotBlank()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (hasLinovelibUser) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF3B82F6).copy(alpha = 0.15f))
                                .clickable { viewModel.openLinovelibBrowser() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasLinovelibUser) Icons.Default.Person else Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = if (hasLinovelibUser) Color(0xFF10B981) else Color(0xFF3B82F6),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (hasLinovelibUser) linovelibUsername else "网页/登录",
                                    color = if (hasLinovelibUser) Color(0xFF10B981) else Color(0xFF3B82F6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Novelia Account Entry
                        val isNoveliaLoggedIn = userSession.isLoggedIn
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isNoveliaLoggedIn) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFFEC4899).copy(alpha = 0.15f))
                                .clickable { showLoginDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isNoveliaLoggedIn) Color(0xFF6366F1) else Color(0xFFEC4899),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isNoveliaLoggedIn) userSession.username.ifEmpty { "已登录" } else "账号登录",
                                    color = if (isNoveliaLoggedIn) Color(0xFF6366F1) else Color(0xFFEC4899),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Category Segmented Tabs (文库小说 / 网络小说 / 哔哩轻小说)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0))
                        .padding(3.dp)
                ) {
                    NoveliaCategory.values().forEach { cat ->
                        val isSelected = filter.category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) (if (isDark) Color(0xFF3B82F6) else Color.White) else Color.Transparent)
                                .clickable { viewModel.setCategory(cat) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.displayName,
                                color = if (isSelected) (if (isDark) Color.White else Color(0xFF3B82F6)) else subTextColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (viewMode == NoveliaViewMode.BROWSE) {
                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = {
                            Text(
                                when (filter.category) {
                                    NoveliaCategory.WENKU -> "搜索文库书名或作者..."
                                    NoveliaCategory.WEB_NOVEL -> "搜索网络小说标题或作者..."
                                    NoveliaCategory.LINOVELIB -> "搜索哔哩轻小说 (tw.linovelib.com)..."
                                },
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = subTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchInput = ""
                                    viewModel.setKeyword("")
                                    viewModel.loadNovels(resetPage = true)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空",
                                        tint = subTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.setKeyword(searchInput)
                            viewModel.loadNovels(resetPage = true)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = if (isDark) Color(0xFF33334D) else Color(0xFFCBD5E1),
                            focusedContainerColor = cardColor,
                            unfocusedContainerColor = cardColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips for Browse Mode
                when (filter.category) {
                    NoveliaCategory.WENKU -> {
                        val wenkuLevels = if (userSession.isLoggedIn) {
                            listOf(
                                "全部小说" to 0,
                                "轻小说" to 1,
                                "轻文学" to 2,
                                "文学" to 3,
                                "非小说" to 4,
                                "R18男性向" to 5,
                                "R18女性向" to 6
                            )
                        } else {
                            listOf(
                                "全部小说" to 0,
                                "轻小说" to 1,
                                "轻文学" to 2,
                                "文学" to 3,
                                "非小说" to 4
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(wenkuLevels) { (label, levelVal) ->
                                val isSel = filter.wenkuLevel == levelVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) Color(0xFF3B82F6) else (if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0)))
                                        .clickable { viewModel.setWenkuLevel(levelVal) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else (if (label.contains("R18")) Color(0xFFEF4444) else subTextColor),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    NoveliaCategory.LINOVELIB -> {
                        val subCats = listOf(
                            "轻小说文库" to 0,
                            "月点击榜" to 1,
                            "总收藏榜" to 2,
                            "月推荐榜" to 3,
                            "全本完结" to 4,
                            "首页推荐" to 5
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(subCats) { (label, subVal) ->
                                val isSel = linovelibSubCategory == subVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) Color(0xFF3B82F6) else (if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0)))
                                        .clickable { viewModel.setLinovelibSubCategory(subVal) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else subTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    NoveliaCategory.WEB_NOVEL -> {
                        val sources = listOf(
                            "全部来源" to "kakuyomu,syosetu,novelup,hameln,pixiv,alphapolis",
                            "Kakuyomu" to "kakuyomu",
                            "成为小说家吧" to "syosetu",
                            "Hameln" to "hameln",
                            "Pixiv" to "pixiv",
                            "Novelup" to "novelup",
                            "Alphapolis" to "alphapolis"
                        )

                        Column {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(sources) { (label, srcKey) ->
                                    val isSel = filter.webProvider == srcKey
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF3B82F6) else (if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0)))
                                        .clickable { viewModel.setWebProvider(srcKey) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else subTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val webLevels = if (userSession.isLoggedIn) {
                            listOf("全部向" to 0, "一般向" to 1, "R18" to 2)
                        } else {
                            listOf("全部向" to 0, "一般向" to 1)
                        }
                        val webTypes = listOf("全部状态" to 0, "连载中" to 1, "已完结" to 2, "短篇" to 3)

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(webLevels) { (label, lvl) ->
                                val isSel = filter.webLevel == lvl
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) Color(0xFF6366F1) else (if (isDark) Color(0xFF1E1E2C) else Color(0xFFEDE9FE)))
                                        .clickable { viewModel.setWebLevel(lvl) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else (if (label == "R18") Color(0xFFEF4444) else subTextColor),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            items(webTypes) { (label, typ) ->
                                val isSel = filter.webType == typ
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) Color(0xFF059669) else (if (isDark) Color(0xFF1E1E2C) else Color(0xFFD1FAE5)))
                                        .clickable { viewModel.setWebType(typ) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else subTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } else {
                // Favorites Mode: Folders & Sort Rows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite Folders
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(favoriteFolders) { folder ->
                            val isSel = filter.favoriteFolderId == folder.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFFEC4899) else (if (isDark) Color(0xFF222233) else Color(0xFFE2E8F0)))
                                    .clickable { viewModel.setFavoriteFolder(folder.id) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSel) Color.White else subTextColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = folder.name,
                                        color = if (isSel) Color.White else subTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort Chips
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF1E1E2E) else Color(0xFFEDE9FE))
                            .padding(2.dp)
                    ) {
                        listOf("更新时间" to 0, "收藏时间" to 1).forEach { (lbl, sortVal) ->
                            val isSel = filter.favoriteSort == sortVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF8B5CF6) else Color.Transparent)
                                    .clickable { viewModel.setFavoriteSort(sortVal) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = lbl,
                                    color = if (isSel) Color.White else subTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content Area (Grid / List)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewMode == NoveliaViewMode.BROWSE) {
                    when (filter.category) {
                        NoveliaCategory.WENKU -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(wenkuNovels) { novel ->
                                    WenkuNovelCard(
                                        novel = novel,
                                        isDark = isDark,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        onClick = { viewModel.openWenkuDetail(novel) }
                                    )
                                }

                                if (wenkuNovels.isNotEmpty()) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3B82F6))
                                            } else {
                                                Text(
                                                    text = "点击加载更多...",
                                                    color = Color(0xFF3B82F6),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.clickable { viewModel.loadMore() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        NoveliaCategory.LINOVELIB -> {
                            if (linovelibNovels.isEmpty() && !isLoading) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                        .align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (filter.keyword.isNotBlank()) "未在公开列表中搜到「${filter.keyword}」" else "暂无哔哩轻小说数据",
                                        color = textColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (filter.keyword.isNotBlank())
                                            "部分下架/版权隐藏作品（如 ID: 4586）未在公共列表展示，但章节依然完整。点击下方「网页搜索」找到后一键导入，或在上方搜索栏直接输入数字 ID / 链接。"
                                        else
                                            "若首次加载或遭遇人机拦截，可点击「重新加载」或打开网页验证",
                                        color = subTextColor,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (filter.keyword.isNotBlank()) {
                                            Button(
                                                onClick = { viewModel.openLinovelibWebSearch(filter.keyword) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                                            ) {
                                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("在网页中搜索「${filter.keyword}」")
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.loadNovels(resetPage = true) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                            ) {
                                                Text("重新加载")
                                            }
                                            Button(
                                                onClick = { viewModel.openLinovelibBrowser() },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                            ) {
                                                Text("打开网页验证")
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(linovelibNovels) { novel ->
                                        LinovelibNovelCard(
                                            novel = novel,
                                            isDark = isDark,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            subTextColor = subTextColor,
                                            onClick = { viewModel.openLinovelibDetail(novel) }
                                        )
                                    }

                                    if (linovelibNovels.isNotEmpty()) {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isLoading) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3B82F6))
                                                } else {
                                                    Text(
                                                        text = "点击加载更多...",
                                                        color = Color(0xFF3B82F6),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.clickable { viewModel.loadMore() }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        NoveliaCategory.WEB_NOVEL -> {
                        // Web Novels tab
                        if (!userSession.isLoggedIn && webNovels.isEmpty() && !isLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp)
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "网络小说专区需要登录",
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Novelia 平台要求登录账号后方可访问网络小说资源",
                                    color = subTextColor,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { showLoginDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                ) {
                                    Text("一键登录账号", color = Color.White)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(1),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(webNovels) { novel ->
                                    WebNovelCard(
                                        novel = novel,
                                        isDark = isDark,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        onClick = { viewModel.openWebNovelDetail(novel) }
                                    )
                                }

                                if (webNovels.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3B82F6))
                                            } else {
                                                Text(
                                                    text = "点击加载更多...",
                                                    color = Color(0xFF3B82F6),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.clickable { viewModel.loadMore() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // FAVORITES VIEW
                    if (!userSession.isLoggedIn) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFEC4899).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "云端收藏夹需要登录",
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "登录 Novelia 账号以同步查看您的云端收藏夹与书签",
                                color = subTextColor,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showLoginDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                            ) {
                                Text("一键登录账号", color = Color.White)
                            }
                        }
                    } else {
                        when (filter.category) {
                            NoveliaCategory.WENKU -> {
                                if (favoredWenku.isEmpty() && !isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = "文库收藏夹为空", color = subTextColor, fontSize = 14.sp)
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(favoredWenku) { novel ->
                                            WenkuNovelCard(
                                                novel = novel,
                                                isDark = isDark,
                                                cardColor = cardColor,
                                                textColor = textColor,
                                                subTextColor = subTextColor,
                                                onClick = { viewModel.openWenkuDetail(novel) }
                                            )
                                        }
                                    }
                                }
                            }
                            NoveliaCategory.LINOVELIB -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = "哔哩轻小说暂不支持云端收藏夹同步，请在「书库」中浏览或搜索下载", color = subTextColor, fontSize = 14.sp)
                                }
                            }
                            NoveliaCategory.WEB_NOVEL -> {
                                if (favoredWeb.isEmpty() && !isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = "网络小说收藏夹为空", color = subTextColor, fontSize = 14.sp)
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(1),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(favoredWeb) { novel ->
                                            WebNovelCard(
                                                novel = novel,
                                                isDark = isDark,
                                                cardColor = cardColor,
                                                textColor = textColor,
                                                subTextColor = subTextColor,
                                                onClick = { viewModel.openWebNovelDetail(novel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading Overlay on initial load
                if (isLoading && ((viewMode == NoveliaViewMode.BROWSE && wenkuNovels.isEmpty() && webNovels.isEmpty()) ||
                    (viewMode == NoveliaViewMode.FAVORITES && favoredWenku.isEmpty() && favoredWeb.isEmpty()))) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                    }
                }

                // Error retry banner
                if (errorMsg != null && !isLoading) {
                    val isBrowsingEmpty = viewMode == NoveliaViewMode.BROWSE && wenkuNovels.isEmpty() && webNovels.isEmpty()
                    val isFavEmpty = viewMode == NoveliaViewMode.FAVORITES && favoredWenku.isEmpty() && favoredWeb.isEmpty()
                    if (isBrowsingEmpty || isFavEmpty) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMsg ?: "加载失败",
                                color = subTextColor,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { 
                                    if (viewMode == NoveliaViewMode.FAVORITES) viewModel.loadFavorites(resetPage = true)
                                    else viewModel.loadNovels(resetPage = true)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("重新加载")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Download Banner (Bottom HUD)
        if (activeTask != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1E1E2E) else Color.White)
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!activeTask!!.isCompleted) {
                            CircularProgressIndicator(
                                progress = { activeTask!!.progress },
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp,
                                color = Color(0xFF3B82F6)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "${activeTask!!.novelTitle} ${activeTask!!.volumeOrChapterTitle}",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeTask!!.statusText,
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.dismissActiveDownloadTask() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "关闭",
                            tint = subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Dialogs
        if (selectedWenku != null) {
            NoveliaWenkuDetailDialog(
                novel = selectedWenku!!,
                activeTask = activeTask,
                onDismiss = { viewModel.closeWenkuDetail() },
                onDownloadVolume = { n, v, eng -> viewModel.downloadWenkuVolume(n, v, eng) },
                onDownloadAll = { n, eng -> viewModel.downloadAllWenkuVolumes(n, eng) },
                onToggleFavorite = { viewModel.toggleFavoriteWenku(it) }
            )
        }

        if (selectedWeb != null) {
            NoveliaWebNovelDetailDialog(
                novel = selectedWeb!!,
                activeTask = activeTask,
                onDismiss = { viewModel.closeWebNovelDetail() },
                onDownloadNovel = { n, eng -> viewModel.downloadWebNovel(n, eng) },
                onToggleFavorite = { viewModel.toggleFavoriteWeb(it) }
            )
        }

        if (selectedLinovelib != null) {
            LinovelibDetailDialog(
                novel = selectedLinovelib!!,
                activeTask = activeTask,
                onDismiss = { viewModel.closeLinovelibDetail() },
                onOpenInBrowser = { url -> viewModel.openLinovelibBrowser(url) },
                onDownloadVolume = { n, v -> viewModel.downloadLinovelibVolume(n, v) },
                onDownloadAllVolumes = { n -> viewModel.downloadAllLinovelibVolumes(n) }
            )
        }

        if (showLinovelibBrowser) {
            val browserUrl by viewModel.linovelibBrowserUrl.collectAsState()
            LinovelibBrowserDialog(
                initialUrl = browserUrl,
                savedUsername = linovelibUsername,
                onDismiss = { viewModel.closeLinovelibBrowser() },
                onCookiesExtracted = { cookies, ua, uname ->
                    viewModel.syncLinovelibCookies(cookies, ua, uname)
                },
                onNovelSelected = { novelId ->
                    viewModel.openLinovelibNovelById(novelId)
                }
            )
        }

        if (showLoginDialog) {
            NoveliaLoginDialog(
                onDismiss = { showLoginDialog = false },
                onSaveSession = { session ->
                    viewModel.saveUserSession(session)
                }
            )
        }
    }
}

@Composable
private fun WenkuNovelCard(
    novel: NoveliaWenkuNovel,
    isDark: Boolean,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, if (isDark) Color(0xFF2E2E42) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
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
                        .size(36.dp)
                        .align(Alignment.Center)
                )
            }

            // Rating badge
            if (novel.ratingCategory.contains("R18") || novel.ratingCategory.contains("18")) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(text = "R18", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = novel.title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (novel.japaneseTitle.isNotBlank() && novel.japaneseTitle != novel.title) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = novel.japaneseTitle,
                color = subTextColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (novel.author.isNotBlank() && novel.author != "未知作者") {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = novel.author,
                color = subTextColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WebNovelCard(
    novel: NoveliaWebNovel,
    isDark: Boolean,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, if (isDark) Color(0xFF2E2E42) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(75.dp)
                .height(105.dp)
                .clip(RoundedCornerShape(8.dp))
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
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = novel.title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = novel.sourcePlatform, color = Color(0xFF3B82F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

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
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEC4899).copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(text = "Sakura: ${novel.sakuraChapters}", color = Color(0xFFEC4899), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(text = "总计: ${novel.totalChapters} 章", color = Color(0xFF10B981), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LinovelibNovelCard(
    novel: LinovelibNovel,
    isDark: Boolean,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, if (isDark) Color(0xFF2E2E42) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Category tag
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = novel.category.ifEmpty { "轻小说" },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = novel.title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = novel.author,
                color = subTextColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = novel.status,
                color = if (novel.status.contains("完结")) Color(0xFF10B981) else Color(0xFF3B82F6),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

