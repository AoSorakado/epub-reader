package com.example.epubreader.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = if (!isSystemInDarkTheme()) Color(0xFF0088FF) else Color(0xFF0091FF),
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val barBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount.coerceAtLeast(1)
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth.coerceAtLeast(1)).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val currentOnTabSelected by rememberUpdatedState(onTabSelected)

        val currentTabWidth by rememberUpdatedState(tabWidth)
        val currentConstraints by rememberUpdatedState(constraints)
        val currentIsLtr by rememberUpdatedState(isLtr)

        var highlightTrigger by remember { mutableStateOf<InteractiveHighlight?>(null) }

        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).coerceAtLeast(0).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { position ->
                    highlightTrigger?.press()
                    val touchX = if (currentIsLtr) position.x else currentConstraints.maxWidth.toFloat() - position.x
                    val slotWidth = (currentConstraints.maxWidth.toFloat() / tabsCount.coerceAtLeast(1)).coerceAtLeast(1f)
                    val targetTab = (touchX / slotWidth).toInt().fastCoerceIn(0, tabsCount - 1).toFloat()
                    updateValue(targetTab)
                },
                onDragStopped = {
                    highlightTrigger?.release()
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                    currentOnTabSelected(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount.x != 0f) {
                        val tw = currentTabWidth.coerceAtLeast(1f)
                        val delta = dragAmount.x / tw * if (currentIsLtr) 1f else -1f
                        snapToValue(
                            (value + delta).fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                }
            )
        }

        val interactiveHighlight = remember(animationScope, dampedDragAnimation, tabsCount) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    val animVal = dampedDragAnimation.value
                    val slotWidth = size.width / tabsCount.coerceAtLeast(1).toFloat()
                    val centerX = if (isLtr) {
                        (animVal + 0.5f) * slotWidth
                    } else {
                        size.width - (animVal + 0.5f) * slotWidth
                    }
                    Offset(centerX, size.height / 2f)
                }
            )
        }

        LaunchedEffect(interactiveHighlight) {
            highlightTrigger = interactiveHighlight
        }

        // Sync when selectedIndex changes externally or on return from other screens
        LaunchedEffect(selectedIndex) {
            if (abs(dampedDragAnimation.targetValue - selectedIndex.toFloat()) > 0.01f) {
                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }

        // Row 1: The main Glass Bottom Bar container - strong blur and lens refraction
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(20f.dp.toPx())
                        lens(
                            refractionHeight = 18f.dp.toPx(),
                            refractionAmount = 36f.dp.toPx(),
                            depthEffect = true
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width.coerceAtLeast(1f), progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    exportedBackdrop = barBackdrop
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // Row 2: Hidden layer that captures ONLY the tab icons/text for crisp chromatic tinting
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // Box 3: The Glass Slider (sliding thumb)
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val x = if (isLtr) {
                        (dampedDragAnimation.value * tabWidth + panelOffset).fastRoundToInt()
                    } else {
                        (constraints.maxWidth - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset).fastRoundToInt()
                    }
                    layout(placeable.width, placeable.height) {
                        placeable.place(x, 0)
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(barBackdrop, tabsBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        lens(
                            refractionHeight = lerp(8f.dp.toPx(), 14f.dp.toPx(), progress),
                            refractionAmount = lerp(12f.dp.toPx(), 20f.dp.toPx(), progress),
                            depthEffect = true
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    onDrawSurface = {
                        drawRect(accentColor.copy(alpha = if (!isLightTheme) 0.28f else 0.18f))
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount.coerceAtLeast(1))
        )

        // Touch & Drag Overlay (Spans entire bar so touching ANY tab immediately summons and drags thumb)
        Box(
            Modifier
                .matchParentSize()
                .then(dampedDragAnimation.modifier)
        )
    }
}
