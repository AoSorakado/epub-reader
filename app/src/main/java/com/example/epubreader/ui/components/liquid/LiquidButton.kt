package com.example.epubreader.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Float = 8f,
    refractionHeight: Float = 24f,
    refractionAmount: Float = 48f,
    content: @Composable RowScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Physical Elastic Animation States (Drag Pull Deformation + Press Bounce)
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val pressScale = remember { Animatable(1f) }

    val curDx = dragX.value
    val curDy = dragY.value
    val curScale = pressScale.value

    // Dynamic directional stretch & squash calculations for organic liquid jelly physics
    val stretchX = if (abs(curDx) > 0.1f) 1f + (abs(curDx) / 160f).coerceAtMost(0.20f) else 1f
    val stretchY = if (abs(curDy) > 0.1f) 1f + (abs(curDy) / 160f).coerceAtMost(0.20f) else 1f
    val squashX = if (abs(curDy) > 0.1f) 1f - (abs(curDy) / 320f).coerceAtMost(0.10f) else 1f
    val squashY = if (abs(curDx) > 0.1f) 1f - (abs(curDx) / 320f).coerceAtMost(0.10f) else 1f

    val finalScaleX = curScale * stretchX * squashX
    val finalScaleY = curScale * stretchY * squashY

    val springSpec = spring<Float>(
        dampingRatio = 0.52f, // Bouncy liquid jelly spring physics
        stiffness = 380f
    )

    fun releasePhysics() {
        coroutineScope.launch {
            launch { dragX.animateTo(0f, springSpec) }
            launch { dragY.animateTo(0f, springSpec) }
            launch { pressScale.animateTo(1f, springSpec) }
        }
    }

    val gestureModifier = if (isInteractive) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        coroutineScope.launch {
                            pressScale.animateTo(0.92f, spring(dampingRatio = 0.70f, stiffness = 600f))
                        }
                        val success = tryAwaitRelease()
                        if (success) {
                            onClick()
                        }
                        releasePhysics()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        coroutineScope.launch {
                            pressScale.animateTo(0.95f, spring(dampingRatio = 0.70f, stiffness = 500f))
                        }
                    },
                    onDragEnd = {
                        releasePhysics()
                    },
                    onDragCancel = {
                        releasePhysics()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val nextX = (dragX.value + dragAmount.x * 0.45f).coerceIn(-45f, 45f)
                            val nextY = (dragY.value + dragAmount.y * 0.45f).coerceIn(-35f, 35f)
                            dragX.snapTo(nextX)
                            dragY.snapTo(nextY)
                        }
                    }
                )
            }
    } else Modifier

    Row(
        modifier = modifier
            .graphicsLayer {
                translationX = curDx * 0.65f
                translationY = curDy * 0.65f
                scaleX = finalScaleX
                scaleY = finalScaleY
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.dp.toPx())
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    } else if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    } else {
                        drawRect(Color.White.copy(alpha = 0.14f))
                    }
                }
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.20f)
                    )
                ),
                shape = shape
            )
            .then(gestureModifier)
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
