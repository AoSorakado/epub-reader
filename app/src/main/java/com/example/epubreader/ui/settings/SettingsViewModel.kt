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

    private val _immersiveStatusBar = MutableStateFlow(prefs.getBoolean("immersiveStatusBar", false))
    val immersiveStatusBar: StateFlow<Boolean> = _immersiveStatusBar.asStateFlow()

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
                
                var foundCount = 0

                // Recursive scan function
                suspend fun scanDirectory(path: String, currentFolderName: String?) {
                    val folderLabel = currentFolderName ?: "根目录"
                    _syncMessage.value = "正在扫描 $folderLabel..."
                    com.example.epubreader.ui.components.toast.GlobalToastManager.showSyncing("WebDAV 扫描中: $folderLabel...")
                    val resources = client.listFiles(path)
                    for (res in resources) {
                        if (res.isDirectory) {
                            // The folder name is the res.name
                            scanDirectory(res.path, res.name)
                        } else if (res.name.endsWith(".epub", ignoreCase = true)) {
                            val seriesName = currentFolderName
                            
                            // Check if already in DB
                            val existingBook = bookDao.getBookByFilePath(res.path)
                            if (existingBook != null) {
                                // Skip to avoid duplicates
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
                                title = res.name.removeSuffix(".epub").replace("-", " ").replace("_", " "),
                                author = "Unknown",
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
}
