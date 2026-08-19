package com.example.epubreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import kotlinx.coroutines.flow.Flow

data class AnimeWithEpisodes(
    @androidx.room.Embedded val anime: AnimeEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "animeId"
    )
    val episodes: List<AnimeEpisodeEntity>
)

@Dao
interface AnimeDao {
    @Query("SELECT * FROM animes ORDER BY updatedAt DESC")
    fun getAllAnimes(): Flow<List<AnimeEntity>>

    @Transaction
    @Query("SELECT * FROM animes ORDER BY updatedAt DESC")
    fun getAllAnimesWithEpisodes(): Flow<List<AnimeWithEpisodes>>

    @Query("SELECT * FROM animes WHERE id = :id")
    suspend fun getAnimeById(id: Long): AnimeEntity?

    @Query("SELECT * FROM animes WHERE webdavPath = :path LIMIT 1")
    suspend fun getAnimeByWebdavPath(path: String): AnimeEntity?

    @Transaction
    @Query("SELECT * FROM animes WHERE id = :id")
    suspend fun getAnimeWithEpisodes(id: Long): AnimeWithEpisodes?

    @Query("SELECT * FROM anime_episodes WHERE animeId = :animeId ORDER BY seasonName ASC, episodeIndex ASC")
    fun getEpisodesByAnimeId(animeId: Long): Flow<List<AnimeEpisodeEntity>>

    @Query("SELECT * FROM anime_episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): AnimeEpisodeEntity?

    @Query("SELECT * FROM anime_episodes WHERE videoUrl = :videoUrl LIMIT 1")
    suspend fun getEpisodeByVideoUrl(videoUrl: String): AnimeEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnime(anime: AnimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<AnimeEpisodeEntity>)

    @Update
    suspend fun updateAnime(anime: AnimeEntity)

    @Update
    suspend fun updateEpisode(episode: AnimeEpisodeEntity)

    @Query("UPDATE animes SET lastWatchEpisodeId = :episodeId, lastWatchEpisodeName = :episodeName, lastWatchTimeMs = :positionMs, updatedAt = :updatedAt WHERE id = :animeId")
    suspend fun updateWatchProgress(animeId: Long, episodeId: Long, episodeName: String, positionMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE anime_episodes SET lastPlayedPositionMs = :positionMs, durationMs = :durationMs, isWatched = :isWatched WHERE id = :episodeId")
    suspend fun updateEpisodeProgress(episodeId: Long, positionMs: Long, durationMs: Long, isWatched: Boolean)

    @Query("SELECT COUNT(*) FROM anime_episodes WHERE isWatched = 1")
    fun getCompletedEpisodeCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM anime_episodes")
    fun getTotalEpisodeCountFlow(): Flow<Int>

    @Query("UPDATE animes SET totalWatchDurationSeconds = totalWatchDurationSeconds + :secondsToAdd, updatedAt = :updatedAt WHERE id = :animeId")
    suspend fun addWatchDuration(animeId: Long, secondsToAdd: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM animes WHERE coverUrl IS NULL OR coverUrl = '' LIMIT 100")
    suspend fun getAnimesWithoutCover(): List<AnimeEntity>

    @Query("DELETE FROM anime_episodes WHERE animeId = :animeId")
    suspend fun deleteEpisodesByAnimeId(animeId: Long)

    @Query("DELETE FROM animes WHERE id = :id")
    suspend fun deleteAnime(id: Long)

    @Query("DELETE FROM animes")
    suspend fun clearAllAnimes()

    @Transaction
    suspend fun saveScannedAnime(anime: AnimeEntity, episodes: List<AnimeEpisodeEntity>): Long {
        val animeId = insertAnime(anime)
        deleteEpisodesByAnimeId(animeId)
        insertEpisodes(episodes.map { it.copy(animeId = animeId) })
        return animeId
    }

    @Transaction
    suspend fun saveAllScannedAnimes(results: List<Pair<AnimeEntity, List<AnimeEpisodeEntity>>>) {
        for ((anime, episodes) in results) {
            val animeId = insertAnime(anime)
            deleteEpisodesByAnimeId(animeId)
            insertEpisodes(episodes.map { it.copy(animeId = animeId) })
        }
    }
}
