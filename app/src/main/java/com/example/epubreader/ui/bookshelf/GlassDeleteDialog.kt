package com.example.epubreader.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubreader.ui.components.liquid.LiquidButton
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

@Composable
fun GlassDeleteDialog(
    bookTitle: String,
    backdrop: LayerBackdrop,
    isDark: Boolean = false,
    primaryTextColor: Color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24),
    secondaryTextColor: Color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
    themeAccent: Color = Color(0xFF6366F1),
    isSeries: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dialogChildBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 330.dp)
            .clip(RoundedCornerShape(26.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(26.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(
                        refractionHeight = 22.dp.toPx(),
                        refractionAmount = 40.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Plain },
                shadow = {
                    Shadow(
                        radius = 28.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 16.dp,
                        alpha = if (isDark) 0.35f else 0.20f
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = if (isDark) 0.10f else 0.18f))
                },
                exportedBackdrop = dialogChildBackdrop
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.65f else 0.85f),
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.30f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* prevent clicking through */ }
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Danger Warning Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF453A).copy(alpha = if (isDark) 0.18f else 0.12f))
                    .border(0.8.dp, Color(0xFFFF453A).copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF453A),
                    modifier = Modifier.size(28.dp)
                )
            }

            // 2. Title & Message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isSeries) "删除全系列" else "删除图书",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center
                )

                // Highlighted Book Title Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.15f))
                        .border(
                            0.6.dp,
                            Color.White.copy(alpha = if (isDark) 0.15f else 0.30f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bookTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "本地文件将被一同清理，此操作不可恢复。",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = secondaryTextColor.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center
                )
            }

            // 3. Action Buttons (Cancel & Confirm Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = dialogChildBackdrop,
                    isCrystal = true,
                    themeAccent = themeAccent,
                    isDark = isDark,
                    surfaceColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                }

                // Confirm Delete Button
                LiquidButton(
                    onClick = onConfirm,
                    backdrop = dialogChildBackdrop,
                    isCrystal = true,
                    themeAccent = Color(0xFFFF453A),
                    isDark = isDark,
                    surfaceColor = Color(0xFFFF453A).copy(alpha = if (isDark) 0.24f else 0.16f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "删除",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF453A)
                        )
                    }
                }
            }
        }
    }
}
