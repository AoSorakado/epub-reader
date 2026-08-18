package com.example.epubreader.ui.components.perf

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class LivePerfMetrics(
    val fps: Float = 60f,
    val frameTimeMs: Float = 16.6f,
    val cpuPercent: Float = 0f,
    val threadCount: Int = 0,
    val jvmHeapUsedMb: Long = 0,
    val nativeHeapUsedMb: Long = 0,
    val totalAppMemoryMb: Long = 0,
    val batteryTempCelsius: Float = 0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false
)

@Composable
fun GlobalPerformanceMonitorHud(
    isEnabled: Boolean,
    backdrop: Backdrop? = null,
    isDark: Boolean = true,
    themeAccent: Color = Color(0xFF6366F1),
    modifier: Modifier = Modifier
) {
    if (!isEnabled) return

    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(120f) }

    var metrics by remember { mutableStateOf(LivePerfMetrics()) }

    // Real-time High Refresh FPS Monitor
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsTimestampNanos by remember { mutableLongStateOf(0L) }
    var currentFps by remember { mutableFloatStateOf(60f) }
    var currentFrameTimeMs by remember { mutableFloatStateOf(16.6f) }

    LaunchedEffect(Unit) {
        var lastNanos = SystemClock.elapsedRealtimeNanos()
        lastFpsTimestampNanos = lastNanos
        while (isActive) {
            withFrameNanos { nowNanos ->
                frameCount++
                val deltaNanos = nowNanos - lastNanos
                lastNanos = nowNanos
                if (deltaNanos > 0) {
                    currentFrameTimeMs = deltaNanos / 1_000_000f
                }

                val totalDeltaNanos = nowNanos - lastFpsTimestampNanos
                if (totalDeltaNanos >= 400_000_000L) { // Update FPS every 400ms
                    currentFps = (frameCount * 1_000_000_000f) / totalDeltaNanos
                    frameCount = 0
                    lastFpsTimestampNanos = nowNanos
                }
            }
        }
    }

    // System Resources Poller (CPU, RAM, Temp, Battery) every 500ms
    LaunchedEffect(Unit) {
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var lastCpuTime = 0L
        var lastSampleTime = 0L

        while (isActive) {
            withContext(Dispatchers.IO) {
                // 1. Process CPU Usage from /proc/self/stat
                var cpuUsage = 0f
                try {
                    val statText = File("/proc/self/stat").readText().trim().split("\\s+".toRegex())
                    if (statText.size >= 15) {
                        val utime = statText[13].toLong()
                        val stime = statText[14].toLong()
                        val nowTime = SystemClock.elapsedRealtime()
                        val totalCpuTicks = utime + stime

                        if (lastCpuTime > 0 && nowTime > lastSampleTime) {
                            val cpuDelta = totalCpuTicks - lastCpuTime
                            val timeDeltaMs = nowTime - lastSampleTime
                            // 10ms per tick on standard Linux kernel
                            cpuUsage = ((cpuDelta * 1000f) / (timeDeltaMs * numCores)).coerceIn(0f, 100f)
                        }
                        lastCpuTime = totalCpuTicks
                        lastSampleTime = nowTime
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                // 2. Memory Breakdown
                val runtime = Runtime.getRuntime()
                val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val nativeUsedMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
                val totalMemMb = jvmUsedMb + nativeUsedMb

                // 3. Battery & Temperature
                val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, batteryFilter)
                val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val tempCelsius = rawTemp / 10f
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val batteryPct = if (scale > 0) (level * 100) / scale else level
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                metrics = LivePerfMetrics(
                    fps = currentFps,
                    frameTimeMs = currentFrameTimeMs,
                    cpuPercent = cpuUsage,
                    threadCount = Thread.activeCount(),
                    jvmHeapUsedMb = jvmUsedMb,
                    nativeHeapUsedMb = nativeUsedMb,
                    totalAppMemoryMb = totalMemMb,
                    batteryTempCelsius = tempCelsius,
                    batteryLevel = batteryPct,
                    isCharging = isCharging
                )
            }
            delay(500L)
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val fpsColor = when {
        metrics.fps >= 55f -> Color(0xFF4ADE80) // Smooth Green
        metrics.fps >= 35f -> Color(0xFFFBBF24) // Warning Yellow
        else -> Color(0xFFF87171)               // Lag Red
    }

    val cpuColor = when {
        metrics.cpuPercent <= 25f -> Color(0xFF4ADE80)
        metrics.cpuPercent <= 60f -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    val tempColor = when {
        metrics.batteryTempCelsius <= 37f -> Color(0xFF38BDF8)
        metrics.batteryTempCelsius <= 42f -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }

    // Only wrap the floating capsule/card without any full-screen blocking layer
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    offsetX.roundToInt().coerceIn(16, (screenWidthPx - 100).roundToInt().coerceAtLeast(16)),
                    offsetY.roundToInt().coerceIn(60, (screenHeightPx - 160).roundToInt().coerceAtLeast(60))
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(16f, (screenWidthPx - 100f).coerceAtLeast(16f))
                    offsetY = (offsetY + dragAmount.y).coerceIn(60f, (screenHeightPx - 160f).coerceAtLeast(60f))
                }
            }
    ) {
        if (!isExpanded) {
            // --- 1. Mini Glass Pill HUD ---
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(12.dp.toPx())
                                    lens(refractionHeight = 12.dp.toPx(), refractionAmount = 24.dp.toPx())
                                },
                                highlight = { Highlight.Plain },
                                shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.35f)) },
                                onDrawSurface = { drawRect(if (isDark) Color(0xFF0F172A).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f)) }
                            )
                        } else {
                            Modifier.background(
                                if (isDark) Color(0xFF0F172A).copy(alpha = 0.88f)
                                else Color(0xFFF8FAFC).copy(alpha = 0.92f)
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(themeAccent.copy(alpha = 0.7f), Color.White.copy(alpha = 0.3f))
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FPS Badge
                Text(
                    text = "${metrics.fps.roundToInt()} FPS",
                    color = fpsColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.Gray))

                // CPU Badge
                Text(
                    text = "CPU ${metrics.cpuPercent.roundToInt()}%",
                    color = cpuColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )

                Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.Gray))

                // RAM Badge
                Text(
                    text = "${metrics.totalAppMemoryMb}MB",
                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )

                if (metrics.batteryTempCelsius > 0) {
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.Gray))
                    Text(
                        text = "${"%.1f".format(metrics.batteryTempCelsius)}°C",
                        color = tempColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = "Expand",
                    tint = themeAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            // --- 2. Expanded Glass Diagnostics Panel ---
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(20.dp) },
                                effects = {
                                    vibrancy()
                                    blur(16.dp.toPx())
                                    lens(refractionHeight = 16.dp.toPx(), refractionAmount = 32.dp.toPx())
                                },
                                highlight = { Highlight.Plain },
                                shadow = { Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.45f)) },
                                onDrawSurface = { drawRect(if (isDark) Color(0xFF0F172A).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)) }
                            )
                        } else {
                            Modifier.background(
                                if (isDark) Color(0xFF0F172A).copy(alpha = 0.94f)
                                else Color.White.copy(alpha = 0.96f)
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(themeAccent.copy(alpha = 0.8f), Color.White.copy(alpha = 0.35f))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = themeAccent, modifier = Modifier.size(18.dp))
                        Text(
                            text = "实时能耗与性能监视",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Collapse",
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { isExpanded = false }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Grid Metrics Row 1: FPS & Frame Latency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        label = "刷新率 (FPS)",
                        value = "${metrics.fps.roundToInt()} fps",
                        subValue = "${"%.1f".format(metrics.frameTimeMs)} ms/frame",
                        valueColor = fpsColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "CPU 占用",
                        value = "${"%.1f".format(metrics.cpuPercent)} %",
                        subValue = "${metrics.threadCount} 个活跃线程",
                        valueColor = cpuColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Grid Metrics Row 2: RAM & Battery / Temp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        label = "应用内存 (RAM)",
                        value = "${metrics.totalAppMemoryMb} MB",
                        subValue = "JVM: ${metrics.jvmHeapUsedMb}M | Nat: ${metrics.nativeHeapUsedMb}M",
                        valueColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "电池 / 发热温度",
                        value = if (metrics.batteryTempCelsius > 0) "${"%.1f".format(metrics.batteryTempCelsius)} °C" else "--",
                        subValue = "电量: ${metrics.batteryLevel}% ${if (metrics.isCharging) "⚡充" else ""}",
                        valueColor = tempColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Diagnostic Tip Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = themeAccent, modifier = Modifier.size(13.dp))
                    Text(
                        text = if (metrics.cpuPercent > 50f) "⚠️ 当前界面 CPU 负荷偏高，建议排查"
                        else "✨ 渲染管线负载正常，低功耗运行中",
                        fontSize = 10.sp,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    subValue: String,
    valueColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
            .padding(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subValue,
            fontSize = 9.5.sp,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace
        )
    }
}
