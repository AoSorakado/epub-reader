package com.example.epubreader.data.linovelib

import java.io.Serializable

data class LinovelibNovel(
    val id: String,
    val title: String,
    val author: String = "未知作者",
    val coverUrl: String = "",
    val category: String = "轻小说",
    val status: String = "连载中",
    val wordCount: String = "",
    val description: String = "",
    val lastUpdate: String = "",
    val latestChapter: String = "",
    val volumes: List<LinovelibVolume> = emptyList()
) : Serializable

data class LinovelibVolume(
    val volumeId: String,
    val volumeIndex: Int = 1,
    val volumeName: String,
    val totalChapters: Int = 0,
    val chapters: List<LinovelibChapter> = emptyList()
) : Serializable

data class LinovelibChapter(
    val id: String,
    val title: String,
    val url: String,
    val chapterIndex: Int = 1,
    val isVolumeHeader: Boolean = false
) : Serializable

data class LinovelibChapterContent(
    val chapterId: String,
    val title: String,
    val paragraphs: List<String>,
    val imageUrls: List<String> = emptyList()
)

data class LinovelibSearchFilter(
    val keyword: String = "",
    val page: Int = 1
)
