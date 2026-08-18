package com.example.epubreader.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

@Composable
fun LiquidVerticalSegmentedControl(
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    options: List<String>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = if (!isSystemInDarkTheme()) Color(0xFF0088FF) else Color(0xFF0091FF)
) {
    val isLightTheme = !isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val barBackdrop = rememberLayerBackdrop()
    val optionsCount = options.size.coerceAtLeast(1)
    val animationScope = rememberCoroutineScope()
    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val innerPadding = 3.dp

    // Format options vertically (e.g., "全\n部", "在\n看", "已\n完")
    val verticalLabels = remember(options) {
        options.map { label ->
            if (label.length == 2) "${label[0]}\n${label[1]}"
            else if (label.length == 3) "${label[0]}\n${label[1]}\n${label[2]}"
            else label
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .width(42.dp)
            .height(140.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val density = LocalDensity.current

        val slotHeightPx = with(density) {
            (constraints.maxHeight.toFloat() - (innerPadding * 2).toPx()) / optionsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffsetY by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxHeight.coerceAtLeast(1)).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val dampedDragAnimation = remember(animationScope, optionsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(optionsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.25f, // 3D Elevation / Lift effect on press and drag
                onDragStarted = { position ->
                    val newIndex = ((position.y - with(density) { innerPadding.toPx() }) / slotHeightPx).fastCoerceIn(0f, (optionsCount - 1).toFloat())
                    snapToValue(newIndex)
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, optionsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                    currentOnOptionSelected(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    snapToValue(
                        (targetValue + dragAmount.y / slotHeightPx)
                            .fastCoerceIn(0f, (optionsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.y)
                    }
                }
            )
        }

        LaunchedEffect(selectedIndex) {
            if (abs(dampedDragAnimation.targetValue - selectedIndex.toFloat()) > 0.01f) {
                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }

        // Layer 1: Vertical Glass Bar Container (Draws backdrop and exports barBackdrop)
        Column(
            modifier = Modifier
                .graphicsLayer {
                    translationY = panelOffsetY
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(16.dp.toPx())
                        lens(
                            refractionHeight = 6.dp.toPx(),
                            refractionAmount = 10.dp.toPx(),
                            depthEffect = false
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 6.dp.toPx() / size.height.coerceAtLeast(1f), progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    highlight = { Highlight.Default },
                    shadow = {
                        Shadow(
                            radius = 8.dp,
                            color = Color.Black.copy(alpha = if (!isLightTheme) 0.30f else 0.10f)
                        )
                    },
                    exportedBackdrop = barBackdrop
                )
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            verticalLabels.forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = (if (!isLightTheme) Color.White else Color(0xFF1E293B)).copy(alpha = 0.72f)
                    )
                }
            }
        }

        // Layer 2: Hidden vertical layer capturing ONLY the text with accentColor tint for crisp optical refraction
        Column(
            modifier = Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer {
                    translationY = panelOffsetY
                }
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            verticalLabels.forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Layer 3: The Floating Vertical Glass Slider Thumb (3D Lift + Combined Backdrop Lens Refraction)
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            constraints.maxWidth,
                            slotHeightPx.fastRoundToInt()
                        )
                    )
                    val y = (dampedDragAnimation.value * slotHeightPx + panelOffsetY).fastRoundToInt()
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, y)
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(barBackdrop, tabsBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        lens(
                            refractionHeight = lerp(4.dp.toPx(), 7.dp.toPx(), progress),
                            refractionAmount = lerp(5.dp.toPx(), 9.dp.toPx(), progress),
                            depthEffect = false
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = lerp(0.85f, 1f, progress))
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(
                            radius = (8f + 8f * progress).dp,
                            color = Color.Black.copy(alpha = lerp(0.40f, 0.65f, progress))
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = (6f + 4f * progress).dp,
                            alpha = lerp(0.75f, 1f, progress)
                        )
                    },
                    onDrawSurface = {
                        drawRect(accentColor.copy(alpha = if (!isLightTheme) 0.28f else 0.18f))
                    }
                )
                .fillMaxWidth()
                .fillMaxHeight(1f / optionsCount.coerceAtLeast(1))
        )

        // Layer 4: Tap & Drag Gesture Layer
        var isDraggingBar by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slotHeightPx, optionsCount) {
                    detectTapGestures(
                        onTap = { offset ->
                            val index = ((offset.y - with(density) { innerPadding.toPx() }) / slotHeightPx).fastCoerceIn(0f, (optionsCount - 1).toFloat())
                            val targetIndex = index.fastRoundToInt().fastCoerceIn(0, optionsCount - 1)
                            dampedDragAnimation.animateToValue(targetIndex.toFloat())
                            currentOnOptionSelected(targetIndex)
                        }
                    )
                }
                .pointerInput(slotHeightPx, optionsCount) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDraggingBar = true
                            dampedDragAnimation.press()
                            val index = ((offset.y - with(density) { innerPadding.toPx() }) / slotHeightPx).fastCoerceIn(0f, (optionsCount - 1).toFloat())
                            dampedDragAnimation.snapToValue(index)
                        },
                        onDragEnd = {
                            isDraggingBar = false
                            val targetIndex = dampedDragAnimation.targetValue.fastRoundToInt().fastCoerceIn(0, optionsCount - 1)
                            dampedDragAnimation.animateToValue(targetIndex.toFloat())
                            dampedDragAnimation.release()
                            currentOnOptionSelected(targetIndex)
                        },
                        onDragCancel = {
                            isDraggingBar = false
                            val targetIndex = dampedDragAnimation.targetValue.fastRoundToInt().fastCoerceIn(0, optionsCount - 1)
                            dampedDragAnimation.animateToValue(targetIndex.toFloat())
                            dampedDragAnimation.release()
                        },
                        onDrag = { change, dragAmount ->
                            if (abs(dragAmount.y) > 0.5f) {
                                change.consume()
                            }
                            val deltaIdx = dragAmount.y / slotHeightPx
                            val newIdx = (dampedDragAnimation.value + deltaIdx).fastCoerceIn(0f, (optionsCount - 1).toFloat())
                            dampedDragAnimation.snapToValue(newIdx)
                        }
                    )
                }
        )
    }
}
