package com.example.epubreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.AnimeEpisodeEntity
import com.example.epubreader.data.model.AnimeStatEntity
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.model.ReadingStatEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ReadingStatEntity::class,
        AnimeEntity::class,
        AnimeEpisodeEntity::class,
        AnimeStatEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun statDao(): StatDao
    abstract fun animeDao(): AnimeDao
    abstract fun animeStatDao(): AnimeStatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS animes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        originalTitle TEXT,
                        coverUrl TEXT,
                        localCoverPath TEXT,
                        bangumiId INTEGER,
                        score REAL NOT NULL DEFAULT 0,
                        summary TEXT,
                        airDate TEXT,
                        totalEpisodes INTEGER NOT NULL DEFAULT 0,
                        seasonCount INTEGER NOT NULL DEFAULT 1,
                        currentSeasonName TEXT,
                        webdavPath TEXT NOT NULL,
                        isMultiSeason INTEGER NOT NULL DEFAULT 0,
                        lastWatchEpisodeId INTEGER,
                        lastWatchEpisodeName TEXT,
                        lastWatchTimeMs INTEGER NOT NULL DEFAULT 0,
                        totalWatchDurationSeconds INTEGER NOT NULL DEFAULT 0,
                        isFinished INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_animes_webdavPath ON animes (webdavPath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_animes_bangumiId ON animes (bangumiId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_animes_title ON animes (title)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS anime_episodes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        animeId INTEGER NOT NULL,
                        seasonName TEXT NOT NULL DEFAULT '正片',
                        episodeIndex INTEGER NOT NULL DEFAULT 1,
                        episodeNumber TEXT NOT NULL DEFAULT '01',
                        title TEXT NOT NULL,
                        videoUrl TEXT NOT NULL,
                        subtitleUrl TEXT,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        lastPlayedPositionMs INTEGER NOT NULL DEFAULT 0,
                        isWatched INTEGER NOT NULL DEFAULT 0,
                        resolution TEXT NOT NULL DEFAULT '1080p',
                        videoCodec TEXT NOT NULL DEFAULT 'HEVC',
                        audioCodec TEXT NOT NULL DEFAULT 'FLAC',
                        releaseGroup TEXT,
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        danmakuEpisodeId INTEGER,
                        danmakuTimeOffsetMs INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (animeId) REFERENCES animes(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_anime_episodes_animeId ON anime_episodes (animeId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_anime_episodes_videoUrl ON anime_episodes (videoUrl)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS anime_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        minutes INTEGER NOT NULL DEFAULT 0,
                        animeId INTEGER NOT NULL DEFAULT 0,
                        episodesWatched INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_anime_stats_date_animeId ON anime_stats (date, animeId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "epub_reader_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
