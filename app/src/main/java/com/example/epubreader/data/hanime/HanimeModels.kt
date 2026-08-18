package com.example.epubreader.data.hanime

data class HanimeInfo(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val duration: String = "",
    val views: String = "",
    val artist: String? = null,
    val uploadTime: String? = null,
    val reviews: String? = null,
    val genre: String? = null,
    val isPlaying: Boolean = false
)

data class HanimeBanner(
    val title: String,
    val description: String?,
    val picUrl: String,
    val videoCode: String?
)

data class HanimePlaylist(
    val playlistName: String?,
    val episodes: List<HanimeInfo>
)

data class HanimeVideo(
    val videoCode: String,
    val title: String,
    val chineseTitle: String? = null,
    val coverUrl: String,
    val uploadTime: String? = null,
    val views: String? = null,
    val introduction: String? = null,
    val videoUrls: Map<String, String> = emptyMap(), // Resolution (e.g., "1080P", "720P", "480P") -> Direct Stream URL
    val tags: List<String> = emptyList(),
    val playlist: HanimePlaylist? = null,
    val relatedHanimes: List<HanimeInfo> = emptyList(),
    val artistName: String? = null,
    val artistAvatarUrl: String? = null,
    val artistGenre: String? = null,
    val favTimes: Int? = null,
    val originalComic: String? = null
) {
    /**
     * Get highest available resolution link, or fallback to any available link
     */
    val bestStreamUrl: String?
        get() = videoUrls["1080P"]
            ?: videoUrls["720P"]
            ?: videoUrls["480P"]
            ?: videoUrls["240P"]
            ?: videoUrls.values.firstOrNull()
}

data class HanimeHomePage(
    val banner: HanimeBanner? = null,
    val latestRelease: List<HanimeInfo> = emptyList(),
    val latestUpload: List<HanimeInfo> = emptyList(),
    val hentaiAnime: List<HanimeInfo> = emptyList(),
    val shortAnime: List<HanimeInfo> = emptyList(),
    val motionAnime: List<HanimeInfo> = emptyList(),
    val threeDCG: List<HanimeInfo> = emptyList(),
    val twoPointFiveD: List<HanimeInfo> = emptyList(),
    val twoDAnime: List<HanimeInfo> = emptyList(),
    val aiGenerated: List<HanimeInfo> = emptyList(),
    val mmd: List<HanimeInfo> = emptyList(),
    val cosplay: List<HanimeInfo> = emptyList(),
    val watchingNow: List<HanimeInfo> = emptyList()
)

data class HanimeSearchFilter(
    val query: String? = null,
    val genre: String? = null,
    val sort: String? = null,
    val broad: Boolean = false,
    val date: String? = null,
    val duration: String? = null,
    val tags: Set<String> = emptySet(),
    val brands: Set<String> = emptySet(),
    val page: Int = 1
)

data class HanimeComment(
    val id: String = "",
    val avatarUrl: String = "",
    val name: String = "",
    val content: String = "",
    val date: String = "",
    val likesCount: Int = 0,
    val childComments: List<HanimeChildComment> = emptyList()
)

data class HanimeChildComment(
    val avatarUrl: String = "",
    val name: String = "",
    val content: String = "",
    val date: String = ""
)

data class SearchOptionItem(
    val name: String,
    val searchKey: String
)
