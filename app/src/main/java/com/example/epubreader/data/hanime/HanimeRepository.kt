package com.example.epubreader.data.hanime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class HanimeRepository(private val context: Context) {

    companion object {
        private const val TAG = "HanimeRepository"
        private const val PREFS_NAME = "hanime_repository_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_SELECTED_DOMAIN = "selected_domain"
    }

    private val apiClient = HanimeApiClient(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentDomain: String
        get() = prefs.getString(KEY_SELECTED_DOMAIN, HanimeApiClient.DEFAULT_BASE_URL) ?: HanimeApiClient.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_SELECTED_DOMAIN, value).apply()

    private var cachedHomePage: HanimeHomePage? = null

    suspend fun getHomePage(forceRefresh: Boolean = false): Result<HanimeHomePage> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedHomePage != null) {
            return@withContext Result.success(cachedHomePage!!)
        }

        // Try primary domain first, then fallback to backups if failed
        val domainsToTry = listOf(currentDomain) + HanimeApiClient.BACKUP_BASE_URLS.filter { it != currentDomain }

        var lastError: Throwable? = null
        for (domain in domainsToTry) {
            val result = apiClient.getHomePage(domain)
            if (result.isSuccess) {
                cachedHomePage = result.getOrNull()
                currentDomain = domain
                return@withContext result
            } else {
                lastError = result.exceptionOrNull()
                Log.w(TAG, "Domain $domain failed, trying next mirror...", lastError)
            }
        }

        Result.failure(lastError ?: Exception("All Hanime mirrors failed"))
    }

    suspend fun getVideoDetail(videoCode: String): Result<HanimeVideo> = withContext(Dispatchers.IO) {
        val domainsToTry = listOf(currentDomain) + HanimeApiClient.BACKUP_BASE_URLS.filter { it != currentDomain }
        var lastError: Throwable? = null

        for (domain in domainsToTry) {
            val result = apiClient.getVideo(videoCode, domain)
            if (result.isSuccess) {
                return@withContext result
            } else {
                lastError = result.exceptionOrNull()
            }
        }
        Result.failure(lastError ?: Exception("Failed to fetch video $videoCode"))
    }

    suspend fun searchVideos(filter: HanimeSearchFilter): Result<List<HanimeInfo>> = withContext(Dispatchers.IO) {
        val domainsToTry = listOf(currentDomain) + HanimeApiClient.BACKUP_BASE_URLS.filter { it != currentDomain }
        var lastError: Throwable? = null

        for (domain in domainsToTry) {
            val result = apiClient.search(filter, domain)
            if (result.isSuccess) {
                if (!filter.query.isNullOrBlank()) {
                    addSearchHistory(filter.query)
                }
                return@withContext result
            } else {
                lastError = result.exceptionOrNull()
            }
        }
        Result.failure(lastError ?: Exception("Search failed"))
    }

    suspend fun getVideoComments(videoCode: String, page: Int = 1): Result<List<HanimeComment>> = withContext(Dispatchers.IO) {
        apiClient.getComments(videoCode, page, currentDomain)
    }

    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getSearchHistory().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = current.take(20)
        prefs.edit().putString(KEY_SEARCH_HISTORY, trimmedList.joinToString("\n")).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun removeSearchHistoryItem(item: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(item)
        prefs.edit().putString(KEY_SEARCH_HISTORY, current.joinToString("\n")).apply()
    }

    fun loadGenres(): List<SearchOptionItem> {
        return loadSearchOptionJson("hanime/genre.json")
    }

    fun loadSortOptions(): List<SearchOptionItem> {
        return loadSearchOptionJson("hanime/sort_option.json")
    }

    fun loadDurationOptions(): List<SearchOptionItem> {
        return loadSearchOptionJson("hanime/duration.json")
    }

    fun loadReleaseDateOptions(): List<SearchOptionItem> {
        return loadSearchOptionJson("hanime/release_date.json")
    }

    fun loadTags(): List<String> {
        return try {
            val jsonStr = context.assets.open("hanime/tags.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val searchKey = obj.optString("search_key", "")
                if (searchKey.isNotBlank()) list.add(searchKey)
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tags", e)
            emptyList()
        }
    }

    private fun loadSearchOptionJson(assetPath: String): List<SearchOptionItem> {
        return try {
            val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<SearchOptionItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val langObj = obj.optJSONObject("lang")
                val name = langObj?.optString("zh-rCN")
                    ?: langObj?.optString("zh-rTW")
                    ?: langObj?.optString("en")
                    ?: obj.optString("search_key", "")
                val searchKey = obj.optString("search_key", name)
                if (name.isNotBlank()) {
                    list.add(SearchOptionItem(name = name, searchKey = searchKey))
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading options from $assetPath", e)
            emptyList()
        }
    }
}
