package com.example.epubreader.ui.novelia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.novelia.NoveliaApiClient
import com.example.epubreader.data.novelia.NoveliaCategory
import com.example.epubreader.data.novelia.NoveliaChapter
import com.example.epubreader.data.novelia.NoveliaDownloadTask
import com.example.epubreader.data.novelia.NoveliaFolder
import com.example.epubreader.data.novelia.NoveliaSearchFilter
import com.example.epubreader.data.novelia.NoveliaUserSession
import com.example.epubreader.data.novelia.NoveliaViewMode
import com.example.epubreader.data.novelia.NoveliaVolume
import com.example.epubreader.data.novelia.NoveliaWebNovel
import com.example.epubreader.data.novelia.NoveliaWenkuNovel
import com.example.epubreader.data.novelia.TranslationEngine
import com.example.epubreader.data.novelia.sync.NoveliaWebDavExporter
import com.example.epubreader.ui.components.toast.GlobalToastManager
import com.example.epubreader.ui.components.toast.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoveliaViewModel(application: Application) : AndroidViewModel(application) {

    val apiClient = NoveliaApiClient(application)
    val webDavExporter = NoveliaWebDavExporter(application, apiClient)

    private val _viewMode = MutableStateFlow(NoveliaViewMode.BROWSE)
    val viewMode: StateFlow<NoveliaViewMode> = _viewMode.asStateFlow()

    private val _searchFilter = MutableStateFlow(NoveliaSearchFilter())
    val searchFilter: StateFlow<NoveliaSearchFilter> = _searchFilter.asStateFlow()

    private val _wenkuNovels = MutableStateFlow<List<NoveliaWenkuNovel>>(emptyList())
    val wenkuNovels: StateFlow<List<NoveliaWenkuNovel>> = _wenkuNovels.asStateFlow()

    private val _webNovels = MutableStateFlow<List<NoveliaWebNovel>>(emptyList())
    val webNovels: StateFlow<List<NoveliaWebNovel>> = _webNovels.asStateFlow()

    private val _favoredWenkuNovels = MutableStateFlow<List<NoveliaWenkuNovel>>(emptyList())
    val favoredWenkuNovels: StateFlow<List<NoveliaWenkuNovel>> = _favoredWenkuNovels.asStateFlow()

    private val _favoredWebNovels = MutableStateFlow<List<NoveliaWebNovel>>(emptyList())
    val favoredWebNovels: StateFlow<List<NoveliaWebNovel>> = _favoredWebNovels.asStateFlow()

    private val _favoriteFolders = MutableStateFlow<List<NoveliaFolder>>(listOf(NoveliaFolder("default", "默认收藏夹", "wenku")))
    val favoriteFolders: StateFlow<List<NoveliaFolder>> = _favoriteFolders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    private val _selectedWenkuNovel = MutableStateFlow<NoveliaWenkuNovel?>(null)
    val selectedWenkuNovel: StateFlow<NoveliaWenkuNovel?> = _selectedWenkuNovel.asStateFlow()

    private val _selectedWebNovel = MutableStateFlow<NoveliaWebNovel?>(null)
    val selectedWebNovel: StateFlow<NoveliaWebNovel?> = _selectedWebNovel.asStateFlow()

    private val _activeDownloadTask = MutableStateFlow<NoveliaDownloadTask?>(null)
    val activeDownloadTask: StateFlow<NoveliaDownloadTask?> = _activeDownloadTask.asStateFlow()

    private val _userSession = MutableStateFlow(apiClient.getUserSession())
    val userSession: StateFlow<NoveliaUserSession> = _userSession.asStateFlow()

    init {
        // Attempt background token refresh if cookies exist
        viewModelScope.launch {
            if (_userSession.value.cookies.isNotBlank() && !_userSession.value.isLoggedIn) {
                val refreshRes = apiClient.refreshAuthToken()
                refreshRes.onSuccess { session ->
                    _userSession.value = session
                }
            }
            loadNovels(resetPage = true)
        }
    }

    fun setViewMode(mode: NoveliaViewMode) {
        if (_viewMode.value == mode) return
        _viewMode.value = mode
        if (mode == NoveliaViewMode.FAVORITES) {
            loadFavoriteFolders()
            loadFavorites(resetPage = true)
        } else {
            loadNovels(resetPage = true)
        }
    }

    fun setCategory(category: NoveliaCategory) {
        if (_searchFilter.value.category == category) return
        _searchFilter.value = _searchFilter.value.copy(category = category, page = 1)
        if (_viewMode.value == NoveliaViewMode.FAVORITES) {
            loadFavorites(resetPage = true)
        } else {
            loadNovels(resetPage = true)
        }
    }

    fun setKeyword(keyword: String) {
        _searchFilter.value = _searchFilter.value.copy(keyword = keyword, page = 1)
    }

    fun setWenkuLevel(level: Int) {
        _searchFilter.value = _searchFilter.value.copy(wenkuLevel = level, page = 1)
        loadNovels(resetPage = true)
    }

    fun setWebProvider(provider: String) {
        _searchFilter.value = _searchFilter.value.copy(webProvider = provider, page = 1)
        loadNovels(resetPage = true)
    }

    fun setWebType(type: Int) {
        _searchFilter.value = _searchFilter.value.copy(webType = type, page = 1)
        loadNovels(resetPage = true)
    }

    fun setWebLevel(level: Int) {
        _searchFilter.value = _searchFilter.value.copy(webLevel = level, page = 1)
        loadNovels(resetPage = true)
    }

    fun setFavoriteFolder(folderId: String) {
        _searchFilter.value = _searchFilter.value.copy(favoriteFolderId = folderId, page = 1)
        loadFavorites(resetPage = true)
    }

    fun setFavoriteSort(sort: Int) {
        _searchFilter.value = _searchFilter.value.copy(favoriteSort = sort, page = 1)
        loadFavorites(resetPage = true)
    }

    fun loadNovels(resetPage: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            if (resetPage) {
                _searchFilter.value = _searchFilter.value.copy(page = 1)
            }

            val filter = _searchFilter.value
            if (filter.category == NoveliaCategory.WENKU) {
                val result = apiClient.searchWenku(filter)
                result.onSuccess { list ->
                    _wenkuNovels.value = if (resetPage) list else _wenkuNovels.value + list
                }.onFailure { err ->
                    _errorMsg.value = err.message ?: "加载文库小说失败"
                }
            } else {
                val result = apiClient.searchWebNovels(filter)
                result.onSuccess { list ->
                    _webNovels.value = if (resetPage) list else _webNovels.value + list
                }.onFailure { err ->
                    _errorMsg.value = err.message ?: "加载网络小说失败"
                }
            }
            _isLoading.value = false
        }
    }

    fun loadFavoriteFolders() {
        viewModelScope.launch {
            val res = apiClient.getFavoredFolders()
            res.onSuccess { list ->
                if (list.isNotEmpty()) {
                    _favoriteFolders.value = list
                }
            }
        }
    }

    fun loadFavorites(resetPage: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            if (resetPage) {
                _searchFilter.value = _searchFilter.value.copy(page = 1)
            }

            val filter = _searchFilter.value
            val page = (filter.page - 1).coerceAtLeast(0)
            val folderId = filter.favoriteFolderId.ifEmpty { "default" }
            val sort = filter.favoriteSort

            if (filter.category == NoveliaCategory.WENKU) {
                val res = apiClient.getFavoredWenku(folderId, page, sort)
                res.onSuccess { list ->
                    _favoredWenkuNovels.value = if (resetPage) list else _favoredWenkuNovels.value + list
                }.onFailure { err ->
                    _errorMsg.value = err.message ?: "加载收藏文库小说失败"
                }
            } else {
                val res = apiClient.getFavoredWeb(folderId, page, sort)
                res.onSuccess { list ->
                    _favoredWebNovels.value = if (resetPage) list else _favoredWebNovels.value + list
                }.onFailure { err ->
                    _errorMsg.value = err.message ?: "加载收藏网络小说失败"
                }
            }
            _isLoading.value = false
        }
    }

    fun toggleFavoriteWenku(novel: NoveliaWenkuNovel) {
        viewModelScope.launch {
            val folderId = _searchFilter.value.favoriteFolderId.ifEmpty { "default" }
            val isFav = novel.isFavorited
            val res = if (isFav) {
                apiClient.unfavoriteWenku(novel.id, folderId)
            } else {
                apiClient.favoriteWenku(novel.id, folderId)
            }

            res.onSuccess {
                val updated = novel.copy(isFavorited = !isFav)
                _selectedWenkuNovel.value = updated
                _wenkuNovels.value = _wenkuNovels.value.map { if (it.id == novel.id) updated else it }
                if (isFav) {
                    _favoredWenkuNovels.value = _favoredWenkuNovels.value.filter { it.id != novel.id }
                    GlobalToastManager.show("已取消收藏", ToastType.Info)
                } else {
                    _favoredWenkuNovels.value = listOf(updated) + _favoredWenkuNovels.value
                    GlobalToastManager.show("已加入我的收藏", ToastType.Success)
                }
            }.onFailure {
                GlobalToastManager.show("收藏操作失败: ${it.message}", ToastType.Error)
            }
        }
    }

    fun toggleFavoriteWeb(novel: NoveliaWebNovel) {
        viewModelScope.launch {
            val folderId = _searchFilter.value.favoriteFolderId.ifEmpty { "default" }
            val isFav = novel.isFavorited
            val res = if (isFav) {
                apiClient.unfavoriteWebNovel(novel.sourcePlatform, novel.id, folderId)
            } else {
                apiClient.favoriteWebNovel(novel.sourcePlatform, novel.id, folderId)
            }

            res.onSuccess {
                val updated = novel.copy(isFavorited = !isFav)
                _selectedWebNovel.value = updated
                _webNovels.value = _webNovels.value.map { if (it.id == novel.id) updated else it }
                if (isFav) {
                    _favoredWebNovels.value = _favoredWebNovels.value.filter { it.id != novel.id }
                    GlobalToastManager.show("已取消收藏", ToastType.Info)
                } else {
                    _favoredWebNovels.value = listOf(updated) + _favoredWebNovels.value
                    GlobalToastManager.show("已加入我的收藏", ToastType.Success)
                }
            }.onFailure {
                GlobalToastManager.show("收藏操作失败: ${it.message}", ToastType.Error)
            }
        }
    }

    fun loadMore() {
        if (_isLoading.value) return
        _searchFilter.value = _searchFilter.value.copy(page = _searchFilter.value.page + 1)
        if (_viewMode.value == NoveliaViewMode.FAVORITES) {
            loadFavorites(resetPage = false)
        } else {
            loadNovels(resetPage = false)
        }
    }

    fun openWenkuDetail(novel: NoveliaWenkuNovel) {
        viewModelScope.launch {
            _selectedWenkuNovel.value = novel
            val detailRes = apiClient.getWenkuDetail(novel.id)
            detailRes.onSuccess { fullNovel ->
                _selectedWenkuNovel.value = fullNovel.copy(isFavorited = novel.isFavorited)
            }
        }
    }

    fun closeWenkuDetail() {
        _selectedWenkuNovel.value = null
    }

    fun openWebNovelDetail(novel: NoveliaWebNovel) {
        viewModelScope.launch {
            _selectedWebNovel.value = novel
            val detailRes = apiClient.getWebNovelDetail(novel.id)
            detailRes.onSuccess { fullNovel ->
                _selectedWebNovel.value = fullNovel.copy(isFavorited = novel.isFavorited)
            }
        }
    }

    fun closeWebNovelDetail() {
        _selectedWebNovel.value = null
    }

    fun saveUserSession(session: NoveliaUserSession) {
        apiClient.saveUserSession(session)
        _userSession.value = session
        viewModelScope.launch {
            // Exchange for live JWT token
            val refreshRes = apiClient.refreshAuthToken()
            refreshRes.onSuccess { fullSession ->
                _userSession.value = fullSession
                GlobalToastManager.show("登录成功: ${fullSession.username}", ToastType.Success)
            }
            if (_viewMode.value == NoveliaViewMode.FAVORITES) {
                loadFavorites(resetPage = true)
            } else {
                loadNovels(resetPage = true)
            }
        }
    }

    fun logout() {
        apiClient.logout()
        _userSession.value = NoveliaUserSession()
        _favoredWenkuNovels.value = emptyList()
        _favoredWebNovels.value = emptyList()
        if (_viewMode.value == NoveliaViewMode.FAVORITES) {
            loadFavorites(resetPage = true)
        } else {
            loadNovels(resetPage = true)
        }
        GlobalToastManager.show("已退出登录", ToastType.Info)
    }

    /**
     * Download single Wenku volume to WebDAV
     */
    fun downloadWenkuVolume(
        novel: NoveliaWenkuNovel,
        volume: NoveliaVolume,
        engine: TranslationEngine = TranslationEngine.SAKURA
    ) {
        if (_activeDownloadTask.value != null && !_activeDownloadTask.value!!.isCompleted) {
            GlobalToastManager.show("已有下载任务正在进行中", ToastType.Info)
            return
        }

        val task = NoveliaDownloadTask(
            novelId = novel.id,
            novelTitle = novel.title,
            author = novel.author,
            category = NoveliaCategory.WENKU,
            volumeOrChapterTitle = "第${volume.volumeIndex}卷 ${volume.volumeName}",
            engine = engine,
            progress = 0.05f,
            statusText = "准备下载第${volume.volumeIndex}卷..."
        )
        _activeDownloadTask.value = task

        viewModelScope.launch {
            GlobalToastManager.showSyncing("开始下载 ${novel.title} 第${volume.volumeIndex}卷")
            val res = webDavExporter.exportWenkuVolume(novel, volume, engine) { progress, status ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    progress = progress,
                    statusText = status
                )
            }

            res.onSuccess { remotePath ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    progress = 1.0f,
                    statusText = "第${volume.volumeIndex}卷已归档至 WebDAV",
                    isCompleted = true
                )
                GlobalToastManager.show("第${volume.volumeIndex}卷已保存至 WebDAV", ToastType.Success)
            }.onFailure { err ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    statusText = "第${volume.volumeIndex}卷下载失败: ${err.message}",
                    error = err.message,
                    isCompleted = true
                )
                GlobalToastManager.show("第${volume.volumeIndex}卷下载失败: ${err.message}", ToastType.Error)
            }
        }
    }

    /**
     * Download all Wenku volumes in batch
     */
    fun downloadAllWenkuVolumes(
        novel: NoveliaWenkuNovel,
        engine: TranslationEngine = TranslationEngine.SAKURA
    ) {
        if (novel.volumes.isEmpty()) {
            GlobalToastManager.show("暂无可下载的分卷", ToastType.Info)
            return
        }

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for ((idx, vol) in novel.volumes.withIndex()) {
                val volIndexDisplay = "第 ${idx + 1}/${novel.volumes.size} 卷"
                val task = NoveliaDownloadTask(
                    novelId = novel.id,
                    novelTitle = novel.title,
                    author = novel.author,
                    category = NoveliaCategory.WENKU,
                    volumeOrChapterTitle = "${vol.volumeName} ($volIndexDisplay)",
                    engine = engine,
                    progress = 0.05f,
                    statusText = "正在下载 $volIndexDisplay..."
                )
                _activeDownloadTask.value = task

                val res = webDavExporter.exportWenkuVolume(novel, vol, engine) { progress, status ->
                    _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                        progress = (idx.toFloat() + progress) / novel.volumes.size.toFloat(),
                        statusText = "($volIndexDisplay) $status"
                    )
                }

                if (res.isSuccess) {
                    successCount++
                } else {
                    failCount++
                    GlobalToastManager.show("$volIndexDisplay 下载失败: ${res.exceptionOrNull()?.message}", ToastType.Error)
                }
            }

            _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                progress = 1.0f,
                statusText = "全系列下载完成 (成功 $successCount 卷, 失败 $failCount 卷)",
                isCompleted = true
            )
            GlobalToastManager.show("${novel.title} 全系列已归档至 WebDAV (共 $successCount 卷)", ToastType.Success)
        }
    }

    /**
     * Download Web novel (fetch all chapters, generate EPUB, upload to WebDAV)
     */
    fun downloadWebNovel(
        novel: NoveliaWebNovel,
        engine: TranslationEngine = TranslationEngine.SAKURA
    ) {
        if (_activeDownloadTask.value != null && !_activeDownloadTask.value!!.isCompleted) {
            GlobalToastManager.show("已有下载任务正在进行中", ToastType.Info)
            return
        }

        val task = NoveliaDownloadTask(
            novelId = novel.id,
            novelTitle = novel.title,
            author = novel.author,
            category = NoveliaCategory.WEB_NOVEL,
            volumeOrChapterTitle = "全本章节",
            sourcePlatform = novel.sourcePlatform,
            engine = engine,
            progress = 0f,
            statusText = "正在拉取章节列表..."
        )
        _activeDownloadTask.value = task

        viewModelScope.launch {
            GlobalToastManager.showSyncing("正在拉取 ${novel.title} 章节...")
            val chaptersRes = apiClient.getWebNovelChapters(novel.id, engine) { p ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    progress = p * 0.3f,
                    statusText = "正在拉取章节: ${(p * 100).toInt()}%"
                )
            }

            val chapters = chaptersRes.getOrNull()
            if (chapters.isNullOrEmpty()) {
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    statusText = "获取章节失败或暂无翻译章节",
                    isCompleted = true,
                    error = "无章节内容"
                )
                GlobalToastManager.show("获取章节失败", ToastType.Error)
                return@launch
            }

            val exportRes = webDavExporter.exportWebNovel(novel, chapters, engine) { progress, status ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    progress = 0.3f + progress * 0.7f,
                    statusText = status
                )
            }

            exportRes.onSuccess { remotePath ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    progress = 1.0f,
                    statusText = "已生成 EPUB 并上传 WebDAV",
                    isCompleted = true
                )
                GlobalToastManager.show("已保存至 WebDAV: $remotePath", ToastType.Success)
            }.onFailure { err ->
                _activeDownloadTask.value = _activeDownloadTask.value?.copy(
                    statusText = "打包上传失败: ${err.message}",
                    error = err.message,
                    isCompleted = true
                )
                GlobalToastManager.show("打包上传失败: ${err.message}", ToastType.Error)
            }
        }
    }

    fun dismissActiveDownloadTask() {
        _activeDownloadTask.value = null
    }
}
