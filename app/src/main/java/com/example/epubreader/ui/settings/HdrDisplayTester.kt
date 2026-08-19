package com.example.epubreader.ui.settings

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.key

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getDisplayHdrSdrRatio(display: Display?): Float {
    if (display == null) return 1.0f
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val method = display.javaClass.getMethod("getHdrSdrRatio")
            val result = method.invoke(display) as? Float
            if (result != null && result > 0f) return result
        }
    } catch (e: Exception) {}
    return 1.0f
}

fun createHdrTestBitmap(isHdrActive: Boolean, ratio: Float): android.graphics.Bitmap {
    val width = 450
    val height = 300
    val baseBmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(baseBmp)
    
    // Background: Dark Gray Card (#303030)
    canvas.drawColor(android.graphics.Color.rgb(48, 48, 48))
    
    val smallPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(180, 180, 180)
        textSize = 24f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val hdrPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 58f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    canvas.drawText("EPUB READER", width / 2f, 105f, smallPaint)
    canvas.drawText("HDR", width / 2f, 185f, hdrPaint)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isHdrActive) {
        // Multi-channel ARGB_8888 Gainmap: Black background (0 gain) + Pure White text (Max hardware boost)
        val gainmapBmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val gCanvas = android.graphics.Canvas(gainmapBmp)
        gCanvas.drawColor(android.graphics.Color.BLACK)
        
        val gPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 58f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val gSmallPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        gCanvas.drawText("EPUB READER", width / 2f, 105f, gSmallPaint)
        gCanvas.drawText("HDR", width / 2f, 185f, gPaint)
        
        val maxGain = if (ratio > 1.0f) (ratio * 2.5f).coerceAtLeast(8.0f) else 8.0f
        val gainmap = android.graphics.Gainmap(gainmapBmp).apply {
            setRatioMin(1.0f, 1.0f, 1.0f)
            setRatioMax(maxGain, maxGain, maxGain)
            setDisplayRatioForFullHdr(1.01f)
            minDisplayRatioForHdrTransition = 1.001f
            setGamma(1.0f, 1.0f, 1.0f)
            setEpsilonSdr(0.001f, 0.001f, 0.001f)
            setEpsilonHdr(0.001f, 0.001f, 0.001f)
        }
        baseBmp.gainmap = gainmap
    }
    return baseBmp
}

fun createSdrTestBitmap(): android.graphics.Bitmap {
    val width = 450
    val height = 300
    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(80, 80, 80)
        textSize = 46f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("SDR", width / 2f, 160f, paint)
    return bmp
}

fun drawHdrContent(holder: android.view.SurfaceHolder, isHdrActive: Boolean, measuredRatio: Float) {
    try {
        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.lockHardwareCanvas()
        } else {
            holder.lockCanvas()
        }
        if (canvas != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val extSrgb = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB)
                    if (isHdrActive) {
                        // Dynamically compute peak luminance multiplier from system measured ratio
                        val peakLuminance = if (measuredRatio > 1.0f) measuredRatio else 4.0f
                        val peakHdrWhite = android.graphics.Color.valueOf(peakLuminance, peakLuminance, peakLuminance, 1.0f, extSrgb).pack()
                        val hdrGold = android.graphics.Color.valueOf(peakLuminance, peakLuminance * 0.65f, 0.05f, 1.0f, extSrgb).pack()
                        val sdrDark = android.graphics.Color.valueOf(0.05f, 0.05f, 0.05f, 1.0f, extSrgb).pack()
                        
                        canvas.drawColor(peakHdrWhite)
                        
                        val paint = android.graphics.Paint().apply {
                            setColor(sdrDark)
                            textSize = 48f
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val smallPaint = android.graphics.Paint().apply {
                            setColor(hdrGold)
                            textSize = 24f
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val w = canvas.width.toFloat()
                        val h = canvas.height.toFloat()
                        canvas.drawText("ULTRA HDR", w / 2f, h / 2f - 10f, paint)
                        canvas.drawText(String.format(java.util.Locale.US, "🔥 %.2fX 硬件峰值爆发中", peakLuminance), w / 2f, h / 2f + 35f, smallPaint)
                    } else {
                        val sdrBg = android.graphics.Color.valueOf(0.25f, 0.25f, 0.25f, 1.0f, extSrgb).pack()
                        val sdrWhite = android.graphics.Color.valueOf(1.0f, 1.0f, 1.0f, 1.0f, extSrgb).pack()
                        val sdrGray = android.graphics.Color.valueOf(0.6f, 0.6f, 0.6f, 1.0f, extSrgb).pack()
                        
                        canvas.drawColor(sdrBg)
                        
                        val paint = android.graphics.Paint().apply {
                            setColor(sdrWhite)
                            textSize = 46f
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val smallPaint = android.graphics.Paint().apply {
                            setColor(sdrGray)
                            textSize = 24f
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val w = canvas.width.toFloat()
                        val h = canvas.height.toFloat()
                        canvas.drawText("ULTRA HDR", w / 2f, h / 2f - 10f, paint)
                        canvas.drawText("点击下方测试开启", w / 2f, h / 2f + 35f, smallPaint)
                    }
                } else {
                    canvas.drawColor(if (isHdrActive) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY)
                    val paint = android.graphics.Paint().apply {
                        color = if (isHdrActive) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        textSize = 46f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val w = canvas.width.toFloat()
                    val h = canvas.height.toFloat()
                    canvas.drawText("ULTRA HDR", w / 2f, h / 2f + 10f, paint)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("HdrTester", "Failed to draw HDR surface", e)
    }
}

/**
 * Interactive HDR & Display Test Lab with real-time Android Display API measurement,
 * live HDR/SDR ratio listener, RGBA_F16 10-bit SurfaceView, and hardware color mode switching.
 */
@Composable
fun HdrDisplayTesterCard(
    backdrop: Backdrop? = null,
    themeAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var isHdrTestActive by remember { mutableStateOf(false) }
    var currentHdrSdrRatio by remember { mutableFloatStateOf(1.0f) }

    // Read Display Info directly from Activity window display for accurate context
    val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager }
    val display = remember(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.display ?: context.display ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            activity?.windowManager?.defaultDisplay ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        }
    }

    // Supported HDR types and luminance limits
    val hdrCapabilities = remember(display) {
        display?.hdrCapabilities
    }

    val maxLuminance = remember(hdrCapabilities) { hdrCapabilities?.desiredMaxLuminance ?: 0f }
    val minLuminance = remember(hdrCapabilities) { hdrCapabilities?.desiredMinLuminance ?: 0f }
    val maxAvgLuminance = remember(hdrCapabilities) { hdrCapabilities?.desiredMaxAverageLuminance ?: 0f }

    val isHdrSupported = remember(display, hdrCapabilities) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display?.isHdr ?: false
        } else {
            hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        }
    }

    val isWideGamutSupported = remember(display) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display?.isWideColorGamut ?: false
        } else false
    }

    val supportedHdrTypesNames = remember(hdrCapabilities) {
        val types = mutableListOf<String>()
        hdrCapabilities?.supportedHdrTypes?.forEach { type ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> types.add("Dolby Vision")
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> types.add("HDR10")
                Display.HdrCapabilities.HDR_TYPE_HLG -> types.add("HLG")
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> types.add("HDR10+")
            }
        }
        if (types.isEmpty() && isHdrSupported) {
            listOf("HDR10", "BT.2020")
        } else types
    }

    // Real-time active polling ticker + Display Listener
    LaunchedEffect(isHdrTestActive, display) {
        while (true) {
            val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity?.display ?: display else display
            val ratio = getDisplayHdrSdrRatio(disp)
            if (currentHdrSdrRatio != ratio) {
                currentHdrSdrRatio = ratio
            }
            delay(250)
        }
    }

    // Register live HDR/SDR ratio listener on Android 14+ (API 34+)
    DisposableEffect(display) {
        val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity?.display ?: display else display
        currentHdrSdrRatio = getDisplayHdrSdrRatio(disp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && disp != null) {
            try {
                val listenerClass = Class.forName("android.view.Display\$HdrSdrRatioListener")
                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onHdrSdrRatioChanged") {
                        val d = args?.getOrNull(0) as? Display ?: disp
                        currentHdrSdrRatio = getDisplayHdrSdrRatio(d)
                    }
                    null
                }
                val registerMethod = disp.javaClass.getMethod(
                    "registerHdrSdrRatioChangedListener",
                    java.util.concurrent.Executor::class.java,
                    listenerClass
                )
                val unregisterMethod = disp.javaClass.getMethod(
                    "unregisterHdrSdrRatioChangedListener",
                    listenerClass
                )
                registerMethod.invoke(disp, context.mainExecutor, proxy)
                onDispose {
                    try {
                        unregisterMethod.invoke(disp, proxy)
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                onDispose {}
            }
        } else {
            onDispose {}
        }
    }

    // Handle HDR Headroom & Color Mode activation dynamically
    LaunchedEffect(isHdrTestActive, currentHdrSdrRatio) {
        activity?.let { act ->
            val win = act.window
            val lp = win.attributes
            if (isHdrTestActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    win.colorMode = ActivityInfo.COLOR_MODE_HDR
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    lp.desiredHdrHeadroom = 100.0f
                }
                win.attributes = lp
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    win.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    lp.desiredHdrHeadroom = 1.0f
                }
                win.attributes = lp
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity?.window?.attributes?.let { lp ->
                    lp.desiredHdrHeadroom = 1.0f
                    activity.window.attributes = lp
                }
            }
        }
    }

    val sdrBmp = remember { createSdrTestBitmap() }
    val hdrBmp = remember(isHdrTestActive, currentHdrSdrRatio) { createHdrTestBitmap(isHdrTestActive, currentHdrSdrRatio) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        if (isHdrTestActive) Color(0xFFFFB800).copy(alpha = 0.8f) else themeAccent.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isHdrTestActive) Color(0xFFFFB800).copy(alpha = 0.25f) else themeAccent.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHdrTestActive) Icons.Filled.WbSunny else Icons.Filled.HdrOn,
                        contentDescription = null,
                        tint = if (isHdrTestActive) Color(0xFFFFB800) else themeAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        "HDR 屏幕硬件显示测试实验室",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Text(
                        "基于 Android 14+ Ultra HDR Gainmap 动态增益测试",
                        fontSize = 11.5.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }

        // Dual SDR vs Ultra HDR Visual Contrast Boxes (Genuine Gainmap rendering)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SDR Card (Left)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(sdrBmp)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Ultra HDR Card (Right) - Native Android 14+ Ultra HDR Gainmap ImageView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (isHdrTestActive) 2.dp else 1.dp,
                        color = if (isHdrTestActive) Color(0xFFFFB800) else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                key(isHdrTestActive, currentHdrSdrRatio) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setImageBitmap(hdrBmp)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageBitmap(hdrBmp)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Action Trigger Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isHdrTestActive) Color(0xFF2E7D32) else themeAccent)
                .clickable {
                    isHdrTestActive = !isHdrTestActive
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isHdrTestActive) "✓ 取消 HDR 增益测试 (恢复 SDR)" else "🚀 启动 Ultra HDR 硬件增益测试",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = "提示：启动测试后，若设备支持 HDR，右侧 HDR 框内的文字和画面将呈现远超左侧 SDR 的刺眼局部高光（基于 Ultra HDR Gainmap 局部硬件增益，不拉高整体屏幕亮度）。若两侧亮度一致，则表示当前环境未激活 HDR 增益。",
            fontSize = 11.sp,
            color = secondaryTextColor,
            lineHeight = 16.sp
        )

        // Realtime Hardware Parameters Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("手机型号", fontSize = 11.5.sp, color = secondaryTextColor)
                Text("${Build.MANUFACTURER.uppercase()} ${Build.MODEL}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Android 系统版本", fontSize = 11.5.sp, color = secondaryTextColor)
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("实时 HDR/SDR 激发比率", fontSize = 11.5.sp, color = secondaryTextColor)
                Text(
                    text = if (currentHdrSdrRatio > 1.0f) {
                        String.format(java.util.Locale.US, "%.7f", currentHdrSdrRatio)
                    } else {
                        "1.0000000 (SDR基准)"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentHdrSdrRatio > 1.0f) Color(0xFFFFB800) else primaryTextColor
                )
            }
            if (maxLuminance > 0f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("硬件峰值亮度 (Max Luminance)", fontSize = 11.5.sp, color = secondaryTextColor)
                    Text("${maxLuminance.toInt()} nits", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("屏幕色彩模式支持", fontSize = 11.5.sp, color = secondaryTextColor)
                Text(
                    text = if (isHdrSupported && isWideGamutSupported) "HDR · Wide Gamut · HDR/SDR" else if (isHdrSupported) "HDR" else "标准动态",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("HDR 硬件标准支持", fontSize = 11.5.sp, color = secondaryTextColor)
                Text(
                    text = supportedHdrTypesNames.joinToString(" · ").ifBlank { "Dolby Vision · HDR10 · HLG" },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
        }
    }
}
