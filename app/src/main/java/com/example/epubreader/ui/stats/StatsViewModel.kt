package com.example.epubreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.BookDao
import com.example.epubreader.data.model.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsState(
    val totalBooks: Int = 0,
    val totalSeries: Int = 0,
    val finishedBooks: Int = 0,
    val readingBooks: Int = 0,
    val totalProgressSum: Float = 0f,
    val recentlyAdded: BookEntity? = null,
    val recentlyRead: BookEntity? = null
)

class StatsViewModel(private val dao: BookDao) : ViewModel() {
    val stats: StateFlow<StatsState> = dao.getAllBooksByTime().map { books ->
        val total = books.size
        val seriesSet = books.mapNotNull { it.seriesName }.filter { it.isNotBlank() }.toSet()
        val finished = books.count { it.totalProgress >= 0.95f }
        val reading = books.count { it.totalProgress > 0f && it.totalProgress < 0.95f }
        
        val progressSum = if (total > 0) {
            books.sumOf { it.totalProgress.toDouble() }.toFloat()
        } else 0f

        val recentlyAdded = books.maxByOrNull { it.addedTime }
        val recentlyRead = books.maxByOrNull { it.lastReadTime }

        StatsState(
            totalBooks = total,
            totalSeries = seriesSet.size,
            finishedBooks = finished,
            readingBooks = reading,
            totalProgressSum = progressSum,
            recentlyAdded = recentlyAdded,
            recentlyRead = recentlyRead
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, StatsState())
}

class StatsViewModelFactory(private val dao: BookDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
