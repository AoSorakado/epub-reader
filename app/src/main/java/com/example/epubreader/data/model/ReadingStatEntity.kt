package com.example.epubreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_stats")
data class ReadingStatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long, // timestamp for the day (e.g., midnight)
    val readDurationMs: Long = 0, // time spent reading in milliseconds
    val wordsRead: Int = 0 // estimated words or characters read
)
