package com.example.epubreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.epubreader.data.model.ReadingStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatDao {
    @Query("SELECT * FROM reading_stats WHERE date = :dayTimestamp LIMIT 1")
    suspend fun getStatForDay(dayTimestamp: Long): ReadingStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stat: ReadingStatEntity)

    @Query("SELECT SUM(readDurationMs) FROM reading_stats")
    fun getTotalDurationFlow(): Flow<Long?>

    @Query("SELECT SUM(readDurationMs) FROM reading_stats WHERE date = :todayStart")
    fun getTodayDurationFlow(todayStart: Long): Flow<Long?>

    @Query("SELECT * FROM reading_stats WHERE date >= :startDay ORDER BY date ASC")
    fun getStatsSinceFlow(startDay: Long): Flow<List<ReadingStatEntity>>

    @Query("SELECT * FROM reading_stats ORDER BY date DESC LIMIT 30")
    fun getRecentStatsFlow(): Flow<List<ReadingStatEntity>>
}
