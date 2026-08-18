package com.example.epubreader.ui.player

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubreader.data.anime.DanmakuItem
import kotlin.math.abs
import kotlin.math.max

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
    val textWidth: Float,
    val rgbColor: Int
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val canvasWidth = constraints.maxWidth.toFloat().coerceAtLeast(100f)
        val canvasHeight = constraints.maxHeight.toFloat().coerceAtLeast(100f)

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

        // Set Paint text sizes once per config/density
        val textSizePx = with(density) { config.fontSizeSp.sp.toPx() }
        val strokeWidthPx = with(density) { 1.2f.dp.toPx() }
        strokePaint.textSize = textSizePx
        strokePaint.strokeWidth = strokeWidthPx
        fillPaint.textSize = textSizePx

        val lineGapPx = with(density) { 6.dp.toPx() }
        val topPaddingPx = with(density) { 12.dp.toPx() }
        val bottomPaddingPx = with(density) { 16.dp.toPx() }

        val lineHeight = fillPaint.fontSpacing + lineGapPx
        val effectiveHeight = canvasHeight * config.displayAreaRatio.coerceIn(0.25f, 1.0f)
        val maxAvailableLines = (effectiveHeight / lineHeight).toInt()
            .coerceAtMost(config.maxLines)
            .coerceAtLeast(3)

        val durationWindowMs = (7000.0 / config.speedFactor.coerceIn(0.5f, 2.5f))

        // Pre-calculate deterministic collision-free lanes & text metrics
        val processedDanmaku = remember(
            danmakuList,
            maxAvailableLines,
            textSizePx,
            config.speedFactor,
            canvasWidth
        ) {
            val sorted = danmakuList.sortedBy { it.timeMs }
            val measurePaint = Paint().apply {
                this.textSize = textSizePx
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            // Multi-item active sliding window for each lane
            class ActiveDanmaku(
                val enterTimeMs: Long,
                val width: Float,
                val speed: Float,
                val exitTimeMs: Long
            )

            val scrollLaneActives = Array(maxAvailableLines) { mutableListOf<ActiveDanmaku>() }
            val topLanesFreeAt = LongArray(maxAvailableLines) { -100000L }
            val bottomLanesFreeAt = LongArray(maxAvailableLines) { -100000L }

            val safetyPaddingPx = 36f // 36px safe distance buffer to prevent any touching

            sorted.map { item ->
                val textW = measurePaint.measureText(item.text)
                val rawColor = item.color and 0xFFFFFF
                val rgbColor = if (rawColor == 0 || rawColor == 0xFFFFFF || item.color == 16777215 || rawColor < 0x202020) {
                    0xFFFFFF
                } else {
                    rawColor
                }

                val lane = when (item.mode) {
                    5 -> {
                        // Top Fixed Danmaku (stays for 3500ms)
                        var chosenLane = 0
                        var earliestTime = Long.MAX_VALUE

                        for (i in 0 until maxAvailableLines) {
                            if (topLanesFreeAt[i] <= item.timeMs) {
                                chosenLane = i
                                break
                            }
                            if (topLanesFreeAt[i] < earliestTime) {
                                earliestTime = topLanesFreeAt[i]
                                chosenLane = i
                            }
                        }
                        topLanesFreeAt[chosenLane] = item.timeMs + 3500L
                        chosenLane
                    }
                    4 -> {
                        // Bottom Fixed Danmaku (stays for 3500ms)
                        var chosenLane = 0
                        var earliestTime = Long.MAX_VALUE

                        for (i in 0 until maxAvailableLines) {
                            if (bottomLanesFreeAt[i] <= item.timeMs) {
                                chosenLane = i
                                break
                            }
                            if (bottomLanesFreeAt[i] < earliestTime) {
                                earliestTime = bottomLanesFreeAt[i]
                                chosenLane = i
                            }
                        }
                        bottomLanesFreeAt[chosenLane] = item.timeMs + 3500L
                        chosenLane
                    }
                    else -> {
                        // Rolling Danmaku (Mode 1)
                        val speed = (canvasWidth + textW) / durationWindowMs.toFloat()
                        val exitTime = item.timeMs + durationWindowMs.toLong()

                        var chosenLane = -1
                        var leastActiveCount = Int.MAX_VALUE
                        var fallbackLane = 0

                        for (i in 0 until maxAvailableLines) {
                            val activeList = scrollLaneActives[i]
                            // Purge any danmakus that have already left the screen
                            activeList.removeAll { it.exitTimeMs <= item.timeMs }

                            if (activeList.isEmpty()) {
                                chosenLane = i
                                break
                            }

                            // Check collision against EVERY active danmaku currently on screen in this lane
                            var hasCollision = false
                            for (prev in activeList) {
                                val deltaT = (item.timeMs - prev.enterTimeMs).toFloat()
                                if (deltaT < 0) {
                                    hasCollision = true
                                    break
                                }

                                // 1. Tail entrance condition: previous tail must be completely inside screen
                                val timeForPrevTailToEnter = (prev.width + safetyPaddingPx) / prev.speed
                                if (deltaT < timeForPrevTailToEnter) {
                                    hasCollision = true
                                    break
                                }

                                // 2. Overtaking condition: if current is faster, it must not catch up before prev exits
                                if (speed > prev.speed) {
                                    val timeToCatchUp = (canvasWidth + safetyPaddingPx) / speed
                                    val timeRemainingForPrev = (prev.exitTimeMs - item.timeMs).toFloat()
                                    if (timeToCatchUp < timeRemainingForPrev) {
                                        hasCollision = true
                                        break
                                    }
                                }
                            }

                            if (!hasCollision) {
                                chosenLane = i
                                break
                            } else {
                                if (activeList.size < leastActiveCount) {
                                    leastActiveCount = activeList.size
                                    fallbackLane = i
                                }
                            }
                        }

                        val finalLane = if (chosenLane != -1) chosenLane else fallbackLane
                        scrollLaneActives[finalLane].add(
                            ActiveDanmaku(
                                enterTimeMs = item.timeMs,
                                width = textW,
                                speed = speed,
                                exitTimeMs = exitTime
                            )
                        )
                        finalLane
                    }
                }

                ProcessedDanmaku(
                    item = item,
                    lane = lane,
                    textWidth = textW,
                    rgbColor = rgbColor
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
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
            val windowStartMs = (adjustedTimeMs - durationWindowMs).toLong()
            val windowEndMs = adjustedTimeMs.toLong()

            // O(log N) Binary Search for the start & end indices of visible items in the sorted list
            // Achieves ZERO memory allocation (no filter, no new ArrayList, 0 GC overhead)
            var low = 0
            var high = processedDanmaku.size - 1
            var startIndex = processedDanmaku.size
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (processedDanmaku[mid].item.timeMs >= windowStartMs) {
                    startIndex = mid
                    high = mid - 1
                } else {
                    low = mid + 1
                }
            }

            low = 0
            high = processedDanmaku.size - 1
            var endIndex = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (processedDanmaku[mid].item.timeMs <= windowEndMs) {
                    endIndex = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            if (startIndex <= endIndex && startIndex in processedDanmaku.indices) {
                drawIntoCanvas { composeCanvas ->
                    val nativeCanvas = composeCanvas.nativeCanvas
                    val opacityAlpha = (config.opacity.coerceIn(0.1f, 1.0f) * 255).toInt().coerceIn(10, 255)
                    strokePaint.color = android.graphics.Color.argb(opacityAlpha, 0, 0, 0)

                    for (idx in startIndex..endIndex) {
                        val processed = processedDanmaku[idx]
                        val item = processed.item
                        val lane = processed.lane

                        if (item.mode == 1 && !config.showScroll) continue
                        if (item.mode == 5 && !config.showTop) continue
                        if (item.mode == 4 && !config.showBottom) continue

                        val rgbColor = processed.rgbColor
                        fillPaint.color = android.graphics.Color.argb(
                            opacityAlpha,
                            (rgbColor shr 16) and 0xFF,
                            (rgbColor shr 8) and 0xFF,
                            rgbColor and 0xFF
                        )

                        val text = item.text
                        val textWidth = processed.textWidth // Reuse precomputed width (0 native measure calls)
                        val elapsed = adjustedTimeMs - item.timeMs
                        val progress = (elapsed / durationWindowMs).toFloat().coerceIn(0f, 1f)

                        when (item.mode) {
                            5 -> {
                                // Top Fixed
                                val y = (lane + 1) * lineHeight + topPaddingPx
                                val x = (canvasWidth - textWidth) / 2f
                                nativeCanvas.drawText(text, x, y, strokePaint)
                                nativeCanvas.drawText(text, x, y, fillPaint)
                            }
                            4 -> {
                                // Bottom Fixed
                                val bottomY = canvasHeight - ((lane + 1) * lineHeight) - bottomPaddingPx
                                val x = (canvasWidth - textWidth) / 2f
                                nativeCanvas.drawText(text, x, bottomY, strokePaint)
                                nativeCanvas.drawText(text, x, bottomY, fillPaint)
                            }
                            else -> {
                                // Rolling Danmaku (Mode 1)
                                val y = (lane + 1) * lineHeight + topPaddingPx
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
    }
}

