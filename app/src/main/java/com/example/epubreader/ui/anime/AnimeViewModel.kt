package com.example.epubreader.ui.anime

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.anime.AnimeWebDavScanner
import com.example.epubreader.data.anime.BangumiApiClient
import com.example.epubreader.data.anime.BangumiSubject
import com.example.epubreader.data.db.AnimeWithEpisodes
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.network.WebDavClient
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class AnimeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val animeDao = db.animeDao()
    private val animeStatDao = db.animeStatDao()

    private val prefs = application.getSharedPreferences("anime_settings", Context.MODE_PRIVATE)

    val animes: StateFlow<List<AnimeEntity>> = animeDao.getAllAnimes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val animesWithEpisodes: StateFlow<List<AnimeWithEpisodes>> = animeDao.getAllAnimesWithEpisodes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val animeEpisodesMap: StateFlow<Map<Long, AnimeWithEpisodes>> = animesWithEpisodes
        .map { list -> list.associateBy { it.anime.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var activePlayingPair by androidx.compose.runtime.mutableStateOf<Pair<AnimeEntity, AnimeEpisodeEntity>?>(null)

    init {
        // Pre-warm Coil image cache ONLY for new covers that haven't been loaded
        viewModelScope.launch(Dispatchers.IO) {
            val queuedCovers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            animes.collect { list ->
                val imageLoader = coil.Coil.imageLoader(getApplication())
                for (anime in list) {
                    val coverPath = anime.localCoverPath
                    val coverUrl = anime.coverUrl
                    val key = coverPath?.ifBlank { null } ?: coverUrl?.ifBlank { null } ?: continue
                    if (queuedCovers.add(key)) {
                        val target: Any = if (!coverPath.isNullOrBlank() && File(coverPath).exists()) {
                            File(coverPath)
                        } else {
                            key
                        }
                        val req = coil.request.ImageRequest.Builder(getApplication())
                            .data(target)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(req)
                    }
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterStatus = MutableStateFlow(prefs.getInt("filterStatus", 0)) // 0: All, 1: Watching, 2: Finished
    val filterStatus: StateFlow<Int> = _filterStatus.asStateFlow()

    private val _sortMethod = MutableStateFlow(prefs.getInt("sortMethod", 0)) // 0: 最近观看, 1: 首播年份, 2: 最高评分, 3: 番剧名称, 4: 最新入库
    val sortMethod: StateFlow<Int> = _sortMethod.asStateFlow()

    private val _sortAscending = MutableStateFlow(prefs.getBoolean("sortAscending", false))
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    data class AnimeFilterState(
        val query: String = "",
        val filterStatus: Int = 0,
        val sortMethod: Int = 0,
        val sortAscending: Boolean = false
    )

    private val filterState = combine(
        _searchQuery,
        _filterStatus,
        _sortMethod,
        _sortAscending
    ) { query, filter, sort, asc ->
        AnimeFilterState(query, filter, sort, asc)
    }

    val filteredAnimes: StateFlow<List<AnimeEntity>> = combine(
        animes,
        filterState
    ) { list, state ->
        var res = list
        if (state.query.isNotBlank()) {
            res = res.filter { 
                it.title.contains(state.query, ignoreCase = true) || 
                it.originalTitle?.contains(state.query, ignoreCase = true) == true 
            }
        }
        when (state.filterStatus) {
            1 -> res = res.filter { !it.isFinished && it.lastWatchTimeMs > 0 }
            2 -> res = res.filter { it.isFinished }
        }
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        res = when (state.sortMethod) {
            0 -> {
                // 0: 最近观看 (倒序: 最近观看排在前, 正序: 最早观看排在前)
                if (state.sortAscending) {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.lastWatchTimeMs == 0L }
                            .thenBy { if (it.lastWatchTimeMs > 0) it.lastWatchTimeMs else it.updatedAt }
                            .thenBy { it.id }
                    )
                } else {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.lastWatchTimeMs == 0L }
                            .thenByDescending { if (it.lastWatchTimeMs > 0) it.lastWatchTimeMs else it.updatedAt }
                            .thenBy { it.id }
                    )
                }
            }
            1 -> {
                // 1: 首播年份 (倒序: 最新年份排在前, 正序: 早期年份排在前)
                if (state.sortAscending) {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.airDate.isNullOrBlank() }
                            .thenBy { it.airDate ?: "" }
                            .thenBy { it.id }
                    )
                } else {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.airDate.isNullOrBlank() }
                            .thenByDescending { it.airDate ?: "" }
                            .thenBy { it.id }
                    )
                }
            }
            2 -> {
                // 2: 最高评分 (倒序: 10.0 -> 0.0, 正序: 0.0 -> 10.0)
                if (state.sortAscending) {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.score == 0f }
                            .thenBy { it.score }
                            .thenBy { it.id }
                    )
                } else {
                    res.sortedWith(
                        compareBy<AnimeEntity> { it.score == 0f }
                            .thenByDescending { it.score }
                            .thenBy { it.id }
                    )
                }
            }
            3 -> {
                // 3: 番剧名称 (正序: A 到 Z, 倒序: Z 到 A)
                if (state.sortAscending) {
                    res.sortedWith { a, b ->
                        val cmp = collator.compare(a.title, b.title)
                        if (cmp != 0) cmp else a.id.compareTo(b.id)
                    }
                } else {
                    res.sortedWith { a, b ->
                        val cmp = collator.compare(b.title, a.title)
                        if (cmp != 0) cmp else b.id.compareTo(a.id)
                    }
                }
            }
            4 -> {
                // 4: 最新入库 (倒序: 最新入库排在前, 正序: 最早入库排在前)
                if (state.sortAscending) {
                    res.sortedWith(compareBy<AnimeEntity> { it.createdAt }.thenBy { it.id })
                } else {
                    res.sortedWith(compareByDescending<AnimeEntity> { it.createdAt }.thenBy { it.id })
                }
            }
            else -> res
        }
        res
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterStatus(status: Int) {
        _filterStatus.value = status
        prefs.edit().putInt("filterStatus", status).apply()
    }

    fun setSortMethod(method: Int) {
        _sortMethod.value = method
        prefs.edit().putInt("sortMethod", method).apply()
    }

    fun setSortAscending(ascending: Boolean) {
        _sortAscending.value = ascending
        prefs.edit().putBoolean("sortAscending", ascending).apply()
    }

    fun toggleSortOrder() {
        val newOrder = !_sortAscending.value
        _sortAscending.value = newOrder
        prefs.edit().putBoolean("sortAscending", newOrder).apply()
    }

    fun scanWebDav(webDavClient: WebDavClient) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = "正在连接 WebDAV 媒体库..."
        GlobalToastManager.showSyncing("正在扫描 WebDAV 媒体库...")

        viewModelScope.launch {
            try {
                val count = AnimeWebDavScanner.scanAnimeDirectory(
                    webDavClient = webDavClient,
                    animeDao = animeDao,
                    context = getApplication(),
                    onProgress = { current, total, name ->
                        _scanProgress.value = "正在解析 [$current/$total] $name"
                        GlobalToastManager.showSyncing("正在扫描 [$current/$total] $name")
                    },
                    onAnimeImported = { title, epCount ->
                        GlobalToastManager.show("✨ 已收录: $title ($epCount 集)", ToastType.Success)
                    }
                )
                if (count > 0) {
                    GlobalToastManager.show("✨ 扫描完成，共收录 $count 部番剧", ToastType.Success)
                    AnimeWebDavScanner.enrichAnimeMetadataInBackground(animeDao, getApplication())
                } else {
                    GlobalToastManager.show("未在指定的 WebDAV 目录中找到视频文件，请检查链接配置", ToastType.Info)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                GlobalToastManager.show("❌ 扫描失败: ${e.localizedMessage ?: "请检查 WebDAV 链接与账号密码"}", ToastType.Error)
            } finally {
                _isScanning.value = false
                _scanProgress.value = ""
            }
        }
    }

    suspend fun getAnimeWithEpisodes(animeId: Long) = withContext(Dispatchers.IO) {
        animeDao.getAnimeWithEpisodes(animeId)
    }

    fun getAnimeWithEpisodesSync(animeId: Long): AnimeWithEpisodes? {
        return animeEpisodesMap.value[animeId] ?: null
    }

    private val _isRefreshingSingleAnime = MutableStateFlow<Long?>(null)
    val isRefreshingSingleAnime: StateFlow<Long?> = _isRefreshingSingleAnime.asStateFlow()

    fun refreshSingleAnime(animeId: Long, webDavClient: WebDavClient) {
        if (_isRefreshingSingleAnime.value != null) return
        _isRefreshingSingleAnime.value = animeId
        GlobalToastManager.showSyncing("正在刷新该番剧数据...")

        viewModelScope.launch {
            try {
                val success = AnimeWebDavScanner.scanSingleAnime(
                    webDavClient = webDavClient,
                    animeDao = animeDao,
                    animeId = animeId,
                    context = getApplication()
                )
                if (success) {
                    GlobalToastManager.show("✨ 番剧剧集与刮削已更新", ToastType.Success)
                } else {
                    GlobalToastManager.show("刷新失败，请检查 WebDAV 连接", ToastType.Error)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                GlobalToastManager.show("刷新异常: ${e.localizedMessage}", ToastType.Error)
            } finally {
                _isRefreshingSingleAnime.value = null
            }
        }
    }

    fun triggerEnrichment() {
        viewModelScope.launch(Dispatchers.IO) {
            AnimeWebDavScanner.enrichAnimeMetadataInBackground(animeDao, getApplication())
        }
    }

    fun applyScrapedMetadata(animeId: Long, scraped: com.example.epubreader.data.anime.ScrapedAnimeInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val anime = animeDao.getAnimeById(animeId) ?: return@launch
            val localCover = com.example.epubreader.data.anime.AnimeMetadataScraper.downloadCover(
                getApplication(),
                scraped.coverUrl,
                scraped.title
            )

            val updatedAnime = anime.copy(
                title = scraped.title.ifBlank { anime.title },
                originalTitle = scraped.originalTitle.ifBlank { anime.originalTitle },
                coverUrl = scraped.coverUrl,
                localCoverPath = localCover ?: anime.localCoverPath,
                score = scraped.score,
                summary = scraped.summary,
                airDate = scraped.airDate,
                totalEpisodes = if (scraped.totalEpisodes > 0) scraped.totalEpisodes else anime.totalEpisodes,
                updatedAt = System.currentTimeMillis()
            )
            animeDao.updateAnime(updatedAnime)
            GlobalToastManager.show("🎉 已成功匹配《${updatedAnime.title}》", ToastType.Success)
        }
    }

    fun rematchBangumi(animeId: Long, customKeyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val scraped = com.example.epubreader.data.anime.AnimeMetadataScraper.scrape(customKeyword)
            if (scraped != null) {
                val anime = animeDao.getAnimeById(animeId) ?: return@launch
                val localCover = com.example.epubreader.data.anime.AnimeMetadataScraper.downloadCover(
                    getApplication(),
                    scraped.coverUrl,
                    scraped.title
                )

                val updatedAnime = anime.copy(
                    title = scraped.title.ifBlank { anime.title },
                    originalTitle = scraped.originalTitle.ifBlank { anime.originalTitle },
                    coverUrl = scraped.coverUrl,
                    localCoverPath = localCover ?: anime.localCoverPath,
                    score = scraped.score,
                    summary = scraped.summary,
                    airDate = scraped.airDate,
                    totalEpisodes = if (scraped.totalEpisodes > 0) scraped.totalEpisodes else anime.totalEpisodes,
                    updatedAt = System.currentTimeMillis()
                )
                animeDao.updateAnime(updatedAnime)
                GlobalToastManager.show("🎉 已成功匹配《${updatedAnime.title}》", ToastType.Success)
            } else {
                GlobalToastManager.show("未在 Bangumi 或豆瓣找到相关番剧信息", ToastType.Error)
            }
        }
    }

    fun deleteAnime(animeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            animeDao.deleteAnime(animeId)
            GlobalToastManager.show("已从番剧库移除", ToastType.Info)
        }
    }

    fun updateWatchProgress(animeId: Long, episodeId: Long, episodeName: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val isWatched = durationMs > 0 && positionMs >= (durationMs * 0.90f)
            val oldEp = animeDao.getEpisodeById(episodeId)
            animeDao.updateWatchProgress(animeId, episodeId, episodeName, positionMs)
            animeDao.updateEpisodeProgress(episodeId, positionMs, durationMs, isWatched)

            if (isWatched && oldEp?.isWatched != true) {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val stat = animeStatDao.getStat(dateStr, animeId)
                if (stat != null) {
                    animeStatDao.insertOrUpdate(stat.copy(episodesWatched = stat.episodesWatched + 1))
                } else {
                    animeStatDao.insertOrUpdate(
                        com.example.epubreader.data.model.AnimeStatEntity(
                            date = dateStr,
                            minutes = 0,
                            animeId = animeId,
                            episodesWatched = 1
                        )
                    )
                }
            }
        }
    }

    fun deleteAllAnimes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                animeDao.clearAllAnimes()
                GlobalToastManager.show("已清空全部番剧记录", ToastType.Success)
            } catch (e: Exception) {
                GlobalToastManager.show("清空失败: ${e.message}", ToastType.Error)
            }
        }
    }
}
