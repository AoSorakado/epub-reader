package com.example.epubreader.util

import android.util.Log

object PerfLog {
    private const val TAG = "EPUB_PERF"

    fun logAction(screen: String, action: String, details: String = "") {
        val message = if (details.isNotBlank()) "[$screen] $action | $details" else "[$screen] $action"
        Log.d(TAG, message)
    }

    fun logJank(screen: String, component: String, durationMs: Long, reason: String = "") {
        Log.w(TAG, "⚠️ JANK [$screen - $component] Render took ${durationMs}ms | $reason")
    }

    fun logPlayback(title: String, res: String, codec: String, bitrateMbps: Double, fps: Double) {
        Log.i(TAG, "🎬 [Player] Playing '$title' ($res $codec) | Live Bitrate: ${"%.2f".format(bitrateMbps)} Mbps | FPS: ${"%.1f".format(fps)}")
    }
}
