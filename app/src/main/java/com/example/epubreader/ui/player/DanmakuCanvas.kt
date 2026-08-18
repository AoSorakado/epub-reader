package com.example.epubreader.ui.player

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.epubreader.data.anime.DanmakuItem
import kotlin.math.abs

data class DanmakuConfig(
    val isEnabled: Boolean = true,
    val opacity: Float = 1.0f,
    val fontSizeSp: Float = 17f,
    val speedFactor: Float = 1.0f,
    val maxLines: Int = 12,
    val timeOffsetMs: Long = 0L,
    val displayAreaRatio: Float = 1.0f,
    val showScroll: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true
)

private data class ProcessedDanmaku(
    val item: DanmakuItem,
    val lane: Int,
    val textWidth: Float
)

@Composable
fun DanmakuCanvas(
    danmakuList: List<DanmakuItem>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    config: DanmakuConfig,
    modifier: Modifier = Modifier
) {
    if (!config.isEnabled || danmakuList.isEmpty()) return

    // Monotonic Hardware Clock Reference
    var frameTick by remember { mutableLongStateOf(0L) }
    var basePlayerTimeMs by remember { mutableDoubleStateOf(currentPositionMs.toDouble()) }
    var baseRealtimeNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }

    // Resync on seeking or large position drift (> 400ms)
    LaunchedEffect(currentPositionMs) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val currentEstimated = if (isPlaying) {
            basePlayerTimeMs + ((nowNanos - baseRealtimeNanos) / 1_000_000.0) * config.speedFactor
        } else {
            basePlayerTimeMs
        }
        val diff = abs(currentPositionMs.toDouble() - currentEstimated)
        if (diff > 400.0 || !isPlaying) {
            basePlayerTimeMs = currentPositionMs.toDouble()
            baseRealtimeNanos = nowNanos
        }
    }

    // High refresh rate (60Hz/120Hz) frame driver
    LaunchedEffect(isPlaying) {
        basePlayerTimeMs = currentPositionMs.toDouble()
        baseRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        while (isPlaying) {
            withFrameNanos { frameNanos ->
                frameTick = frameNanos
            }
        }
    }

    // Paints: Stroke (Crisp Narrow Outline) & Fill (Bright Pure Colors)
    val strokePaint = remember {
        Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isDither = true
            style = Paint.Style.STROKE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    val fillPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isDither = true
            style = Paint.Style.FILL
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    // Pre-calculate deterministic collision-free lanes once per danmaku list
    val processedDanmaku = remember(danmakuList, config.maxLines, config.fontSizeSp, config.speedFactor) {
        val sorted = danmakuList.sortedBy { it.timeMs }
        val maxL = config.maxLines.coerceIn(3, 24)
        val scrollLaneFreeAt = LongArray(maxL) { 0L }
        val topLaneFreeAt = LongArray(maxL) { 0L }
        val bottomLaneFreeAt = LongArray(maxL) { 0L }

        val measurePaint = Paint().apply {
            textSize = config.fontSizeSp * 2.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val durationWindow = (7000L / config.speedFactor.coerceIn(0.5f, 2.5f)).toLong()

        sorted.map { item ->
            val textW = measurePaint.measureText(item.text)
            val lane = when (item.mode) {
                5 -> {
                    var chosen = 0
                    for (i in 0 until maxL) {
                        if (topLaneFreeAt[i] <= item.timeMs) {
                            chosen = i
                            break
                        }
                    }
                    topLaneFreeAt[chosen] = item.timeMs + 3500L
                    chosen
                }
                4 -> {
                    var chosen = 0
                    for (i in 0 until maxL) {
                        if (bottomLaneFreeAt[i] <= item.timeMs) {
                            chosen = i
                            break
                        }
                    }
                    bottomLaneFreeAt[chosen] = item.timeMs + 3500L
                    chosen
                }
                else -> {
                    val enterTime = (durationWindow * 0.35f).toLong()
                    var chosen = -1
                    for (i in 0 until maxL) {
                        if (scrollLaneFreeAt[i] <= item.timeMs) {
                            chosen = i
                            break
                        }
                    }
                    if (chosen == -1) {
                        var minIdx = 0
                        var minTime = scrollLaneFreeAt[0]
                        for (i in 1 until maxL) {
                            if (scrollLaneFreeAt[i] < minTime) {
                                minTime = scrollLaneFreeAt[i]
                                minIdx = i
                            }
                        }
                        chosen = minIdx
                    }
                    scrollLaneFreeAt[chosen] = item.timeMs + enterTime + 250L
                    chosen
                }
            }
            ProcessedDanmaku(item = item, lane = lane, textWidth = textW)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read frameTick to trigger draw phase re-execution on every 60Hz/120Hz display refresh
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick

        // Compute real-time sub-millisecond continuous playback time directly in draw phase
        val nowNanos = if (isPlaying) SystemClock.elapsedRealtimeNanos() else baseRealtimeNanos
        val currentContinuousMs = if (isPlaying) {
            val elapsedMs = ((nowNanos - baseRealtimeNanos) / 1_000_000.0) * config.speedFactor
            basePlayerTimeMs + elapsedMs
        } else {
            currentPositionMs.toDouble()
        }

        val adjustedTimeMs = currentContinuousMs + config.timeOffsetMs
        val durationWindowMs = (7000L / config.speedFactor.coerceIn(0.5f, 2.5f)).toDouble()

        val canvasWidth = size.width
        val canvasHeight = size.height
        val density = this.density

        val textSizePx = config.fontSizeSp * density
        strokePaint.textSize = textSizePx
        // Sharp, tight 1.2dp outline (prevents dark blurry stroke from smothering glyphs)
        strokePaint.strokeWidth = 1.2f * density
        fillPaint.textSize = textSizePx

        val lineHeight = fillPaint.fontSpacing + 6f
        val effectiveHeight = canvasHeight * config.displayAreaRatio.coerceIn(0.25f, 1.0f)
        val maxAvailableLines = (effectiveHeight / lineHeight).toInt().coerceAtMost(config.maxLines).coerceAtLeast(3)

        val windowStartMs = (adjustedTimeMs - durationWindowMs).toLong()
        val windowEndMs = adjustedTimeMs.toLong()

        val visibleItems = processedDanmaku.filter { p ->
            p.item.timeMs in windowStartMs..windowEndMs
        }

        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            val opacityAlpha = (config.opacity.coerceIn(0.1f, 1.0f) * 255).toInt().coerceIn(10, 255)
            strokePaint.color = android.graphics.Color.argb(opacityAlpha, 0, 0, 0)

            visibleItems.forEach { processed ->
                val item = processed.item
                val lane = processed.lane % maxAvailableLines

                if (item.mode == 1 && !config.showScroll) return@forEach
                if (item.mode == 5 && !config.showTop) return@forEach
                if (item.mode == 4 && !config.showBottom) return@forEach

                val rawColor = item.color and 0xFFFFFF
                val rgbColor = if (rawColor == 0 || rawColor == 0xFFFFFF || item.color == 16777215 || rawColor < 0x202020) {
                    0xFFFFFF
                } else {
                    rawColor
                }

                fillPaint.color = android.graphics.Color.argb(
                    opacityAlpha,
                    (rgbColor shr 16) and 0xFF,
                    (rgbColor shr 8) and 0xFF,
                    rgbColor and 0xFF
                )

                val text = item.text
                val textWidth = fillPaint.measureText(text)
                val elapsed = adjustedTimeMs - item.timeMs
                val progress = (elapsed / durationWindowMs).toFloat().coerceIn(0f, 1f)

                when (item.mode) {
                    5 -> {
                        // Top Fixed
                        val y = (lane + 1) * lineHeight + 12f
                        val x = (canvasWidth - textWidth) / 2f
                        nativeCanvas.drawText(text, x, y, strokePaint)
                        nativeCanvas.drawText(text, x, y, fillPaint)
                    }
                    4 -> {
                        // Bottom Fixed
                        val bottomY = canvasHeight - ((lane + 1) * lineHeight) - 16f
                        val x = (canvasWidth - textWidth) / 2f
                        nativeCanvas.drawText(text, x, bottomY, strokePaint)
                        nativeCanvas.drawText(text, x, bottomY, fillPaint)
                    }
                    else -> {
                        // Rolling Danmaku (Mode 1)
                        val y = (lane + 1) * lineHeight + 12f
                        val startX = canvasWidth
                        val endX = -textWidth
                        val currentX = startX - progress * (startX - endX)

                        nativeCanvas.drawText(text, currentX, y, strokePaint)
                        nativeCanvas.drawText(text, currentX, y, fillPaint)
                    }
                }
            }
        }
    }
}
