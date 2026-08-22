package com.example.epubreader.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.AppDatabase
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.network.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.epubreader.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class SyncState {
    IDLE, SYNCING, SUCCESS, ERROR
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        LiquidEffectConfig(
            blurRadius = prefs.getFloat("blurRadius", 16f),
            lensRx = prefs.getFloat("lensRx", 32f),
            lensRy = prefs.getFloat("lensRy", 32f),
            alpha = prefs.getFloat("alpha", 0.4f),
            chromaticAberration = prefs.getBoolean("chromaticAberration", false),
            vibrancy = prefs.getBoolean("vibrancy", true)
        )
    )
    val config: StateFlow<LiquidEffectConfig> = _config.asStateFlow()

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("appTheme", AppTheme.OCEAN_WAVE.name) ?: AppTheme.OCEAN_WAVE.name)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _autoNightMode = MutableStateFlow(prefs.getBoolean("autoNightMode", false))
    val autoNightMode: StateFlow<Boolean> = _autoNightMode.asStateFlow()

    private val _userPreferredDayTheme = MutableStateFlow(
        try {
            val saved = prefs.getString("userPreferredDayTheme", AppTheme.OCEAN_WAVE.name) ?: AppTheme.OCEAN_WAVE.name
            val theme = AppTheme.valueOf(saved)
            if (theme == AppTheme.MIDNIGHT_GLASS) AppTheme.OCEAN_WAVE else theme
        } catch (e: Exception) {
            AppTheme.OCEAN_WAVE
        }
    )

    private val _immersiveStatusBar = MutableStateFlow(prefs.getBoolean("immersiveStatusBar", false))
    val immersiveStatusBar: StateFlow<Boolean> = _immersiveStatusBar.asStateFlow()

    private val _isPerfMonitorEnabled = MutableStateFlow(prefs.getBoolean("perf_monitor_enabled", false))
    val isPerfMonitorEnabled: StateFlow<Boolean> = _isPerfMonitorEnabled.asStateFlow()

    fun setPerfMonitorEnabled(enabled: Boolean) {
        _isPerfMonitorEnabled.value = enabled
        prefs.edit().putBoolean("perf_monitor_enabled", enabled).apply()
    }

    private val readerPrefs = application.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _pageTurnMode = MutableStateFlow(readerPrefs.getInt("pageTurnMode", 0)) // 0: 滚动, 1: 翻页
    val pageTurnMode: StateFlow<Int> = _pageTurnMode.asStateFlow()

    private val _pageAnimStyle = MutableStateFlow(readerPrefs.getInt("pageAnimStyle", 0)) // 0: 仿真, 1: 平移, 2: 覆盖, 3: 淡入, 4: 无
    val pageAnimStyle: StateFlow<Int> = _pageAnimStyle.asStateFlow()

    fun setPageTurnMode(mode: Int) {
        _pageTurnMode.value = mode
        readerPrefs.edit().putInt("pageTurnMode", mode).apply()
    }

    fun setPageAnimStyle(style: Int) {
        _pageAnimStyle.value = style
        readerPrefs.edit().putInt("pageAnimStyle", style).apply()
    }

    private val _isCustomThemeThreeColors = MutableStateFlow(prefs.getBoolean("isCustomThemeThreeColors", false))
    val isCustomThemeThreeColors: StateFlow<Boolean> = _isCustomThemeThreeColors.asStateFlow()

    private val _customColors = MutableStateFlow(
        listOf(
            Color(prefs.getInt("customColor1", android.graphics.Color.parseColor("#80D0C7"))),
            Color(prefs.getInt("customColor2", android.graphics.Color.parseColor("#0093E9"))),
            Color(prefs.getInt("customColor3", android.graphics.Color.parseColor("#F48FB1")))
        )
    )
    val customColors: StateFlow<List<Color>> = _customColors.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    fun getSavedWebDavUrl(): String = prefs.getString("webdav_url", "") ?: ""
    fun getSavedWebDavUser(): String = prefs.getString("webdav_user", "") ?: ""
    fun getSavedWebDavPass(): String = prefs.getString("webdav_pass", "") ?: ""

    fun updateConfig(newConfig: LiquidEffectConfig) {
        _config.value = newConfig
        viewModelScope.launch {
            prefs.edit()
                .putFloat("blurRadius", newConfig.blurRadius)
                .putFloat("lensRx", newConfig.lensRx)
                .putFloat("lensRy", newConfig.lensRy)
                .putFloat("alpha", newConfig.alpha)
                .putBoolean("chromaticAberration", newConfig.chromaticAberration)
                .putBoolean("vibrancy", newConfig.vibrancy)
                .apply()
        }
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("appTheme", theme.name).apply()
        if (theme != AppTheme.MIDNIGHT_GLASS) {
            _userPreferredDayTheme.value = theme
            prefs.edit().putString("userPreferredDayTheme", theme.name).apply()
            val dayReadingTheme = readerPrefs.getInt("userPreferredDayReadingTheme", 0)
            readerPrefs.edit().putInt("themeIndex", dayReadingTheme).apply()
        } else {
            readerPrefs.edit().putInt("themeIndex", 2).apply()
        }
    }

    fun setAutoNightMode(enabled: Boolean) {
        _autoNightMode.value = enabled
        prefs.edit().putBoolean("autoNightMode", enabled).apply()
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isNightTime = (currentHour >= 19 || currentHour < 7)
        if (enabled) {
            if (isNightTime) {
                if (_appTheme.value != AppTheme.MIDNIGHT_GLASS) {
                    _appTheme.value = AppTheme.MIDNIGHT_GLASS
                    prefs.edit().putString("appTheme", AppTheme.MIDNIGHT_GLASS.name).apply()
                }
                readerPrefs.edit().putInt("themeIndex", 2).apply()
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = "🌙 自动夜间模式已开启，已为您切换至暗夜护眼主题~",
                    type = com.example.epubreader.ui.components.toast.ToastType.Info,
                    durationMs = 3000L
                )
            } else {
                if (_appTheme.value == AppTheme.MIDNIGHT_GLASS) {
                    _appTheme.value = _userPreferredDayTheme.value
                    prefs.edit().putString("appTheme", _userPreferredDayTheme.value.name).apply()
                    val dayReadingTheme = readerPrefs.getInt("userPreferredDayReadingTheme", 0)
                    readerPrefs.edit().putInt("themeIndex", dayReadingTheme).apply()
                }
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = "☀️ 自动夜间模式已开启（将在 19:00 - 07:00 自动生效）",
                    type = com.example.epubreader.ui.components.toast.ToastType.Info,
                    durationMs = 3000L
                )
            }
        } else {
            // Revert back to preferred day theme if current theme was forced to midnight glass
            if (_appTheme.value == AppTheme.MIDNIGHT_GLASS) {
                _appTheme.value = _userPreferredDayTheme.value
                prefs.edit().putString("appTheme", _userPreferredDayTheme.value.name).apply()
                val dayReadingTheme = readerPrefs.getInt("userPreferredDayReadingTheme", 0)
                readerPrefs.edit().putInt("themeIndex", dayReadingTheme).apply()
            }
            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                text = "⛅ 自动夜间模式已关闭",
                type = com.example.epubreader.ui.components.toast.ToastType.Info,
                durationMs = 2500L
            )
        }
    }

    fun checkDailyStatus() {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isNightTime = (currentHour >= 19 || currentHour < 7)

        if (_autoNightMode.value) {
            if (isNightTime) {
                if (_appTheme.value != AppTheme.MIDNIGHT_GLASS) {
                    _appTheme.value = AppTheme.MIDNIGHT_GLASS
                    prefs.edit().putString("appTheme", AppTheme.MIDNIGHT_GLASS.name).apply()
                }
                readerPrefs.edit().putInt("themeIndex", 2).apply()
                val nightGreeting = when (currentHour) {
                    in 19..23 -> "🌙 晚上好！已为您切换至暗夜护眼主题，愿好书伴您度过宁静夜晚~"
                    else -> "🌌 夜深了，已为您切换至暗夜护眼主题，愿好书伴您入眠~"
                }
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = nightGreeting,
                    type = com.example.epubreader.ui.components.toast.ToastType.Info,
                    durationMs = 3500L
                )
            } else {
                if (_appTheme.value == AppTheme.MIDNIGHT_GLASS) {
                    val dayTheme = _userPreferredDayTheme.value
                    _appTheme.value = dayTheme
                    prefs.edit().putString("appTheme", dayTheme.name).apply()
                    val dayReadingTheme = readerPrefs.getInt("userPreferredDayReadingTheme", 0)
                    readerPrefs.edit().putInt("themeIndex", dayReadingTheme).apply()
                }
                val dayGreeting = when (currentHour) {
                    in 5..10 -> "🌅 早上好！已为您切换至日间清新主题，新的一天从好书开始~"
                    in 11..13 -> "🌤️ 中午好！已为您切换至日间清新主题，享受惬意阅读时光~"
                    else -> "🍵 下午好！已为您切换至日间清新主题，祝您阅读愉快~"
                }
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = dayGreeting,
                    type = com.example.epubreader.ui.components.toast.ToastType.Info,
                    durationMs = 3500L
                )
            }
        } else {
            // General launch greeting (shown on every app launch)
            val greeting = when (currentHour) {
                in 5..10 -> "🌅 早上好！新的一天，从阅读一本好书开始吧~"
                in 11..13 -> "🌤️ 中午好！享受惬意的阅读时光~"
                in 14..18 -> "🍵 下午好！泡一杯茶，继续未完的篇章吧~"
                in 19..23 -> "🌙 晚上好！在书海中卸下一天的疲惫~"
                else -> "🌌 夜深了，注意护眼，愿好书伴您入眠~"
            }
            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                text = greeting,
                type = com.example.epubreader.ui.components.toast.ToastType.Info,
                durationMs = 3500L
            )
        }
    }

    fun setCustomThemeColors(isThreeColors: Boolean, c1: Color, c2: Color, c3: Color) {
        _isCustomThemeThreeColors.value = isThreeColors
        _customColors.value = listOf(c1, c2, c3)
        prefs.edit()
            .putBoolean("isCustomThemeThreeColors", isThreeColors)
            .putInt("customColor1", c1.toArgb())
            .putInt("customColor2", c2.toArgb())
            .putInt("customColor3", c3.toArgb())
            .apply()
    }

    fun setImmersiveStatusBar(enabled: Boolean) {
        _immersiveStatusBar.value = enabled
        prefs.edit().putBoolean("immersiveStatusBar", enabled).apply()
    }

    fun syncWebDav(url: String, user: String, pass: String) {
        if (_syncState.value == SyncState.SYNCING) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncState.SYNCING
            _syncMessage.value = "正在连接 WebDAV..."
            com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("正在连接 WebDAV 服务器...")
            try {
                // Save credentials for future use
                prefs.edit()
                    .putString("webdav_url", url)
                    .putString("webdav_user", user)
                    .putString("webdav_pass", pass)
                    .apply()

                val client = WebDavClient(url, user, pass)
                val bookDao = AppDatabase.getDatabase(getApplication()).bookDao()
                
                // 1. Auto-cleanup any already duplicated books in local DB
                try {
                    val allBooksInDb = bookDao.getAllBooksList()
                    val grouped = allBooksInDb.groupBy { 
                        "${it.title.trim().lowercase()}_${it.seriesName?.trim()?.lowercase() ?: ""}" 
                    }
                    for ((_, booksWithSameTitle) in grouped) {
                        if (booksWithSameTitle.size > 1) {
                            // Keep the book with highest reading progress, then latest read, then smallest id
                            val sorted = booksWithSameTitle.sortedWith(
                                compareByDescending<BookEntity> { it.totalProgress }
                                    .thenByDescending { it.lastReadTime }
                                    .thenBy { it.id }
                            )
                            for (dup in sorted.drop(1)) {
                                bookDao.deleteBook(dup)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var foundCount = 0

                // Recursive scan function
                suspend fun scanDirectory(path: String, currentFolderName: String?) {
                    val folderLabel = currentFolderName ?: "根目录"
                    _syncMessage.value = "正在扫描 $folderLabel..."
                    com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("WebDAV 扫描中: $folderLabel...")
                    val resources = client.listFiles(path)
                    val allExistingBooks = bookDao.getAllBooksList()

                    for (res in resources) {
                        if (res.isDirectory) {
                            // The folder name is the res.name
                            scanDirectory(res.path, res.name)
                        } else if (res.name.endsWith(".epub", ignoreCase = true)) {
                            // Extract author from folder like "[Author] SeriesName" if present
                            var seriesName: String? = currentFolderName
                            var authorFromFolder: String? = null
                            if (currentFolderName != null) {
                                val folderMatch = Regex("^\\[([^\\]]+)\\]\\s*(.+)$").find(currentFolderName)
                                if (folderMatch != null) {
                                    authorFromFolder = folderMatch.groupValues[1].trim()
                                    seriesName = folderMatch.groupValues[2].trim()
                                }
                            }

                            val cleanTitle = res.name.removeSuffix(".epub")
                                .replace(Regex("\\[(Sakura|GPT|有道|有道翻译|百度|Baidu|日文原文)\\]", RegexOption.IGNORE_CASE), "")
                                .replace("-", " ")
                                .replace("_", " ")
                                .trim()
                            val cleanResPath = res.path.trimEnd('/')
                            val decodedResPath = try { java.net.URLDecoder.decode(cleanResPath, "UTF-8") } catch (e: Exception) { cleanResPath }
                            val resFilename = java.io.File(decodedResPath).name

                            // Comprehensive Check if already in DB (by URL, decoded URL, filename, or title+series)
                            val existingBook = allExistingBooks.firstOrNull { existing ->
                                val cleanExistingPath = existing.filePath.trimEnd('/')
                                val decodedExistingPath = try { java.net.URLDecoder.decode(cleanExistingPath, "UTF-8") } catch (e: Exception) { cleanExistingPath }
                                val existingFilename = java.io.File(decodedExistingPath).name

                                cleanExistingPath == cleanResPath ||
                                decodedExistingPath == decodedResPath ||
                                (existing.isWebDav && existingFilename.equals(resFilename, ignoreCase = true)) ||
                                existing.title.trim().equals(cleanTitle, ignoreCase = true)
                            }

                            if (existingBook != null) {
                                var needsUpdate = false
                                var updatedBook = existingBook
                                if (seriesName != null && existingBook.seriesName != seriesName) {
                                    updatedBook = updatedBook.copy(seriesName = seriesName)
                                    needsUpdate = true
                                }
                                if (authorFromFolder != null && (existingBook.author.isNullOrBlank() || existingBook.author.equals("Unknown", ignoreCase = true) || existingBook.author == "未知作者")) {
                                    updatedBook = updatedBook.copy(author = authorFromFolder)
                                    needsUpdate = true
                                }
                                if (needsUpdate) {
                                    bookDao.updateBook(updatedBook)
                                }
                                continue
                            }

                            // 2. Extract Cover Streamingly
                            _syncMessage.value = "正在提取封面: ${res.name}..."
                            var coverImagePath: String? = null
                            try {
                                client.streamFile(res.path) { inputStream ->
                                    val coverBytes = com.example.epubreader.data.parser.EpubParser.extractCoverOnly(inputStream)
                                    if (coverBytes != null) {
                                        val coversDir = java.io.File(getApplication<Application>().filesDir, "covers").apply { mkdirs() }
                                        val coverFile = java.io.File(coversDir, "webdav_cover_${System.currentTimeMillis()}_${foundCount}.jpg")
                                        coverFile.writeBytes(coverBytes)
                                        coverImagePath = coverFile.absolutePath
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val book = BookEntity(
                                title = cleanTitle,
                                author = authorFromFolder ?: "未知作者",
                                coverImage = coverImagePath,
                                filePath = res.path,
                                isWebDav = true,
                                seriesName = seriesName
                            )
                            bookDao.insertBook(book)
                            foundCount++
                            _syncMessage.value = "已发现 $foundCount 本新书..."
                            com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("WebDAV 同步中: 已导入 $foundCount 本新书...")
                        }
                    }
                }

                scanDirectory("", null)
                val successText = if (foundCount > 0) "WebDAV 同步完成，已导入 $foundCount 本书籍" else "WebDAV 同步完成，藏书已是最新"
                _syncMessage.value = successText
                _syncState.value = SyncState.SUCCESS
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = successText,
                    type = com.example.epubreader.ui.components.toast.ToastType.Success,
                    durationMs = 3000L
                )
                
                // Reset state after 3 seconds
                kotlinx.coroutines.delay(3000)
                _syncState.value = SyncState.IDLE
            } catch (e: Exception) {
                e.printStackTrace()
                val errorText = "WebDAV 同步失败: ${e.localizedMessage ?: "连接超时或认证错误"}"
                _syncMessage.value = errorText
                _syncState.value = SyncState.ERROR
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = errorText,
                    type = com.example.epubreader.ui.components.toast.ToastType.Error,
                    durationMs = 3500L
                )
                kotlinx.coroutines.delay(3000)
                _syncState.value = SyncState.IDLE
            }
        }
    }

    private val _isProgressSyncing = MutableStateFlow(false)
    val isProgressSyncing: StateFlow<Boolean> = _isProgressSyncing.asStateFlow()

    private val _lastProgressSyncTime = MutableStateFlow(prefs.getLong("last_progress_sync_time", 0L))
    val lastProgressSyncTime: StateFlow<Long> = _lastProgressSyncTime.asStateFlow()

    fun syncReadingProgress(customUrl: String? = null, customUser: String? = null, customPass: String? = null) {
        if (_isProgressSyncing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProgressSyncing.value = true
            com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("正在连接 WebDAV 服务器...")
            try {
                val url = customUrl ?: prefs.getString("webdav_url", "") ?: ""
                val user = customUser ?: prefs.getString("webdav_user", "") ?: ""
                val pass = customPass ?: prefs.getString("webdav_pass", "") ?: ""

                if (url.isBlank()) {
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "请先配置 WebDAV 服务器地址与凭据",
                        type = com.example.epubreader.ui.components.toast.ToastType.Error,
                        durationMs = 3500L
                    )
                    _isProgressSyncing.value = false
                    return@launch
                }

                val client = WebDavClient(url, user, pass)
                val bookDao = AppDatabase.getDatabase(getApplication()).bookDao()

                // Step 1: Query local books
                val localBooks = bookDao.getAllBooksList()

                // Step 2: Fetch remote progress json
                com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("正在检索云端进度数据...")
                
                // Ensure directory exists or use root
                try {
                    client.createDirectory("/.epub_reader")
                } catch (ignored: Exception) {}

                val remoteJsonStr = client.getTextFile("/.epub_reader/progress_sync.json")
                    ?: client.getTextFile("/epub_reader_progress.json")

                // Step 3: Parse and merge
                com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("正在合并多端阅读进度...")

                val remoteItemsMap = mutableMapOf<String, org.json.JSONObject>()
                if (!remoteJsonStr.isNullOrBlank()) {
                    try {
                        val rootJson = org.json.JSONObject(remoteJsonStr)
                        val itemsArray = rootJson.optJSONArray("items")
                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                val itemObj = itemsArray.getJSONObject(i)
                                val filePath = itemObj.optString("filePath", "")
                                val title = itemObj.optString("title", "")
                                val key = if (filePath.isNotBlank()) filePath else title
                                if (key.isNotBlank()) {
                                    remoteItemsMap[key] = itemObj
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                var pulledFromCloudCount = 0
                var pushedToCloudCount = 0
                val mergedItemsArray = org.json.JSONArray()
                val processedKeys = mutableSetOf<String>()

                for (localBook in localBooks) {
                    val keyByPath = localBook.filePath
                    val keyByTitle = localBook.title
                    val remoteObj = remoteItemsMap[keyByPath] ?: remoteItemsMap[keyByTitle]

                    if (remoteObj != null) {
                        val remoteKey = if (remoteItemsMap.containsKey(keyByPath)) keyByPath else keyByTitle
                        processedKeys.add(remoteKey)

                        val remoteLastReadTime = remoteObj.optLong("lastReadTime", 0L)
                        val remotePos = remoteObj.optString("lastReadPosition", "")
                        val remoteProgress = remoteObj.optDouble("totalProgress", 0.0).toFloat()

                        if (remoteLastReadTime > localBook.lastReadTime && remotePos.isNotBlank()) {
                            // Cloud is newer -> update local DB
                            val updatedBook = localBook.copy(
                                lastReadPosition = remotePos,
                                totalProgress = remoteProgress,
                                lastReadTime = remoteLastReadTime
                            )
                            bookDao.updateBook(updatedBook)
                            pulledFromCloudCount++

                            // Put the remote/updated object into merged array
                            mergedItemsArray.put(remoteObj)
                        } else {
                            // Local is newer or equal -> keep local
                            if (!localBook.lastReadPosition.isNullOrBlank()) {
                                val newObj = org.json.JSONObject().apply {
                                    put("title", localBook.title)
                                    put("author", localBook.author)
                                    put("filePath", localBook.filePath)
                                    put("fileName", localBook.filePath.substringAfterLast("/"))
                                    put("lastReadPosition", localBook.lastReadPosition ?: "")
                                    put("totalProgress", localBook.totalProgress.toDouble())
                                    put("lastReadTime", localBook.lastReadTime)
                                }
                                mergedItemsArray.put(newObj)
                                if (localBook.lastReadTime > remoteLastReadTime) {
                                    pushedToCloudCount++
                                }
                            } else {
                                mergedItemsArray.put(remoteObj)
                            }
                        }
                    } else {
                        // Not yet in cloud: if local has read progress, upload it
                        if (!localBook.lastReadPosition.isNullOrBlank()) {
                            val newObj = org.json.JSONObject().apply {
                                put("title", localBook.title)
                                put("author", localBook.author)
                                put("filePath", localBook.filePath)
                                put("fileName", localBook.filePath.substringAfterLast("/"))
                                put("lastReadPosition", localBook.lastReadPosition ?: "")
                                put("totalProgress", localBook.totalProgress.toDouble())
                                put("lastReadTime", localBook.lastReadTime)
                            }
                            mergedItemsArray.put(newObj)
                            pushedToCloudCount++
                        }
                    }
                }

                // Add any remaining remote items that local doesn't have yet
                for ((k, obj) in remoteItemsMap) {
                    if (!processedKeys.contains(k)) {
                        mergedItemsArray.put(obj)
                    }
                }

                // Step 4: Upload merged payload back to cloud
                com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("正在上传最新合并进度至云端...")
                val outputJson = org.json.JSONObject().apply {
                    put("version", 1)
                    put("lastSyncTime", System.currentTimeMillis())
                    put("items", mergedItemsArray)
                }

                val jsonContent = outputJson.toString(2)
                var (uploadSuccess, uploadError) = client.uploadTextFile("/epub_reader_progress.json", jsonContent)
                if (!uploadSuccess) {
                    val res2 = client.uploadTextFile("/.epub_reader/progress_sync.json", jsonContent)
                    uploadSuccess = res2.first
                    if (!uploadSuccess) {
                        uploadError = res2.second ?: uploadError
                    }
                }

                if (!uploadSuccess) {
                    throw Exception("云端写入失败 (${uploadError ?: "请检查 WebDAV 写入权限"})")
                }

                val now = System.currentTimeMillis()
                prefs.edit().putLong("last_progress_sync_time", now).apply()
                _lastProgressSyncTime.value = now

                // Step 5: Success Toast
                val resultText = if (pulledFromCloudCount > 0 && pushedToCloudCount > 0) {
                    "🎉 进度同步成功：云端拉取 $pulledFromCloudCount 本，上传 $pushedToCloudCount 本"
                } else if (pulledFromCloudCount > 0) {
                    "🎉 进度同步成功：已从云端同步 $pulledFromCloudCount 本书的最新进度"
                } else if (pushedToCloudCount > 0) {
                    "🎉 进度同步成功：已上传 $pushedToCloudCount 本书的最新进度至云端"
                } else {
                    "🎉 进度同步完成：多端进度已是最新"
                }

                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = resultText,
                    type = com.example.epubreader.ui.components.toast.ToastType.Success,
                    durationMs = 4000L
                )
            } catch (e: Exception) {
                e.printStackTrace()
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = "❌ 进度同步失败: ${e.localizedMessage ?: "网络异常或写入权限受限"}",
                    type = com.example.epubreader.ui.components.toast.ToastType.Error,
                    durationMs = 4000L
                )
            } finally {
                _isProgressSyncing.value = false
            }
        }
    }

    // --- Anime WebDAV Settings ---
    private val _animeWebDavUrl = MutableStateFlow(prefs.getString("anime_webdav_url", "") ?: "")
    val animeWebDavUrl: StateFlow<String> = _animeWebDavUrl.asStateFlow()

    private val _animeWebDavUser = MutableStateFlow(prefs.getString("anime_webdav_user", "") ?: "")
    val animeWebDavUser: StateFlow<String> = _animeWebDavUser.asStateFlow()

    private val _animeWebDavPass = MutableStateFlow(prefs.getString("anime_webdav_pass", "") ?: "")
    val animeWebDavPass: StateFlow<String> = _animeWebDavPass.asStateFlow()

    fun saveAnimeWebDavConfig(url: String, user: String, pass: String) {
        _animeWebDavUrl.value = url.trim()
        _animeWebDavUser.value = user.trim()
        _animeWebDavPass.value = pass
        prefs.edit()
            .putString("anime_webdav_url", url.trim())
            .putString("anime_webdav_user", user.trim())
            .putString("anime_webdav_pass", pass)
            .apply()
    }

    fun getEffectiveAnimeWebDavClient(): WebDavClient? {
        val url = _animeWebDavUrl.value.ifBlank { getSavedWebDavUrl() }
        val user = _animeWebDavUser.value.ifBlank { getSavedWebDavUser() }
        val pass = if (_animeWebDavPass.value.isNotBlank()) _animeWebDavPass.value else getSavedWebDavPass()
        return if (url.isNotBlank()) {
            WebDavClient(url, user, pass)
        } else null
    }
}
