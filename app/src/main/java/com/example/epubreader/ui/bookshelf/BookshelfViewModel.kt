package com.example.epubreader.ui.bookshelf

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.db.BookDao
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.parser.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModel(private val bookDao: BookDao, application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bookshelf_settings", Context.MODE_PRIVATE)

    // 0: 最近阅读, 1: 导入时间, 2: 书名排序, 3: 阅读进度
    private val _sortMethod = MutableStateFlow(prefs.getInt("sortMethod", 0))
    val sortMethod = _sortMethod.asStateFlow()

    private val _layoutMethod = MutableStateFlow(prefs.getInt("layoutMethod", 0)) // 0: Grid, 1: List
    val layoutMethod = _layoutMethod.asStateFlow()

    // Observe real DB data based on sort mode
    val books: StateFlow<List<BookEntity>> = _sortMethod.flatMapLatest { sort ->
        when (sort) {
            0 -> bookDao.getAllBooksByLastRead()
            1 -> bookDao.getAllBooksByTime()
            2 -> bookDao.getAllBooksByName()
            3 -> bookDao.getAllBooksByProgress()
            else -> bookDao.getAllBooksByLastRead()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSortMethod(method: Int) {
        prefs.edit().putInt("sortMethod", method).apply()
        _sortMethod.value = method
    }

    fun setLayoutMethod(method: Int) {
        prefs.edit().putInt("layoutMethod", method).apply()
        _layoutMethod.value = method
    }

    fun importLocalBook(uri: Uri, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Create storage directories
                    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

                    // We need a unique filename. Use timestamp.
                    val timestamp = System.currentTimeMillis()
                    val localEpubFile = File(booksDir, "book_.epub")

                    // 1. Copy the selected file to our internal storage
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localEpubFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 2. Parse the EPUB using high-speed random-access
                    val epubBook = EpubParser.parse(localEpubFile)

                    // 3. Save Cover Image if exists
                    var coverImagePath: String? = null
                    if (epubBook.coverImage != null) {
                        val coverFile = File(coversDir, "cover_.jpg")
                        coverFile.writeBytes(epubBook.coverImage)
                        coverImagePath = coverFile.absolutePath
                    }

                    // 4. Create and insert BookEntity
                    val bookEntity = BookEntity(
                        title = epubBook.title,
                        author = epubBook.author,
                        coverImage = coverImagePath,
                        filePath = localEpubFile.absolutePath,
                        isWebDav = false,
                        addedTime = timestamp,
                        lastReadTime = 0
                    )
                    bookDao.insertBook(bookEntity)
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "📖 成功导入《${epubBook.title}》",
                        type = com.example.epubreader.ui.components.toast.ToastType.Success
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteAllBooks() {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.deleteAll()
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.deleteBook(book)
            book.filePath?.let { File(it).delete() }
            book.coverImage?.let { File(it).delete() }
            com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                text = "🗑️ 已从书架移除《${book.title}》",
                type = com.example.epubreader.ui.components.toast.ToastType.Info
            )
        }
    }

    fun updateBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBook(book)
        }
    }

    fun updateBookInfo(book: BookEntity, newTitle: String, newCoverUri: Uri?, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var coverImagePath = book.coverImage
                if (newCoverUri != null) {
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val coverFile = File(coversDir, "cover_${book.id}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(newCoverUri)?.use { input ->
                        FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    coverImagePath = coverFile.absolutePath
                    // Delete old cover
                    book.coverImage?.let { File(it).delete() }
                }
                val updatedBook = book.copy(title = newTitle, coverImage = coverImagePath)
                bookDao.updateBook(updatedBook)
                com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                    text = "✨ 《$newTitle》书籍信息已更新",
                    type = com.example.epubreader.ui.components.toast.ToastType.Success
                )
            }
        }
    }

    fun updateSortOrder(books: List<BookEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedBooks = books.mapIndexed { index, book ->
                book.copy(sortOrder = index)
            }
            bookDao.updateBooks(updatedBooks)
        }
    }
}

class BookshelfViewModelFactory(private val bookDao: BookDao, private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookshelfViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookshelfViewModel(bookDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
