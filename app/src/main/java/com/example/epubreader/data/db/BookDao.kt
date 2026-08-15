package com.example.epubreader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.epubreader.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadTime > 0 THEN lastReadTime ELSE addedTime END DESC")
    fun getAllBooksByLastRead(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY addedTime DESC")
    fun getAllBooksByTime(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooksByName(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY totalProgress DESC, lastReadTime DESC")
    fun getAllBooksByProgress(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY sortOrder ASC")
    fun getAllBooksByManual(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Query("SELECT * FROM books WHERE seriesName = :seriesName ORDER BY volumeIndex ASC")
    fun getBooksBySeries(seriesName: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Update
    suspend fun updateBooks(books: List<BookEntity>)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}
