package com.example.epubreader.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF007AFF),
    trackColor: Color = Color.Black.copy(alpha = 0.12f)
) {
    val density = LocalDensity.current
    val thumbWidth = 38.dp
    val thumbHeight = 22.dp

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue = value()

    var isDragging by remember { mutableStateOf(false) }
    var localProgress by remember {
        mutableFloatStateOf(
            ((currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        )
    }

    // Physical Lift & Press dynamics
    val liftProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentValue, isDragging) {
        if (!isDragging) {
            val target = ((currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            localProgress = target
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        val maxTravel = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

        val thumbLeft = maxTravel * localProgress
        val activeWidthDp = with(density) { (thumbLeft + thumbWidthPx / 2f).coerceIn(0f, trackWidthPx).toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxTravel, valueRange, isLtr) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            coroutineScope.launch {
                                liftProgress.animateTo(
                                    1f,
                                    spring(dampingRatio = 0.55f, stiffness = 380f)
                                )
                            }
                            val progress = ((offset.x - thumbWidthPx / 2f) / maxTravel).coerceIn(0f, 1f)
                            localProgress = if (isLtr) progress else 1f - progress
                            val nextVal = (valueRange.start + (valueRange.endInclusive - valueRange.start) * localProgress).coerceIn(valueRange)
                            currentOnValueChange(nextVal)
                        },
                        onDragEnd = {
                            isDragging = false
                            coroutineScope.launch {
                                liftProgress.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.55f, stiffness = 380f)
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                liftProgress.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.55f, stiffness = 380f)
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaProgress = (dragAmount.x / maxTravel) * (if (isLtr) 1f else -1f)
                            localProgress = (localProgress + deltaProgress).coerceIn(0f, 1f)
                            val nextVal = (valueRange.start + (valueRange.endInclusive - valueRange.start) * localProgress).coerceIn(valueRange)
                            currentOnValueChange(nextVal)
                        }
                    )
                }
                .pointerInput(maxTravel, valueRange, isLtr) {
                    detectTapGestures { offset ->
                        val progress = ((offset.x - thumbWidthPx / 2f) / maxTravel).coerceIn(0f, 1f)
                        localProgress = if (isLtr) progress else 1f - progress
                        val nextVal = (valueRange.start + (valueRange.endInclusive - valueRange.start) * localProgress).coerceIn(valueRange)
                        currentOnValueChange(nextVal)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Background Track (Inactive Gray)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
            )

            // Active Track (Vibrant Electric Blue Fill)
            Box(
                Modifier
                    .width(activeWidthDp)
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )

            // Transparent Glass Thumb with Physical Lift Animation
            val currentLift = liftProgress.value
            val scale = lerp(1f, 1.28f, currentLift)
            val liftOffsetPx = lerp(0f, -with(density) { 3.dp.toPx() }, currentLift)
            val shadowRadiusDp = lerp(3f, 10f, currentLift).dp
            val shadowAlpha = lerp(0.10f, 0.28f, currentLift)

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = thumbLeft
                        translationY = liftOffsetPx
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(
                                refractionHeight = lerp(5f, 9f, currentLift).dp.toPx(),
                                refractionAmount = lerp(10f, 18f, currentLift).dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Plain
                        },
                        shadow = {
                            Shadow(
                                radius = shadowRadiusDp,
                                color = Color.Black.copy(alpha = shadowAlpha)
                            )
                        },
                        onDrawSurface = {
                            // Frosted liquid glass surface
                            drawRect(Color.White.copy(alpha = 0.75f))
                        }
                    )
                    .size(thumbWidth, thumbHeight)
            )
        }
    }
}
