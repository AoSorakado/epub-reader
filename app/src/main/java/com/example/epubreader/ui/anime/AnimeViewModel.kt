package com.example.epubreader.ui.anime

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.anime.AnimeWebDavScanner
import com.example.epubreader.data.anime.BangumiApiClient
import com.example.epubreader.data.anime.BangumiSubject
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

    val animes: StateFlow<List<AnimeEntity>> = animeDao.getAllAnimes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterStatus = MutableStateFlow(0) // 0: All, 1: Watching, 2: Finished
    val filterStatus: StateFlow<Int> = _filterStatus.asStateFlow()

    private val _sortType = MutableStateFlow(0) // 0: Updated, 1: Score, 2: Name
    val sortType: StateFlow<Int> = _sortType.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    val filteredAnimes: StateFlow<List<AnimeEntity>> = combine(
        animes,
        _searchQuery,
        _filterStatus,
        _sortType
    ) { list, query, filter, sort ->
        var res = list
        if (query.isNotBlank()) {
            res = res.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.originalTitle?.contains(query, ignoreCase = true) == true 
            }
        }
        when (filter) {
            1 -> res = res.filter { !it.isFinished && it.lastWatchTimeMs > 0 }
            2 -> res = res.filter { it.isFinished }
        }
        when (sort) {
            0 -> res.sortedByDescending { it.updatedAt }
            1 -> res.sortedByDescending { it.score }
            2 -> res.sortedBy { it.title }
            else -> res
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterStatus(status: Int) {
        _filterStatus.value = status
    }

    fun setSortType(sort: Int) {
        _sortType.value = sort
    }

    fun scanWebDav(webDavClient: WebDavClient) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = "正在连接 WebDAV 媒体库..."
        GlobalToastManager.show("🚀 正在扫描 WebDAV 媒体库...", ToastType.Info)

        viewModelScope.launch {
            try {
                val count = AnimeWebDavScanner.scanAnimeDirectory(
                    webDavClient = webDavClient,
                    animeDao = animeDao,
                    context = getApplication(),
                    onProgress = { current, total, name ->
                        _scanProgress.value = "正在解析 [$current/$total] $name"
                    }
                )
                if (count > 0) {
                    GlobalToastManager.show("✨ 扫描完成，已收录 $count 部番剧", ToastType.Success)
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

    fun rematchBangumi(animeId: Long, customKeyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val subject = BangumiApiClient.searchSubject(customKeyword)
            if (subject != null) {
                val anime = animeDao.getAnimeById(animeId) ?: return@launch
                
                var localCoverPath = anime.localCoverPath
                if (subject.coverLarge.isNotBlank()) {
                    try {
                        val coversDir = File(getApplication<Application>().filesDir, "anime_covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        val coverFile = File(coversDir, "anime_${System.currentTimeMillis()}_${subject.id}.jpg")
                        val input = URL(subject.coverLarge).openStream()
                        val output = FileOutputStream(coverFile)
                        input.use { inp -> output.use { out -> inp.copyTo(out) } }
                        localCoverPath = coverFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val updatedAnime = anime.copy(
                    title = subject.nameCn.ifBlank { subject.name },
                    originalTitle = subject.name,
                    coverUrl = subject.coverLarge,
                    localCoverPath = localCoverPath,
                    bangumiId = subject.id,
                    score = subject.score,
                    summary = subject.summary,
                    airDate = subject.airDate,
                    totalEpisodes = if (subject.epsCount > 0) subject.epsCount else anime.totalEpisodes,
                    updatedAt = System.currentTimeMillis()
                )
                animeDao.updateAnime(updatedAnime)
                GlobalToastManager.show("🎉 已成功匹配《${updatedAnime.title}》", ToastType.Success)
            } else {
                GlobalToastManager.show("未找到相关番剧信息", ToastType.Error)
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
            animeDao.updateWatchProgress(animeId, episodeId, episodeName, positionMs)
            animeDao.updateEpisodeProgress(episodeId, positionMs, durationMs, isWatched)
        }
    }
}
