package com.example.epubreader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "animes",
    indices = [
        Index(value = ["webdavPath"], unique = true),
        Index(value = ["bangumiId"]),
        Index(value = ["title"])
    ]
)
data class AnimeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalTitle: String? = null,
    val coverUrl: String? = null,
    val localCoverPath: String? = null,
    val bangumiId: Long? = null,
    val score: Float = 0f,
    val summary: String? = null,
    val airDate: String? = null,
    val totalEpisodes: Int = 0,
    val seasonCount: Int = 1,
    val currentSeasonName: String? = null,
    val webdavPath: String,
    val isMultiSeason: Boolean = false,
    val lastWatchEpisodeId: Long? = null,
    val lastWatchEpisodeName: String? = null,
    val lastWatchTimeMs: Long = 0L,
    val totalWatchDurationSeconds: Long = 0L,
    val isFinished: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "anime_episodes",
    foreignKeys = [
        ForeignKey(
            entity = AnimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["animeId"]),
        Index(value = ["videoUrl"], unique = true)
    ]
)
data class AnimeEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val animeId: Long,
    val seasonName: String = "正片",
    val episodeIndex: Int = 1,
    val episodeNumber: String = "01",
    val title: String,
    val videoUrl: String,
    val subtitleUrl: String? = null,
    val durationMs: Long = 0L,
    val lastPlayedPositionMs: Long = 0L,
    val isWatched: Boolean = false,
    val resolution: String = "1080p",
    val videoCodec: String = "HEVC",
    val audioCodec: String = "FLAC",
    val releaseGroup: String? = null,
    val fileSize: Long = 0L,
    val danmakuEpisodeId: Long? = null,
    val danmakuTimeOffsetMs: Long = 0L
)

@Entity(
    tableName = "anime_stats",
    indices = [
        Index(value = ["date", "animeId"], unique = true)
    ]
)
data class AnimeStatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val minutes: Int = 0,
    val animeId: Long = 0L,
    val episodesWatched: Int = 0
)
