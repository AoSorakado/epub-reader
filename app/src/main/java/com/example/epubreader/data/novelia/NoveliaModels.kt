package com.example.epubreader.data.novelia

enum class NoveliaCategory(val displayName: String) {
    WENKU("文库小说"),
    WEB_NOVEL("网络小说")
}

enum class NoveliaViewMode(val displayName: String) {
    BROWSE("书库浏览"),
    FAVORITES("我的收藏")
}

enum class TranslationEngine(val code: String, val displayName: String) {
    SAKURA("sakura", "Sakura"),
    GPT("gpt", "GPT"),
    YOUDAO("youdao", "有道"),
    ORIGINAL("raw", "日文原文")
}

data class NoveliaFolder(
    val id: String,
    val name: String,
    val type: String = "wenku"
)

data class NoveliaWenkuNovel(
    val id: String,
    val title: String,
    val japaneseTitle: String = "",
    val author: String = "",
    val artists: String = "",
    val publisher: String = "",
    val imprint: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val ratingCategory: String = "全年龄",
    val volumes: List<NoveliaVolume> = emptyList(),
    val updateTime: String = "",
    val isFavorited: Boolean = false
)

data class NoveliaVolume(
    val id: String,
    val volumeIndex: Int = 1,
    val volumeName: String,
    val totalChapters: Int = 0,
    val youdaoChapters: Int = 0,
    val gptChapters: Int = 0,
    val sakuraChapters: Int = 0,
    val defaultDownloadUrl: String = "",
    val engineDownloadUrls: Map<TranslationEngine, String> = emptyMap()
)

data class NoveliaWebNovel(
    val id: String,
    val sourcePlatform: String = "Kakuyomu",
    val sourceNovelId: String = "",
    val title: String,
    val japaneseTitle: String = "",
    val author: String = "",
    val artists: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val status: String = "连载中",
    val ratingCategory: String = "一般向",
    val tags: List<String> = emptyList(),
    val totalChapters: Int = 0,
    val youdaoChapters: Int = 0,
    val gptChapters: Int = 0,
    val sakuraChapters: Int = 0,
    val lastUpdated: String = "",
    val isFavorited: Boolean = false
)

data class NoveliaChapter(
    val id: String,
    val chapterIndex: Int,
    val volumeName: String = "正文",
    val title: String,
    val japaneseTitle: String = "",
    val content: String = "",
    val japaneseContent: String? = null
)

data class NoveliaUserSession(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val email: String = "",
    val cookies: String = "",
    val token: String = "",
    val isVip: Boolean = false,
    val hasNsfwAccess: Boolean = false
)

data class NoveliaSearchFilter(
    val keyword: String = "",
    val category: NoveliaCategory = NoveliaCategory.WENKU,
    val wenkuLevel: Int = 0, // 0: 全部, 1: 轻小说, 2: 轻文学, 3: 文学, 4: 非小说, 5: R18男性向, 6: R18女性向
    val webProvider: String = "kakuyomu,syosetu,novelup,hameln,pixiv,alphapolis",
    val webType: Int = 0, // 0: 全部, 1: 连载中, 2: 已完结, 3: 短篇
    val webLevel: Int = 0, // 0: 全部, 1: 一般向, 2: R18
    val webTranslate: Int = 0, // 0: 全部, 1: 已翻译
    val webSort: Int = 0, // 0: 按更新, 1: 按热度
    val page: Int = 1,
    val favoriteFolderId: String = "default",
    val favoriteSort: Int = 0 // 0: 按更新时间, 1: 按收藏时间
)

data class NoveliaDownloadTask(
    val novelId: String,
    val novelTitle: String,
    val author: String,
    val category: NoveliaCategory,
    val volumeOrChapterTitle: String,
    val sourcePlatform: String = "",
    val engine: TranslationEngine = TranslationEngine.SAKURA,
    val progress: Float = 0f,
    val statusText: String = "准备下载...",
    val isCompleted: Boolean = false,
    val error: String? = null
)
