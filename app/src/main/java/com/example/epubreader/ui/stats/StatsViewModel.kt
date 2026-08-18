package com.example.epubreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.AnimeDao
import com.example.epubreader.data.db.AnimeStatDao
import com.example.epubreader.data.db.BookDao
import com.example.epubreader.data.db.StatDao
import com.example.epubreader.data.model.AnimeEntity
import com.example.epubreader.data.model.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayReadingStat(
    val dayLabel: String,
    val dateTimestamp: Long,
    val minutes: Int
)

data class DayWatchingStat(
    val dayLabel: String,
    val dateStr: String,
    val minutes: Int
)

data class HeatmapDay(
    val dateTimestamp: Long,
    val dateStr: String, // e.g. "8月16日"
    val fullDateStr: String, // e.g. "2026-08-16"
    val dayOfWeek: Int, // 0 (Sun) to 6 (Sat)
    val dayLabel: String, // e.g. "周日"
    val minutes: Int,
    val level: Int // 0 to 4
)

data class StatsState(
    val totalBooks: Int = 0,
    val totalSeries: Int = 0,
    val finishedBooks: Int = 0,
    val readingBooks: Int = 0,
    val totalProgressSum: Float = 0f,
    val totalReadingMinutes: Long = 0,
    val todayReadingMinutes: Long = 0,
    val weeklyTrend: List<DayReadingStat> = emptyList(),
    val heatmapWeeks: List<List<HeatmapDay>> = emptyList(), // 52 columns, 7 items per column
    val activeDaysCount: Int = 0,
    val currentStreakDays: Int = 0,
    val maxStreakDays: Int = 0,
    val recentlyAdded: BookEntity? = null,
    val recentlyRead: BookEntity? = null
)

data class AnimeStatsState(
    val totalAnimes: Int = 0,
    val watchingAnimes: Int = 0,
    val finishedAnimes: Int = 0,
    val unwatchedAnimes: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val totalEpisodesCount: Int = 0,
    val totalWatchMinutes: Long = 0,
    val todayWatchMinutes: Long = 0,
    val averageScore: Double = 0.0,
    val weeklyTrend: List<DayWatchingStat> = emptyList(),
    val recentlyAdded: AnimeEntity? = null,
    val recentlyWatched: AnimeEntity? = null
)

class StatsViewModel(
    private val bookDao: BookDao,
    private val statDao: StatDao,
    private val animeDao: AnimeDao,
    private val animeStatDao: AnimeStatDao
) : ViewModel() {

    private fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getHeatmapStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Align to start on Sunday 52 weeks ago
        val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 (Sun) .. 6 (Sat)
        cal.add(Calendar.DAY_OF_YEAR, -(52 * 7 - 1 + currentDayOfWeek))
        return cal.timeInMillis
    }

    private fun getSevenDaysAgoDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    // 1. Novel Reading Stats Flow
    val stats: StateFlow<StatsState> = combine(
        bookDao.getAllBooksByTime(),
        statDao.getTotalDurationFlow(),
        statDao.getTodayDurationFlow(getTodayStartTimestamp()),
        statDao.getStatsSinceFlow(getHeatmapStartTimestamp())
    ) { books, totalDurationMs, todayDurationMs, yearStats ->
        val total = books.size
        val seriesSet = books.mapNotNull { it.seriesName }.filter { it.isNotBlank() }.toSet()
        val finished = books.count { it.totalProgress >= 0.99f }
        val reading = books.count { (it.totalProgress > 0.0001f || (!it.lastReadPosition.isNullOrEmpty() && it.lastReadPosition != "0_0_0")) && it.totalProgress < 0.99f }
        
        val progressSum = if (total > 0) {
            books.sumOf { it.totalProgress.toDouble() }.toFloat()
        } else 0f

        val recentlyAdded = books.maxByOrNull { it.addedTime }
        val recentlyRead = books.maxByOrNull { it.lastReadTime }

        val totalMinutes = (totalDurationMs ?: 0L) / 60000L
        val todayMinutes = (todayDurationMs ?: 0L) / 60000L

        // Generate 7-day trend
        val statMap = yearStats.associateBy { it.date }
        val dayOfWeekLabels = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val weeklyTrend = (6 downTo 0).map { dayOffset ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
            val dayStart = cal.timeInMillis
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val label = if (dayOffset == 0) "今日" else dayOfWeekLabels.getOrElse(dayOfWeek) { "周" }
            val stat = statMap[dayStart]
            val durationMinutes = ((stat?.readDurationMs ?: 0L) / 60000L).toInt()
            DayReadingStat(
                dayLabel = label,
                dateTimestamp = dayStart,
                minutes = durationMinutes
            )
        }

        // Generate 52-week GitHub-style Heatmap matrix
        val heatmapWeeks = mutableListOf<List<HeatmapDay>>()
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = getHeatmapStartTimestamp()

        val fullSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val shortSdf = SimpleDateFormat("M月d日", Locale.getDefault())
        val nowMs = System.currentTimeMillis()

        var activeDays = 0
        var currentStreak = 0
        var maxStreak = 0
        var tempStreak = 0

        val totalDays = 53 * 7
        var currentWeekList = mutableListOf<HeatmapDay>()

        for (i in 0 until totalDays) {
            val dayTs = startCal.timeInMillis
            val date = Date(dayTs)
            val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK) - 1
            val stat = statMap[dayTs]
            val durationMin = if (dayTs <= nowMs) {
                ((stat?.readDurationMs ?: 0L) / 60000L).toInt()
            } else 0

            val level = when {
                dayTs > nowMs -> 0
                durationMin <= 0 -> 0
                durationMin <= 15 -> 1
                durationMin <= 35 -> 2
                durationMin <= 60 -> 3
                else -> 4
            }

            if (durationMin > 0) {
                activeDays++
                tempStreak++
                if (tempStreak > maxStreak) maxStreak = tempStreak
            } else if (dayTs <= nowMs) {
                tempStreak = 0
            }

            val heatmapDay = HeatmapDay(
                dateTimestamp = dayTs,
                dateStr = shortSdf.format(date),
                fullDateStr = fullSdf.format(date),
                dayOfWeek = dayOfWeek,
                dayLabel = dayOfWeekLabels.getOrElse(dayOfWeek) { "" },
                minutes = durationMin,
                level = level
            )

            currentWeekList.add(heatmapDay)

            if (currentWeekList.size == 7) {
                heatmapWeeks.add(currentWeekList)
                currentWeekList = mutableListOf()
            }

            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (currentWeekList.isNotEmpty()) {
            heatmapWeeks.add(currentWeekList)
        }

        // Calculate current streak backward from today
        val todayStart = getTodayStartTimestamp()
        val streakCheckCal = Calendar.getInstance()
        streakCheckCal.timeInMillis = todayStart
        while (true) {
            val ts = streakCheckCal.timeInMillis
            val stat = statMap[ts]
            val duration = ((stat?.readDurationMs ?: 0L) / 60000L).toInt()
            if (duration > 0) {
                currentStreak++
                streakCheckCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (ts == todayStart) {
                    streakCheckCal.add(Calendar.DAY_OF_YEAR, -1)
                    val yestStat = statMap[streakCheckCal.timeInMillis]
                    val yestDuration = ((yestStat?.readDurationMs ?: 0L) / 60000L).toInt()
                    if (yestDuration > 0) {
                        currentStreak++
                        streakCheckCal.add(Calendar.DAY_OF_YEAR, -1)
                        continue
                    }
                }
                break
            }
        }

        StatsState(
            totalBooks = total,
            totalSeries = seriesSet.size,
            finishedBooks = finished,
            readingBooks = reading,
            totalProgressSum = progressSum,
            totalReadingMinutes = totalMinutes,
            todayReadingMinutes = todayMinutes,
            weeklyTrend = weeklyTrend,
            heatmapWeeks = heatmapWeeks,
            activeDaysCount = activeDays,
            currentStreakDays = currentStreak,
            maxStreakDays = maxStreak,
            recentlyAdded = recentlyAdded,
            recentlyRead = recentlyRead
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, StatsState())

    // 2. Anime Stats Flow
    private val epCountsFlow = combine(
        animeDao.getCompletedEpisodeCountFlow(),
        animeDao.getTotalEpisodeCountFlow()
    ) { completed, total -> Pair(completed, total) }

    val animeStats: StateFlow<AnimeStatsState> = combine(
        animeDao.getAllAnimes(),
        animeStatDao.getLifetimeTotalMinutesFlow(),
        animeStatDao.getTodayTotalMinutesFlow(getTodayDateString()),
        animeStatDao.getStatsSinceFlow(getSevenDaysAgoDateString()),
        epCountsFlow
    ) { animes: List<AnimeEntity>, totalMinutesFromStats: Int?, todayMinutesFromStats: Int?, recentStats, epCounts: Pair<Int, Int> ->
        val (completedEpisodes, totalEpisodes) = epCounts
        val total = animes.size
        val watching = animes.count { !it.isFinished && it.lastWatchTimeMs > 0 }
        val finished = animes.count { it.isFinished }
        val unwatched = animes.count { it.lastWatchTimeMs == 0L }

        // Also aggregate watch duration from AnimeEntity totalWatchDurationSeconds if stats table is empty
        val dbAggregatedMinutes = animes.sumOf { it.totalWatchDurationSeconds / 60 }
        val totalMinutes = maxOf((totalMinutesFromStats ?: 0).toLong(), dbAggregatedMinutes)
        val todayMinutes = (todayMinutesFromStats ?: 0).toLong()

        val ratedAnimes = animes.filter { it.score > 0f }
        val avgScore = if (ratedAnimes.isNotEmpty()) {
            ratedAnimes.map { it.score.toDouble() }.average()
        } else 0.0

        val recentlyAdded = animes.maxByOrNull { it.createdAt }
        val recentlyWatched = animes.filter { it.lastWatchTimeMs > 0 }.maxByOrNull { it.updatedAt }

        // 7-day watch trend
        val statMapByDate = recentStats.groupBy { it.date }
            .mapValues { entry -> entry.value.sumOf { it.minutes } }

        val dayOfWeekLabels = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weeklyTrend = (6 downTo 0).map { dayOffset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
            val dStr = sdf.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val label = if (dayOffset == 0) "今日" else dayOfWeekLabels.getOrElse(dayOfWeek) { "周" }
            val minutes = statMapByDate[dStr] ?: 0
            DayWatchingStat(
                dayLabel = label,
                dateStr = dStr,
                minutes = minutes
            )
        }

        AnimeStatsState(
            totalAnimes = total,
            watchingAnimes = watching,
            finishedAnimes = finished,
            unwatchedAnimes = unwatched,
            totalEpisodesWatched = completedEpisodes,
            totalEpisodesCount = totalEpisodes,
            totalWatchMinutes = totalMinutes,
            todayWatchMinutes = todayMinutes,
            averageScore = avgScore,
            weeklyTrend = weeklyTrend,
            recentlyAdded = recentlyAdded,
            recentlyWatched = recentlyWatched
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AnimeStatsState())
}

class StatsViewModelFactory(
    private val bookDao: BookDao,
    private val statDao: StatDao,
    private val animeDao: AnimeDao,
    private val animeStatDao: AnimeStatDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(bookDao, statDao, animeDao, animeStatDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
