package com.example.epubreader.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun GlassDeleteDialog(
    bookTitle: String,
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(20.dp) },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(
                            refractionHeight = 44f.dp.toPx(),
                            refractionAmount = 88f.dp.toPx(),
                            depthEffect = true
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow() },
                    innerShadow = { InnerShadow(radius = 22.dp, alpha = 0.55f) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                )
        ) {
            // ── Title + message ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "删除图书",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "确定要删除《$bookTitle》？\n本地文件将被一同清理，无法恢复。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center
                )
            }

            // ── Horizontal divider ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.22f))
            )

            // ── Action buttons ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDismiss() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onConfirm() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "删除",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF453A)
                    )
                }
            }
        }
}
