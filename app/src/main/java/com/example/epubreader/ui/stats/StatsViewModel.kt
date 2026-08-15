package com.example.epubreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.BookDao
import com.example.epubreader.data.db.StatDao
import com.example.epubreader.data.model.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class DayReadingStat(
    val dayLabel: String,
    val dateTimestamp: Long,
    val minutes: Int
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
    val recentlyAdded: BookEntity? = null,
    val recentlyRead: BookEntity? = null
)

class StatsViewModel(
    private val bookDao: BookDao,
    private val statDao: StatDao
) : ViewModel() {

    private fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun get7DaysAgoTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return cal.timeInMillis
    }

    val stats: StateFlow<StatsState> = combine(
        bookDao.getAllBooksByTime(),
        statDao.getTotalDurationFlow(),
        statDao.getTodayDurationFlow(getTodayStartTimestamp()),
        statDao.getStatsSinceFlow(get7DaysAgoTimestamp())
    ) { books, totalDurationMs, todayDurationMs, recentStats ->
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
        val statMap = recentStats.associateBy { it.date }
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

        StatsState(
            totalBooks = total,
            totalSeries = seriesSet.size,
            finishedBooks = finished,
            readingBooks = reading,
            totalProgressSum = progressSum,
            totalReadingMinutes = totalMinutes,
            todayReadingMinutes = todayMinutes,
            weeklyTrend = weeklyTrend,
            recentlyAdded = recentlyAdded,
            recentlyRead = recentlyRead
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, StatsState())
}

class StatsViewModelFactory(
    private val bookDao: BookDao,
    private val statDao: StatDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(bookDao, statDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
