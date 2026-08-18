package com.example.epubreader.data.hanime

import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element

object HanimeParser {

    private const val TAG = "HanimeParser"

    val videoUrlRegex = Regex(
        """(?:(?:https?:)?//[^\s"'<>/]+|(?:hanime(?:1|one)|javchu)\.(?:com|me))?(?:/[^/?#\s"'<>]+)*/watch\?(?:[^#\s"'<>]*&)?v=(\d+)"""
    )
    val videoSourceRegex = Regex("""const source = '(.+)'""")
    val viewAndUploadTimeRegex = Regex("""(觀看次數|观看次数)：(.+次) *(\d{4}-\d{2}-\d{2})""")

    fun String.toVideoCode(): String? = videoUrlRegex.find(this)?.groupValues?.get(1)

    fun parseHomePage(html: String, baseUrl: String = "https://hanime1.me/"): HanimeHomePage {
        val doc = Jsoup.parse(html, baseUrl).body()
        val homePageParse = doc.select("div[id=home-rows-wrapper] > div")

        // 1. Banner
        val bannerCSS = doc.selectFirst("div[id=home-banner-wrapper]")
        val bannerImg = bannerCSS?.previousElementSibling()
        val bannerTitle = bannerImg?.selectFirst("img")?.attr("alt") ?: ""
        val bannerPic = bannerImg?.select("img")?.let { imgList ->
            imgList.getOrNull(1)?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: imgList.getOrNull(0)?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: imgList.getOrNull(0)?.attr("src")
        } ?: ""
        val bannerDesc = bannerCSS?.selectFirst("h4")?.ownText()

        val bannerVideoCodeScript = doc.select("script").firstOrNull { it.data().contains("watch?v=") }?.data()
        val regex = Regex("""watch\?v=(\d+)""")
        var bannerVideoCode = bannerVideoCodeScript?.let { regex.find(it)?.groupValues?.get(1) }
        if (bannerVideoCode == null) {
            bannerCSS?.traverse { node, _ ->
                if (node is Comment) {
                    node.data.toVideoCode()?.let {
                        bannerVideoCode = it
                        return@traverse
                    }
                }
            }
        }

        val banner = if (bannerTitle.isNotBlank() && bannerPic.isNotBlank()) {
            HanimeBanner(
                title = bannerTitle,
                description = bannerDesc,
                picUrl = bannerPic,
                videoCode = bannerVideoCode
            )
        } else null

        // 2. Sections (Deduplicated)
        val latestRelease = homePageParse.getOrNull(0).extractHanimeList()
        val latestUpload = homePageParse.getOrNull(1).extractHanimeList()
        val hentaiAnime = homePageParse.getOrNull(2).extractHanimeList()
        val shortAnime = homePageParse.getOrNull(3).extractHanimeList()
        val motionAnime = homePageParse.getOrNull(5).extractHanimeList()
        val threeDCG = homePageParse.getOrNull(6).extractHanimeList()
        val twoPointFiveD = homePageParse.getOrNull(7).extractHanimeList()
        val twoDAnime = homePageParse.getOrNull(8).extractHanimeList()
        val aiGenerated = homePageParse.getOrNull(10).extractHanimeList()
        val mmd = homePageParse.getOrNull(11).extractHanimeList()
        val cosplay = homePageParse.getOrNull(12).extractHanimeList()
        val watchingNow = homePageParse.getOrNull(13).extractHanimeList()

        return HanimeHomePage(
            banner = banner,
            latestRelease = latestRelease,
            latestUpload = latestUpload,
            hentaiAnime = hentaiAnime,
            shortAnime = shortAnime,
            motionAnime = motionAnime,
            threeDCG = threeDCG,
            twoPointFiveD = twoPointFiveD,
            twoDAnime = twoDAnime,
            aiGenerated = aiGenerated,
            mmd = mmd,
            cosplay = cosplay,
            watchingNow = watchingNow
        )
    }

    fun parseVideo(html: String, fallbackVideoCode: String, baseUrl: String = "https://hanime1.me/"): HanimeVideo {
        val doc = Jsoup.parse(html, baseUrl).body()
        val title = doc.getElementById("shareBtn-title")?.text()?.trim() ?: "Hanime Video"

        val videoDetailWrapper = doc.selectFirst("div[class=video-details-wrapper]")
        val videoCaptionText = videoDetailWrapper?.selectFirst("div[class^=video-caption-text]")
        val chineseTitle = videoCaptionText?.previousElementSibling()?.ownText()
        val introduction = videoCaptionText?.ownText()

        val uploadTimeWithViews = videoDetailWrapper?.selectFirst("div > div > div")?.text()
        val uploadMatch = uploadTimeWithViews?.let { viewAndUploadTimeRegex.find(it) }
        val views = uploadMatch?.groupValues?.getOrNull(2)
        val uploadTime = uploadMatch?.groupValues?.getOrNull(3)

        // Tags
        val tags = mutableListOf<String>()
        doc.getElementsByClass("single-video-tag").forEach { tag ->
            val a = tag.selectFirst("a")
            if (a != null) {
                val t = a.text().substringBefore(" (").removePrefix("#").trim()
                if (t.isNotEmpty()) tags.add(t)
            }
        }

        // Resolutions / Video Sources
        val resolutionMap = linkedMapOf<String, String>()
        val videoEl = doc.selectFirst("video[id=player]")
        val poster = videoEl?.absUrl("poster")?.takeIf { it.isNotBlank() } ?: videoEl?.attr("poster").orEmpty()
        val sources = videoEl?.select("source")

        if (!sources.isNullOrEmpty()) {
            for (src in sources) {
                val size = src.attr("size").trim()
                val url = src.absUrl("src").takeIf { it.isNotBlank() } ?: src.attr("src").trim()
                if (url.isNotBlank()) {
                    val key = if (size.endsWith("P", ignoreCase = true)) size.uppercase() else "${size}P"
                    resolutionMap[key] = url
                }
            }
        }

        // Fallback: Check script in player div
        if (resolutionMap.isEmpty()) {
            val playerDivWrapper = doc.selectFirst("div[id=player-div-wrapper]")
            playerDivWrapper?.select("script")?.forEach { script ->
                val data = script.data()
                if (data.contains("const source")) {
                    val srcUrl = videoSourceRegex.find(data)?.groupValues?.getOrNull(1)
                    if (!srcUrl.isNullOrBlank()) {
                        resolutionMap["720P"] = srcUrl
                    }
                }
            }
        }

        // Series / Playlist
        var playlist: HanimePlaylist? = null
        val playlistWrapper = doc.selectFirst("div.video-playlist-wrapper")
            ?: doc.selectFirst("div[id=video-playlist-wrapper]")

        if (playlistWrapper != null) {
            val playlistVideoList = mutableListOf<HanimeInfo>()
            val playlistScroll = playlistWrapper.getElementById("playlist-scroll")
            val items = playlistScroll?.select("div[class^=related-watch-wrap], a[href*='watch?v=']")
                ?: playlistWrapper.select("a[href*='watch?v=']")

            for (item in items) {
                val link = if (item.tagName() == "a") item else item.selectFirst("a")
                val vCode = link?.attr("href")?.toVideoCode() ?: continue
                val cUrl = link.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() } ?: link.selectFirst("img")?.attr("src").orEmpty()
                val pTitle = link.selectFirst("div[class*=title]")?.text()
                    ?: link.selectFirst("img")?.attr("alt")
                    ?: link.text()
                val epNo = link.selectFirst("div[class*=ep-no], div.playlist-ep-no")?.text()

                playlistVideoList.add(
                    HanimeInfo(
                        videoCode = vCode,
                        title = pTitle.trim(),
                        coverUrl = cUrl,
                        duration = epNo.orEmpty()
                    )
                )
            }

            val playlistTitle = playlistWrapper.selectFirst("div[class*=playlist-title], h4")?.text() ?: "合集列表"
            if (playlistVideoList.isNotEmpty()) {
                val distinctList = playlistVideoList.distinctBy { it.videoCode }
                val currentIdx = distinctList.indexOfFirst { it.videoCode == fallbackVideoCode }.coerceAtLeast(0)
                playlist = HanimePlaylist(
                    playlistName = playlistTitle,
                    episodes = distinctList
                )
            }
        }

        // Related Hanimes
        val relatedAnimeList = mutableListOf<HanimeInfo>()
        val relatedTabContent = doc.getElementById("related-tabcontent")
        if (relatedTabContent != null) {
            relatedAnimeList.addAll(relatedTabContent.extractHanimeList())
        }

        // Author / Artist
        val artistAvatarUrl = doc.select("div.video-details-wrapper img[style*='border-radius: 50%'], div.video-details-wrapper img[style*='border-radius:50%']")
            .firstOrNull()?.absUrl("src")
            ?: doc.select("div.video-details-wrapper img").firstOrNull()?.absUrl("src").orEmpty()

        val artistNameEl = doc.getElementById("video-artist-name")
            ?: doc.selectFirst("div.video-details-wrapper a[href*='/artist/'], div.video-details-wrapper a[href*='/uploader/']")
        val artistName = artistNameEl?.text()?.trim()
        val artistGenre = artistNameEl?.nextElementSibling()?.text()?.trim()

        return HanimeVideo(
            videoCode = fallbackVideoCode,
            title = title,
            chineseTitle = chineseTitle,
            coverUrl = poster,
            views = views,
            uploadTime = uploadTime,
            introduction = introduction,
            tags = tags,
            videoUrls = resolutionMap,
            playlist = playlist,
            relatedHanimes = relatedAnimeList.distinctBy { it.videoCode },
            artistName = artistName,
            artistAvatarUrl = artistAvatarUrl,
            artistGenre = artistGenre
        )
    }

    fun parseSearchResults(html: String, baseUrl: String = "https://hanime1.me/"): List<HanimeInfo> {
        val doc = Jsoup.parse(html, baseUrl).body()
        val normalContainer = doc.getElementsByClass("content-padding-new").firstOrNull()
        val simplifiedContainer = doc.getElementsByClass("home-rows-videos-wrapper").firstOrNull()

        val results = mutableListOf<HanimeInfo>()
        if (normalContainer != null) {
            val cards = normalContainer.select("div[class^=horizontal-card]")
            for (card in cards) {
                parseSingleCard(card)?.let { results.add(it) }
            }
        } else if (simplifiedContainer != null) {
            val links = simplifiedContainer.select("a[href*='watch?v=']")
            for (link in links) {
                parseSingleCard(link)?.let { results.add(it) }
            }
        } else {
            val fallbackCards = doc.select("div[class^=horizontal-card]")
            if (fallbackCards.isNotEmpty()) {
                for (card in fallbackCards) {
                    parseSingleCard(card)?.let { results.add(it) }
                }
            } else {
                val links = doc.select("a[href*='watch?v=']")
                for (link in links) {
                    parseSingleCard(link)?.let { results.add(it) }
                }
            }
        }
        return results.distinctBy { it.videoCode }
    }

    fun parseSearchResult(html: String, baseUrl: String = "https://hanime1.me/"): List<HanimeInfo> = parseSearchResults(html, baseUrl)

    fun parseComments(jsonString: String, baseUrl: String = "https://hanime1.me/"): List<HanimeComment> {
        return try {
            val jsonObject = JSONObject(jsonString)
            val commentBody = jsonObject.optString("comments", "")
            if (commentBody.isBlank()) return emptyList()

            val doc = Jsoup.parse(commentBody, baseUrl).body()
            val commentList = mutableListOf<HanimeComment>()
            val allCommentsClass = doc.getElementById("comment-start") ?: return emptyList()

            val chunks = allCommentsClass.children().chunked(4)
            for (elements in chunks) {
                val container = Element("div").apply { appendChildren(elements) }
                val avatarUrl = container.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() } ?: container.selectFirst("img")?.attr("src").orEmpty()
                val textClass = container.getElementsByClass("comment-index-text")
                val nameAndDateClass = textClass.firstOrNull()
                val username = nameAndDateClass?.selectFirst("a")?.ownText()?.trim().orEmpty()
                val date = nameAndDateClass?.selectFirst("span")?.ownText()?.trim().orEmpty()
                val content = textClass.getOrNull(1)?.text().orEmpty()
                val thumbUp = container.getElementById("comment-like-form-wrapper")
                    ?.select("span[style]")?.getOrNull(1)
                    ?.text()?.toIntOrNull() ?: 0
                val id = container.selectFirst("div[id^=reply-section-wrapper]")
                    ?.id()?.substringAfterLast("-").orEmpty()

                if (username.isNotBlank() || content.isNotBlank()) {
                    commentList.add(
                        HanimeComment(
                            id = id,
                            avatarUrl = avatarUrl,
                            name = username,
                            content = content,
                            date = date,
                            likesCount = thumbUp
                        )
                    )
                }
            }
            commentList
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing comments", e)
            emptyList()
        }
    }

    private fun Element?.extractHanimeList(): List<HanimeInfo> {
        if (this == null) return emptyList()
        val resultList = mutableListOf<HanimeInfo>()

        // 1. Look for horizontal cards
        val cards = this.select("div[class^=horizontal-card]")
        if (cards.isNotEmpty()) {
            for (card in cards) {
                parseSingleCard(card)?.let { resultList.add(it) }
            }
        } else {
            // 2. Direct watch links or fallback containers
            val links = this.select("a[href*='watch?v=']")
            if (links.isNotEmpty()) {
                for (link in links) {
                    parseSingleCard(link)?.let { resultList.add(it) }
                }
            } else {
                val fallbackCards = this.select("div.home-rows-videos-div, div[class^=video-item-container]")
                for (card in fallbackCards) {
                    parseSingleCard(card)?.let { resultList.add(it) }
                }
            }
        }
        return resultList.distinctBy { it.videoCode }
    }

    private fun parseSingleCard(item: Element): HanimeInfo? {
        val linkEl = if (item.tagName() == "a") item else (item.selectFirst("a") ?: item.parent()?.takeIf { it.tagName() == "a" })
            ?: return null

        val vCode = linkEl.absUrl("href").toVideoCode()
            ?: linkEl.attr("href").toVideoCode()
            ?: item.attr("data-href").toVideoCode()
            ?: item.id().toVideoCode()
            ?: return null

        val title = item.selectFirst("div.home-rows-videos-title, div.title, h4.video-title, h4")?.text()?.trim()
            ?: linkEl.selectFirst("div.home-rows-videos-title, div.title, h4.video-title, h4")?.text()?.trim()
            ?: item.selectFirst("img")?.attr("alt")?.trim()
            ?: linkEl.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val imgEl = item.selectFirst("img") ?: linkEl.selectFirst("img")
        val coverUrl = imgEl?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: imgEl?.attr("src")
            ?: ""

        val thumbContainer = item.selectFirst("div[class^=thumb-container], .thumb-container")
            ?: linkEl.selectFirst("div[class^=thumb-container], .thumb-container")
        val duration = thumbContainer?.selectFirst("div[class^=duration], .duration")?.text()
            ?: item.selectFirst(".duration")?.text()
            ?: linkEl.selectFirst(".duration")?.text().orEmpty()

        val statItems = thumbContainer?.select("div[class^=stat-item], .stat-item")
            ?: linkEl.select("div[class^=stat-item], .stat-item")
        val views = statItems?.getOrNull(1)?.text()
            ?: item.selectFirst(".stat-item")?.text()
            ?: linkEl.selectFirst(".stat-item")?.text().orEmpty()

        val subtitleElement = item.selectFirst("div.subtitle, div.video-meta-data")
            ?: linkEl.selectFirst("div.subtitle, div.video-meta-data")
        val artistAndUploadTime = subtitleElement?.text()?.trim().orEmpty()
        var artist: String? = null
        var uploadTime: String? = null
        if (artistAndUploadTime.contains("•")) {
            val parts = artistAndUploadTime.split("•").map { it.trim() }
            artist = parts.getOrNull(0)
            uploadTime = parts.getOrNull(1)
        } else {
            artist = subtitleElement?.selectFirst("a")?.text()?.trim()
            uploadTime = subtitleElement?.selectFirst("span.subtitle-time, span")?.text()?.trim()
        }

        return HanimeInfo(
            videoCode = vCode,
            title = title,
            coverUrl = coverUrl,
            duration = duration,
            views = views,
            artist = artist,
            uploadTime = uploadTime
        )
    }
}
