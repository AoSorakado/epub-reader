package com.example.epubreader.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

data class ContextMenuItem(
    val title: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun GlassContextMenu(
    backdrop: Backdrop,
    items: List<ContextMenuItem>,
    isDark: Boolean = false,
    primaryTextColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(min = 175.dp, max = 210.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(20.dp) },
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
                        radius = 16.dp,
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
                        Color.White.copy(alpha = if (isDark) 0.40f else 0.80f),
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.40f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        items.forEachIndexed { index, item ->
            val itemColor = if (item.isDestructive) Color(0xFFFF453A) else primaryTextColor

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { item.onClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = itemColor
                )
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = itemColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.White.copy(alpha = if (isDark) 0.15f else 0.30f))
                )
            }
        }
    }
}
