package com.example.epubreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset

enum class AppTheme(val title: String, val shortTitle: String) {
    PEACH_BLOSSOM("落樱微光", "落樱"),
    CYBER_SUNSET("暮光浅霞", "暮光"),
    OCEAN_WAVE("湛蓝浅汐", "浅汐"),
    AURORA_GREEN("极光秘境", "极光"),
    FROSTED_MINT("薄荷冰川", "薄荷"),
    STARLIGHT_PURPLE("紫雾琉璃", "紫雾"),
    SUNSET_GLOW("晚霞暖阳", "晚霞"),
    MIDNIGHT_GLASS("暗夜黑曜", "暗夜"),
    CUSTOM("自定义", "自定义")
}

fun getThemeGradient(theme: AppTheme, customColors: List<Color> = emptyList()): Brush {
    return when (theme) {
        AppTheme.PEACH_BLOSSOM -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFD4CA), // Soft Sakura Peach
                Color(0xFFF6D6EC), // Rosy Lavender
                Color(0xFFDFDAFA)  // Mist Lilac
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.CYBER_SUNSET -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFB8CEF2), // Sky Blue
                Color(0xFFDEC3EC), // Twilight Purple
                Color(0xFFF9D1BF)  // Soft Warm Sunset
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.OCEAN_WAVE -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFA6CCFD), // Pure Ice Cyan
                Color(0xFFC7ECFD), // Luminous Azure
                Color(0xFF98BFFB)  // Ultramarine Fog
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.AURORA_GREEN -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFB9ECE5), // Jade Mint
                Color(0xFFD0EBD0), // Sage Dew
                Color(0xFFAFE0F5)  // Arctic Sky
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.FROSTED_MINT -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFD6F0E4), // Matcha Glaze
                Color(0xFFCFECE8), // Frosted Seafoam
                Color(0xFFC2E8E0)  // Nordic Clean Ice
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.STARLIGHT_PURPLE -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFDBCDEF), // Wisteria Lavender
                Color(0xFFEBD7EA), // Pale Orchid
                Color(0xFFCAC0EB)  // Pearlescent Mauve
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.SUNSET_GLOW -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFDAB9), // Peach Amber
                Color(0xFFFBB5B1), // Warm Coral
                Color(0xFFE9C5E8)  // Evening Violet
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.MIDNIGHT_GLASS -> Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F172A), // Dark Slate
                Color(0xFF1E293B), // Midnight Navy
                Color(0xFF2B2144)  // Deep Velvet Purple
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
        AppTheme.CUSTOM -> Brush.linearGradient(
            colors = if (customColors.size >= 2) customColors else listOf(Color(0xFFFFD4CA), Color(0xFFDFDAFA)),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2000f)
        )
    }
}

fun getThemeAccentColor(theme: AppTheme, customColors: List<Color> = emptyList()): Color {
    return when (theme) {
        AppTheme.PEACH_BLOSSOM -> Color(0xFFFF2D75) // Vivid Sakura Blossom Pink
        AppTheme.CYBER_SUNSET -> Color(0xFF8B5CF6)  // Vivid Twilight Purple
        AppTheme.OCEAN_WAVE -> Color(0xFF007AFF)    // Electric Sapphire Azure Blue
        AppTheme.AURORA_GREEN -> Color(0xFF059669)  // Rich Aurora Emerald Green
        AppTheme.FROSTED_MINT -> Color(0xFF0D9488)  // Fresh Matcha Teal
        AppTheme.STARLIGHT_PURPLE -> Color(0xFF6D28D9) // Deep Starlight Royal Violet
        AppTheme.SUNSET_GLOW -> Color(0xFFEA580C)   // Warm Sunset Amber Orange
        AppTheme.MIDNIGHT_GLASS -> Color(0xFF38BDF8) // High-contrast Luminous Cyan
        AppTheme.CUSTOM -> customColors.firstOrNull() ?: Color(0xFF007AFF)
    }
}

fun getThemeColors(theme: AppTheme, customColors: List<Color> = emptyList()): List<Color> {
    return when (theme) {
        AppTheme.PEACH_BLOSSOM -> listOf(Color(0xFFFF2D75), Color(0xFFFF7597), Color(0xFFFFB3C6))
        AppTheme.CYBER_SUNSET -> listOf(Color(0xFF8B5CF6), Color(0xFFB87CF8), Color(0xFFF472B6))
        AppTheme.OCEAN_WAVE -> listOf(Color(0xFF007AFF), Color(0xFF38BDF8), Color(0xFF67E8F9))
        AppTheme.AURORA_GREEN -> listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))
        AppTheme.FROSTED_MINT -> listOf(Color(0xFF0D9488), Color(0xFF14B8A6), Color(0xFF2DD4BF))
        AppTheme.STARLIGHT_PURPLE -> listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFA78BFA))
        AppTheme.SUNSET_GLOW -> listOf(Color(0xFFEA580C), Color(0xFFF97316), Color(0xFFFB923C))
        AppTheme.MIDNIGHT_GLASS -> listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF6366F1))
        AppTheme.CUSTOM -> if (customColors.isNotEmpty()) customColors else listOf(Color(0xFF007AFF), Color(0xFF38BDF8))
    }
}

fun getThemeAccentGradient(theme: AppTheme, customColors: List<Color> = emptyList()): Brush {
    val colors = getThemeColors(theme, customColors)
    return Brush.horizontalGradient(if (colors.size == 1) listOf(colors[0], colors[0]) else colors)
}

