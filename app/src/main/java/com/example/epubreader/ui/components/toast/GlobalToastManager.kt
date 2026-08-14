package com.example.epubreader.ui.components.toast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ToastType {
    object Info : ToastType()
    object Success : ToastType()
    object Error : ToastType()
    object Syncing : ToastType()
    object Health : ToastType()
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val type: ToastType = ToastType.Info,
    val durationMs: Long = 3000L,
    val isPersistent: Boolean = false
)

object GlobalToastManager {
    private val _currentToast = MutableStateFlow<ToastMessage?>(null)
    val currentToast: StateFlow<ToastMessage?> = _currentToast.asStateFlow()

    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show(text: String, type: ToastType = ToastType.Info, durationMs: Long = 3000L) {
        dismissJob?.cancel()
        _currentToast.value = ToastMessage(text = text, type = type, durationMs = durationMs)
        if (durationMs > 0) {
            dismissJob = scope.launch {
                delay(durationMs)
                _currentToast.value = null
            }
        }
    }

    fun showSyncing(text: String) {
        dismissJob?.cancel()
        _currentToast.value = ToastMessage(text = text, type = ToastType.Syncing, isPersistent = true)
    }

    fun dismiss() {
        dismissJob?.cancel()
        _currentToast.value = null
    }
}
