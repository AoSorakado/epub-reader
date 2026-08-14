package com.example.epubreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val coverImage: String? = null, // URI or local path to cover image
    val filePath: String, // WebDAV URL or local URI
    val isWebDav: Boolean,
    val seriesName: String? = null, // Used to group different volumes
    val volumeIndex: Int = 0,
    val lastReadPosition: String? = null, // CFI or chapter index
    val lastReadTime: Long = 0,
    val totalProgress: Float = 0f, // 0.0 to 1.0
    val addedTime: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
