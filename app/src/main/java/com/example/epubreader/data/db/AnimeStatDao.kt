package com.example.epubreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.epubreader.data.model.AnimeStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeStatDao {
    @Query("SELECT * FROM anime_stats ORDER BY date DESC")
    fun getAllStats(): Flow<List<AnimeStatEntity>>

    @Query("SELECT * FROM anime_stats WHERE date = :date")
    suspend fun getStatsByDate(date: String): List<AnimeStatEntity>

    @Query("SELECT SUM(minutes) FROM anime_stats WHERE date = :date")
    suspend fun getTotalMinutesByDate(date: String): Int?

    @Query("SELECT SUM(minutes) FROM anime_stats WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalMinutesBetween(startDate: String, endDate: String): Int?

    @Query("SELECT SUM(minutes) FROM anime_stats")
    suspend fun getLifetimeTotalMinutes(): Int?

    @Query("SELECT SUM(minutes) FROM anime_stats")
    fun getLifetimeTotalMinutesFlow(): Flow<Int?>

    @Query("SELECT SUM(minutes) FROM anime_stats WHERE date = :date")
    fun getTodayTotalMinutesFlow(date: String): Flow<Int?>

    @Query("SELECT SUM(episodesWatched) FROM anime_stats")
    suspend fun getLifetimeTotalEpisodesWatched(): Int?

    @Query("SELECT SUM(episodesWatched) FROM anime_stats")
    fun getLifetimeTotalEpisodesWatchedFlow(): Flow<Int?>

    @Query("SELECT * FROM anime_stats WHERE date >= :startDate")
    fun getStatsSinceFlow(startDate: String): Flow<List<AnimeStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stat: AnimeStatEntity)

    @Query("SELECT * FROM anime_stats WHERE date = :date AND animeId = :animeId LIMIT 1")
    suspend fun getStat(date: String, animeId: Long): AnimeStatEntity?
}
