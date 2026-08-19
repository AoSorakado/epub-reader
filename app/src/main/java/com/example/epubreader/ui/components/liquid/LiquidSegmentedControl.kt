package com.example.epubreader.ui.components.liquid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

/**
 * Modern Liquid Glass Segmented Tabs
 * Independent floating liquid crystal capsule buttons with full LiquidButton physics:
 * rubber-band drag elasticity, InteractiveHighlight specular spotlight,
 * lens refraction, RGB chromatic aberration, and luminous vibrant color glow.
 */
@Composable
fun LiquidGlassSegmentedTabs(
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    options: List<String>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.5.sp,
    themeAccent: Color = if (!isSystemInDarkTheme()) Color(0xFF007AFF) else Color(0xFF0A84FF),
    isDark: Boolean = isSystemInDarkTheme()
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, title ->
            val isSelected = (selectedIndex == index)

            LiquidButton(
                onClick = { onOptionSelected(index) },
                backdrop = backdrop,
                enableDrag = true,
                isInteractive = true,
                shape = CircleShape,
                isCrystal = isSelected,
                isDark = isDark,
                themeAccent = themeAccent,
                surfaceColor = if (isSelected) {
                    themeAccent.copy(alpha = if (isDark) 0.35f else 0.22f)
                } else {
                    Color.White.copy(alpha = if (isDark) 0.08f else 0.22f)
                },
                modifier = Modifier.height(34.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = fontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            if (isDark) Color.White else themeAccent
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF475569)
                        }
                    )
                }
            }
        }
    }
}

// Backwards compatibility alias
@Composable
fun LiquidSegmentedControl(
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    options: List<String>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.5.sp,
    accentColor: Color = if (!isSystemInDarkTheme()) Color(0xFF007AFF) else Color(0xFF0A84FF)
) {
    LiquidGlassSegmentedTabs(
        selectedIndex = selectedIndex,
        onOptionSelected = onOptionSelected,
        options = options,
        backdrop = backdrop,
        modifier = modifier,
        fontSize = fontSize,
        themeAccent = accentColor
    )
}
