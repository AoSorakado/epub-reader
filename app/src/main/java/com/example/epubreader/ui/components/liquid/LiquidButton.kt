package com.example.epubreader.ui.components.liquid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enableDrag: Boolean = true,
    isInteractive: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    themeAccent: Color = Color.Unspecified,
    isCrystal: Boolean = false,
    isDark: Boolean = false,
    blurRadius: Float = 6f,
    refractionHeight: Float = 16f,
    refractionAmount: Float = 32f,
    content: @Composable RowScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val interactiveHighlight = remember(coroutineScope) {
        InteractiveHighlight(
            animationScope = coroutineScope
        )
    }

    val baseModifier = if (backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.dp.toPx())
                lens(refractionHeight.dp.toPx(), refractionAmount.dp.toPx(), depthEffect = false, chromaticAberration = true)
            },
            highlight = { Highlight.Plain },
            shadow = {
                if (isDark || isCrystal) {
                    Shadow(radius = 0.dp, color = Color.Transparent)
                } else {
                    Shadow(radius = 3.dp, color = Color.Black.copy(alpha = 0.08f))
                }
            },
            layerBlock = if (isInteractive && enableDrag) {
                {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 0.94f, progress)

                    val maxOffset = size.minDimension * 0.45f
                    val initialDerivative = 0.08f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset.coerceAtLeast(1f))
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset.coerceAtLeast(1f))

                    val maxDragScale = 4f.dp.toPx() / size.height.coerceAtLeast(1f)
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
                            (width / height.coerceAtLeast(1f)).fastCoerceAtMost(1.15f)
                    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
                            (height / width.coerceAtLeast(1f)).fastCoerceAtMost(1.15f)
                }
            } else null,
            onDrawSurface = {
                if (isCrystal && themeAccent.isSpecified) {
                    // Rich frosted dark acrylic base
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E2430).copy(alpha = 0.55f),
                                Color(0xFF10141C).copy(alpha = 0.70f)
                            )
                        )
                    )
                    // Top specular highlight
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.22f else 0.30f),
                                Color.White.copy(alpha = if (isDark) 0.05f else 0.10f)
                            )
                        )
                    )
                    // Vibrant theme accent radial glow
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                themeAccent.copy(alpha = if (isDark) 0.32f else 0.38f),
                                themeAccent.copy(alpha = if (isDark) 0.08f else 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.2f, 0f),
                            radius = size.width * 0.95f
                        )
                    )
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                } else if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                } else if (surfaceColor.isSpecified) {
                    drawRect(surfaceColor)
                } else {
                    drawRect(Color.White.copy(alpha = 0.12f))
                }
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (tint.isSpecified) tint.copy(alpha = 0.75f) else (if (surfaceColor.isSpecified) surfaceColor else if (isCrystal) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)),
                        if (tint.isSpecified) tint.copy(alpha = 0.55f) else (if (surfaceColor.isSpecified) surfaceColor.copy(alpha = 0.85f) else if (isCrystal) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f))
                    )
                )
            )
    }

    val borderBrush = if (isCrystal && themeAccent.isSpecified) {
        Brush.linearGradient(
            colors = listOf(
                themeAccent.copy(alpha = if (isDark) 0.85f else 0.95f),
                Color.White.copy(alpha = if (isDark) 0.70f else 0.90f),
                Color.White.copy(alpha = if (isDark) 0.20f else 0.35f),
                themeAccent.copy(alpha = if (isDark) 0.65f else 0.80f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color(0xFFE0E7FF).copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.20f)
            )
        )
    }

    Row(
        modifier = baseModifier
            .then(
                if (isInteractive && enableDrag) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = 1.0.dp,
                brush = borderBrush,
                shape = shape
            )
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
