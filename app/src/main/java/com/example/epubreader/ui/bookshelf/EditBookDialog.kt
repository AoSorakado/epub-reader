package com.example.epubreader.ui.bookshelf

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.epubreader.data.model.BookEntity
import com.example.epubreader.data.network.WebDavClient
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditBookDialog(
    book: BookEntity,
    backdrop: Backdrop,
    isDark: Boolean = false,
    themeAccent: Color = Color(0xFF007AFF),
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866),
    showCardContainer: Boolean = true,
    onDismissRequest: () -> Unit,
    onConfirm: (newTitle: String, newAuthor: String, newSeries: String?, newCoverUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.seriesName ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val isTxt = book.filePath.endsWith(".txt", ignoreCase = true)
    val ext = if (isTxt) "txt" else "epub"
    var remoteFileSize by remember { mutableStateOf<Long?>(null) }
    var isFetchingRemoteSize by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, book.filePath, book.isWebDav) {
        if (book.isWebDav) {
            val cacheFile = File(context.cacheDir, "webdav_${book.id}.$ext")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                remoteFileSize = cacheFile.length()
            } else {
                isFetchingRemoteSize = true
                withContext(Dispatchers.IO) {
                    try {
                        val prefs = context.getSharedPreferences("liquid_settings", Context.MODE_PRIVATE)
                        val url = prefs.getString("webdav_url", "") ?: ""
                        val user = prefs.getString("webdav_user", "") ?: ""
                        val pass = prefs.getString("webdav_pass", "") ?: ""
                        if (url.isNotBlank()) {
                            val client = WebDavClient(url, user, pass)
                            val size = client.getFileSize(book.filePath)
                            if (size > 0) {
                                remoteFileSize = size
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isFetchingRemoteSize = false
                    }
                }
            }
        }
    }

    val fileSizeText = remember(book.filePath, book.isWebDav, remoteFileSize, isFetchingRemoteSize) {
        val sizeBytes = if (book.isWebDav) {
            val cacheFile = File(context.cacheDir, "webdav_${book.id}.$ext")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile.length()
            } else {
                remoteFileSize
            }
        } else {
            val f = File(book.filePath)
            if (f.exists()) f.length() else null
        }

        if (sizeBytes != null && sizeBytes > 0) {
            if (sizeBytes >= 1024 * 1024) {
                String.format(Locale.US, "%.1f MB", sizeBytes.toDouble() / (1024.0 * 1024.0))
            } else {
                String.format(Locale.US, "%.1f KB", sizeBytes.toDouble() / 1024.0)
            }
        } else if (book.isWebDav) {
            if (isFetchingRemoteSize) "获取中..." else "云端存储"
        } else {
            "未知大小"
        }
    }
    val progressText = String.format(Locale.US, "%.1f%%", (book.totalProgress * 100f).coerceIn(0f, 100f))
    val addedDateText = remember(book.addedTime) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(book.addedTime))
    }

    val containerModifier = if (showCardContainer) {
        Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(
                        refractionHeight = 16f.dp.toPx(),
                        refractionAmount = 32f.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Plain },
                shadow = {
                    Shadow(
                        radius = 20.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.35f else 0.15f)
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = if (isDark) 0.10f else 0.18f))
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.45f else 0.80f),
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.40f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    } else {
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
    }

    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily)
    ) {
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = themeAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "书籍详情与元数据",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            }
            
            // Format Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(themeAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isTxt) "TXT 格式" else "EPUB 格式",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeAccent
                )
            }
        }

        // Cover Picker Row (Liquid Glass Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.20f))
                .border(
                    width = 0.6.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.30f else 0.50f),
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.20f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    imagePickerLauncher.launch("image/*")
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail preview
            Box(
                modifier = Modifier
                    .size(44.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(selectedImageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "New Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (book.coverImage != null && File(book.coverImage).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(book.coverImage))
                            .crossfade(false)
                            .build(),
                        contentDescription = "Current Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedImageUri != null) "已选定新封面 (点击可重选)" else "更换书籍封面",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedImageUri != null) themeAccent else primaryTextColor
                )
                Text(
                    text = "支持从相册选择自定义插画或书封",
                    fontSize = 11.5.sp,
                    color = secondaryTextColor
                )
            }
        }
        
        // Title Input Box
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "书名 (Title)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.25f))
                    .border(
                        width = 0.6.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.30f else 0.50f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                        fontSize = 14.sp,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(themeAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Author and Series Inputs in a 2-column row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Author
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "作者 (Author)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.25f))
                        .border(
                            width = 0.6.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.30f else 0.50f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = author,
                        onValueChange = { author = it },
                        textStyle = TextStyle(
                            fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                            fontSize = 14.sp,
                            color = primaryTextColor,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(themeAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Series
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "系列/分组 (Series)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.25f))
                        .border(
                            width = 0.6.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.30f else 0.50f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = series,
                        onValueChange = { series = it },
                        textStyle = TextStyle(
                            fontFamily = com.example.epubreader.ui.theme.ClaudeUIFontFamily,
                            fontSize = 14.sp,
                            color = primaryTextColor,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(themeAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // File Metadata Info Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = if (isDark) 0.05f else 0.14f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "文件大小",
                    fontSize = 10.5.sp,
                    color = secondaryTextColor
                )
                Text(
                    text = fileSizeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
            Column {
                Text(
                    text = "当前进度",
                    fontSize = 10.5.sp,
                    color = secondaryTextColor
                )
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeAccent
                )
            }
            Column {
                Text(
                    text = "导入日期",
                    fontSize = 10.5.sp,
                    color = secondaryTextColor
                )
                Text(
                    text = addedDateText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
        }
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cancel button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.30f))
                    .border(
                        width = 0.6.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismissRequest()
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
            
            // Save button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(themeAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onConfirm(title, author, series.ifBlank { null }, selectedImageUri)
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存修改",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
    }
}
