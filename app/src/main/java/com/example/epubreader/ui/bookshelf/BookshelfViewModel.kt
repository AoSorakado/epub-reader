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
                    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

                    val timestamp = System.currentTimeMillis()
                    val originalName = getFileNameFromUri(uri, context) ?: "book_$timestamp.epub"
                    val isTxt = originalName.endsWith(".txt", ignoreCase = true)
                    val targetExt = if (isTxt) ".txt" else ".epub"
                    val localBookFile = File(booksDir, "book_${timestamp}${targetExt}")

                    // 1. Copy the selected file to internal storage
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localBookFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 2. Parse the book (EPUB or TXT)
                    val book = if (isTxt) {
                        com.example.epubreader.data.parser.TxtParser.parse(localBookFile)
                    } else {
                        EpubParser.parse(localBookFile)
                    }

                    // 3. Save Cover Image if exists
                    var coverImagePath: String? = null
                    if (book.coverImage != null) {
                        val coverFile = File(coversDir, "cover_${timestamp}.jpg")
                        coverFile.writeBytes(book.coverImage)
                        coverImagePath = coverFile.absolutePath
                    }

                    // 4. Create and insert BookEntity
                    val bookEntity = BookEntity(
                        title = book.title,
                        author = book.author,
                        coverImage = coverImagePath,
                        filePath = localBookFile.absolutePath,
                        isWebDav = false,
                        addedTime = timestamp,
                        lastReadTime = 0
                    )
                    bookDao.insertBook(bookEntity)
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "📖 成功导入《${book.title}》",
                        type = com.example.epubreader.ui.components.toast.ToastType.Success
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    com.example.epubreader.ui.components.toast.GlobalToastManager.show(
                        text = "导入书籍失败: ${e.localizedMessage}",
                        type = com.example.epubreader.ui.components.toast.ToastType.Error
                    )
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        return name
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

    fun updateBookInfo(
        book: BookEntity,
        newTitle: String,
        newAuthor: String,
        newSeries: String?,
        newCoverUri: Uri?,
        context: Context
    ) {
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
                    // Delete old custom cover if different
                    if (book.coverImage != null && book.coverImage != coverImagePath) {
                        File(book.coverImage).delete()
                    }
                }
                val updatedBook = book.copy(
                    title = newTitle.ifBlank { book.title },
                    author = newAuthor.ifBlank { "未知作者" },
                    seriesName = newSeries?.ifBlank { null },
                    coverImage = coverImagePath
                )
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
