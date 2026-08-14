package com.example.epubreader.ui.components.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

@Composable
fun GlobalLiquidToast(
    backdrop: Backdrop,
    isDark: Boolean = false,
    themeAccent: Color = Color(0xFF007AFF),
    modifier: Modifier = Modifier
) {
    val currentToast by GlobalToastManager.currentToast.collectAsState()

    AnimatedVisibility(
        visible = currentToast != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.76f, stiffness = 320f)
        ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f)) + scaleIn(
            initialScale = 0.88f,
            transformOrigin = TransformOrigin.Center
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f)
        ) + fadeOut() + scaleOut(
            targetScale = 0.88f,
            transformOrigin = TransformOrigin.Center
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(top = 10.dp, start = 20.dp, end = 20.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        currentToast?.let { toast ->
            val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24)
            val iconTint = when (toast.type) {
                is ToastType.Success -> Color(0xFF34C759)
                is ToastType.Error -> Color(0xFFFF3B30)
                is ToastType.Syncing -> themeAccent
                is ToastType.Health -> Color(0xFFFF9500)
                is ToastType.Info -> themeAccent
            }

            Box(
                modifier = Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
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
                                radius = 18.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f)
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = if (isDark) 0.12f else 0.22f))
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
                        shape = RoundedCornerShape(50)
                    )
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        GlobalToastManager.dismiss()
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (toast.type) {
                        is ToastType.Syncing -> {
                            CircularProgressIndicator(
                                strokeWidth = 2.2.dp,
                                color = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        is ToastType.Success -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        is ToastType.Error -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        is ToastType.Health -> {
                            Icon(
                                imageVector = Icons.Filled.LocalCafe,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        is ToastType.Info -> {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = toast.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
