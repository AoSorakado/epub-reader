package com.example.epubreader.ui.components.toast

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    var activeToast by remember { mutableStateOf<ToastMessage?>(null) }

    LaunchedEffect(currentToast) {
        if (currentToast != null) {
            activeToast = currentToast
        }
    }

    if (currentToast != null || activeToast != null) {
        Popup(
            alignment = Alignment.TopCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val effectiveTopPadding = if (statusBarTop < 36.dp) 48.dp else statusBarTop + 14.dp

            AnimatedVisibility(
                visible = currentToast != null,
                enter = slideInVertically(
                    initialOffsetY = { -it - 120 },
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f)
                ) + fadeIn(animationSpec = spring(dampingRatio = 0.80f, stiffness = 280f)) + scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin.Center
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it - 120 },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f)
                ) + fadeOut(animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)) + scaleOut(
                    targetScale = 0.82f,
                    transformOrigin = TransformOrigin.Center
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = effectiveTopPadding, start = 20.dp, end = 20.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            ) {
                activeToast?.let { _ ->
                    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E1E24)

                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(26.dp) },
                                effects = {
                                    vibrancy()
                                    blur(6f.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = if (isDark) 0.14f else 0.24f))
                                }
                            )
                            .border(
                                width = 0.9.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.45f else 0.85f),
                                        Color.White.copy(alpha = if (isDark) 0.15f else 0.40f)
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                GlobalToastManager.dismiss()
                            }
                            .widthIn(min = 230.dp, max = 340.dp)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = activeToast,
                            transitionSpec = {
                                (slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
                                ) + fadeIn(animationSpec = tween(180)))
                                    .togetherWith(
                                        slideOutVertically(
                                            targetOffsetY = { -it / 2 },
                                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
                                        ) + fadeOut(animationSpec = tween(140))
                                    )
                            },
                            label = "toastContentTransition"
                        ) { targetToast ->
                            if (targetToast != null) {
                                val iconTint = when (targetToast.type) {
                                    is ToastType.Success -> Color(0xFF34C759)
                                    is ToastType.Error -> Color(0xFFFF3B30)
                                    is ToastType.Syncing -> themeAccent
                                    is ToastType.Health -> Color(0xFFFF9500)
                                    is ToastType.Info -> themeAccent
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    when (targetToast.type) {
                                        is ToastType.Syncing -> {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.2.dp,
                                                color = iconTint,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        is ToastType.Success -> {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        is ToastType.Error -> {
                                            Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        is ToastType.Health -> {
                                            Icon(
                                                imageVector = Icons.Filled.LocalCafe,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        is ToastType.Info -> {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(9.dp))

                                    Text(
                                        text = targetToast.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        maxLines = 2,
                                        lineHeight = 19.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
