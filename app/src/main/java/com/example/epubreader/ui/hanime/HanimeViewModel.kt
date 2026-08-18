package com.example.epubreader.ui.hanime

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.hanime.HanimeComment
import com.example.epubreader.data.hanime.HanimeHomePage
import com.example.epubreader.data.hanime.HanimeInfo
import com.example.epubreader.data.hanime.HanimeRepository
import com.example.epubreader.data.hanime.HanimeSearchFilter
import com.example.epubreader.data.hanime.HanimeVideo
import com.example.epubreader.data.hanime.SearchOptionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class HanimePageState<out T> {
    object Idle : HanimePageState<Nothing>()
    object Loading : HanimePageState<Nothing>()
    data class Success<T>(val data: T) : HanimePageState<T>()
    data class Error(val message: String) : HanimePageState<Nothing>()
}

class HanimeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HanimeRepository(application)

    // 1. Home Page State
    private val _homePageState = MutableStateFlow<HanimePageState<HanimeHomePage>>(HanimePageState.Loading)
    val homePageState: StateFlow<HanimePageState<HanimeHomePage>> = _homePageState.asStateFlow()

    private val _isRefreshingHome = MutableStateFlow(false)
    val isRefreshingHome: StateFlow<Boolean> = _isRefreshingHome.asStateFlow()

    // 2. Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(HanimeSearchFilter())
    val searchFilter: StateFlow<HanimeSearchFilter> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val searchResults: StateFlow<List<HanimeInfo>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _canLoadMoreSearch = MutableStateFlow(true)
    val canLoadMoreSearch: StateFlow<Boolean> = _canLoadMoreSearch.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Preloaded filter options
    val genres: List<SearchOptionItem> by lazy { repository.loadGenres() }
    val sortOptions: List<SearchOptionItem> by lazy { repository.loadSortOptions() }
    val durationOptions: List<SearchOptionItem> by lazy { repository.loadDurationOptions() }
    val releaseDateOptions: List<SearchOptionItem> by lazy { repository.loadReleaseDateOptions() }
    val tags: List<String> by lazy { repository.loadTags() }

    // 3. Video Detail State
    private val _selectedVideoCode = MutableStateFlow<String?>(null)
    val selectedVideoCode: StateFlow<String?> = _selectedVideoCode.asStateFlow()

    private val _selectedVideoDetail = MutableStateFlow<HanimeVideo?>(null)
    val selectedVideoDetail: StateFlow<HanimeVideo?> = _selectedVideoDetail.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    private val _comments = MutableStateFlow<List<HanimeComment>>(emptyList())
    val comments: StateFlow<List<HanimeComment>> = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    // 4. Online Video Playback State (Triggers full screen player overlay)
    var activePlayingVideo by mutableStateOf<HanimeVideo?>(null)
    var activePlayingResolution by mutableStateOf("1080P")
    var currentEpisodeIndex by mutableIntStateOf(0)

    init {
        loadHomePage()
        refreshSearchHistory()
    }

    fun loadHomePage(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshingHome.value = true
            } else if (_homePageState.value !is HanimePageState.Success) {
                _homePageState.value = HanimePageState.Loading
            }

            val result = repository.getHomePage(forceRefresh)
            if (result.isSuccess) {
                _homePageState.value = HanimePageState.Success(result.getOrThrow())
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "加载首页失败"
                if (_homePageState.value !is HanimePageState.Success) {
                    _homePageState.value = HanimePageState.Error(errorMsg)
                }
            }
            _isRefreshingHome.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: HanimeSearchFilter) {
        _searchFilter.value = filter
    }

    fun refreshSearchHistory() {
        _searchHistory.value = repository.getSearchHistory()
    }

    fun removeSearchHistoryItem(item: String) {
        repository.removeSearchHistoryItem(item)
        refreshSearchHistory()
    }

    fun clearSearchHistory() {
        repository.clearSearchHistory()
        refreshSearchHistory()
    }

    fun performSearch(
        query: String? = _searchQuery.value,
        genre: String? = _searchFilter.value.genre,
        sort: String? = _searchFilter.value.sort,
        tags: Set<String> = _searchFilter.value.tags,
        date: String? = _searchFilter.value.date,
        duration: String? = _searchFilter.value.duration,
        isLoadMore: Boolean = false
    ) {
        val newPage = if (isLoadMore) _searchFilter.value.page + 1 else 1
        val newFilter = _searchFilter.value.copy(
            query = query,
            genre = genre,
            sort = sort,
            tags = tags,
            date = date,
            duration = duration,
            page = newPage
        )
        _searchFilter.value = newFilter

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null

            val result = repository.searchVideos(newFilter)
            if (result.isSuccess) {
                val list = result.getOrDefault(emptyList())
                if (isLoadMore) {
                    _searchResults.value = (_searchResults.value + list).distinctBy { it.videoCode }
                } else {
                    _searchResults.value = list.distinctBy { it.videoCode }
                }
                _canLoadMoreSearch.value = list.isNotEmpty()
                refreshSearchHistory()
            } else {
                _searchError.value = result.exceptionOrNull()?.localizedMessage ?: "搜索失败"
                if (!isLoadMore) {
                    _searchResults.value = emptyList()
                }
            }
            _isSearching.value = false
        }
    }

    fun openVideoDetail(videoCode: String) {
        _selectedVideoCode.value = videoCode
        _selectedVideoDetail.value = null
        _comments.value = emptyList()
        _isLoadingDetail.value = true

        viewModelScope.launch {
            val result = repository.getVideoDetail(videoCode)
            if (result.isSuccess) {
                val detail = result.getOrNull()
                _selectedVideoDetail.value = detail
                // Set default resolution
                detail?.let {
                    activePlayingResolution = if (it.videoUrls.containsKey("1080P")) "1080P"
                    else if (it.videoUrls.containsKey("720P")) "720P"
                    else it.videoUrls.keys.firstOrNull() ?: "720P"
                }
                loadComments(videoCode)
            }
            _isLoadingDetail.value = false
        }
    }

    fun closeVideoDetail() {
        _selectedVideoCode.value = null
        _selectedVideoDetail.value = null
    }

    fun loadComments(videoCode: String) {
        viewModelScope.launch {
            _isLoadingComments.value = true
            val result = repository.getVideoComments(videoCode)
            if (result.isSuccess) {
                _comments.value = result.getOrDefault(emptyList())
            }
            _isLoadingComments.value = false
        }
    }

    fun startPlaying(video: HanimeVideo, resolution: String? = null, epIndex: Int = 0) {
        val res = resolution ?: if (video.videoUrls.containsKey("1080P")) "1080P"
        else if (video.videoUrls.containsKey("720P")) "720P"
        else video.videoUrls.keys.firstOrNull() ?: "720P"

        activePlayingResolution = res
        currentEpisodeIndex = epIndex
        activePlayingVideo = video
        closeVideoDetail()
    }

    fun playPlaylistEpisode(episodeVideoCode: String, epIndex: Int) {
        viewModelScope.launch {
            val result = repository.getVideoDetail(episodeVideoCode)
            if (result.isSuccess) {
                val video = result.getOrNull() ?: return@launch
                _selectedVideoDetail.value = video
                startPlaying(video, activePlayingResolution, epIndex)
            }
        }
    }

    fun playNextEpisode() {
        val currentVideo = activePlayingVideo ?: return
        val episodes = currentVideo.playlist?.episodes ?: return
        val nextIdx = currentEpisodeIndex + 1
        if (nextIdx < episodes.size) {
            val nextEp = episodes[nextIdx]
            playPlaylistEpisode(nextEp.videoCode, nextIdx)
        }
    }

    fun switchResolution(newResolution: String) {
        activePlayingResolution = newResolution
    }

    fun exitPlaying() {
        activePlayingVideo = null
    }
}
