package com.example.epubreader.ui.bookshelf

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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import java.io.File

@Composable
fun EditBookDialog(
    book: BookEntity,
    backdrop: Backdrop,
    isDark: Boolean = false,
    themeAccent: Color = Color(0xFF007AFF),
    primaryTextColor: Color = Color(0xFF1E1E24),
    secondaryTextColor: Color = Color(0xFF543866),
    onDismissRequest: () -> Unit,
    onConfirm: (String, Uri?) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier
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
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = themeAccent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "编辑信息",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        }
        
        // Title Input Box (Liquid Glass Style)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "书名 (Title)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.25f))
                    .border(
                        width = 0.6.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.30f else 0.60f),
                                Color.White.copy(alpha = if (isDark) 0.10f else 0.25f)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(themeAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Cover Picker Row (Liquid Glass Style)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "书籍封面",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail preview
                Box(
                    modifier = Modifier
                        .size(42.dp, 56.dp)
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
                        text = if (selectedImageUri != null) "已选择新封面" else "更换书籍封面",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedImageUri != null) themeAccent else primaryTextColor
                    )
                    Text(
                        text = "点击从相册选取本地图片",
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.30f))
                    .border(
                        width = 0.6.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismissRequest()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
            
            // Save button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(themeAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onConfirm(title, selectedImageUri)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
