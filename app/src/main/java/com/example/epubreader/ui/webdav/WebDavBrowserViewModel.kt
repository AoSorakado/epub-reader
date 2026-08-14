package com.example.epubreader.ui.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.data.model.network.WebDavResource
import com.example.epubreader.data.network.WebDavClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WebDavBrowserViewModel : ViewModel() {

    private var client: WebDavClient? = null

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<WebDavResource>>(emptyList())
    val files: StateFlow<List<WebDavResource>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect(url: String, user: String, pass: String) {
        client = WebDavClient(url, user, pass)
        _currentPath.value = ""
        loadFiles()
    }

    fun navigateTo(folder: WebDavResource) {
        if (!folder.isDirectory) return
        _currentPath.value = folder.path
        loadFiles()
    }

    fun navigateUp() {
        val path = _currentPath.value
        if (path.isEmpty() || path == "/") return
        
        // Remove trailing slash if exists
        val cleanPath = path.trimEnd('/')
        // Find last slash
        val lastSlash = cleanPath.lastIndexOf('/')
        
        if (lastSlash <= 0) {
            _currentPath.value = ""
        } else {
            _currentPath.value = cleanPath.substring(0, lastSlash + 1)
        }
        loadFiles()
    }

    private fun loadFiles() {
        val currentClient = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val list = currentClient.listFiles(_currentPath.value)
                _files.value = list.sortedBy { !it.isDirectory } // folders first
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load files"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun getClient(): WebDavClient? = client
}
