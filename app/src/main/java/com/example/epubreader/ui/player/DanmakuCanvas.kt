package com.example.epubreader.ui.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.epubreader.data.anime.DanmakuItem

data class DanmakuConfig(
    val isEnabled: Boolean = true,
    val opacity: Float = 0.85f,
    val fontSizeSp: Float = 16f,
    val speedFactor: Float = 1.0f,
    val maxLines: Int = 10,
    val timeOffsetMs: Long = 0L,
    val showScroll: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true
)

private class ActiveDanmaku(
    val item: DanmakuItem,
    val startPlaybackTimeMs: Long,
    val lineIndex: Int,
    var textWidth: Float = 0f
)

@Composable
fun DanmakuCanvas(
    danmakuList: List<DanmakuItem>,
    currentPositionMs: Long,
    config: DanmakuConfig,
    modifier: Modifier = Modifier
) {
    if (!config.isEnabled || danmakuList.isEmpty()) return

    val adjustedPositionMs = currentPositionMs + config.timeOffsetMs
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
        }
    }

    // Window of active danmaku (within 8 seconds of current playback time)
    val durationWindowMs = (7000L / config.speedFactor).toLong()

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val density = this.density

        paint.textSize = config.fontSizeSp * density
        val lineHeight = paint.fontSpacing + 4f
        val maxAvailableLines = (canvasHeight / lineHeight).toInt().coerceAtMost(config.maxLines).coerceAtLeast(3)

        // Filter danmakus within the active sliding time window
        val visibleItems = danmakuList.filter { item ->
            val offsetTime = item.timeMs - adjustedPositionMs
            offsetTime in -durationWindowMs..0L
        }

        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas

            visibleItems.forEach { item ->
                // Check mode filters
                if (item.mode == 1 && !config.showScroll) return@forEach
                if (item.mode == 5 && !config.showTop) return@forEach
                if (item.mode == 4 && !config.showBottom) return@forEach

                paint.color = item.color
                paint.alpha = (config.opacity * 255).toInt().coerceIn(10, 255)

                val text = item.text
                val textWidth = paint.measureText(text)

                val elapsed = adjustedPositionMs - item.timeMs
                val progress = (elapsed.toFloat() / durationWindowMs.toFloat()).coerceIn(0f, 1f)

                val lineIndex = (item.text.hashCode().let { if (it < 0) -it else it }) % maxAvailableLines
                val y = (lineIndex + 1) * lineHeight + 20f

                when (item.mode) {
                    5 -> {
                        // Top Fixed Danmaku
                        val x = (canvasWidth - textWidth) / 2f
                        nativeCanvas.drawText(text, x, y, paint)
                    }
                    4 -> {
                        // Bottom Fixed Danmaku
                        val bottomY = canvasHeight - ((lineIndex + 1) * lineHeight) - 30f
                        val x = (canvasWidth - textWidth) / 2f
                        nativeCanvas.drawText(text, x, bottomY, paint)
                    }
                    else -> {
                        // Rolling Scroll Danmaku (Right to Left)
                        val totalDistance = canvasWidth + textWidth
                        val x = canvasWidth - (progress * totalDistance)
                        nativeCanvas.drawText(text, x, y, paint)
                    }
                }
            }
        }
    }
}
