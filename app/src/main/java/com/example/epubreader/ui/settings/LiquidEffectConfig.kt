package com.example.epubreader.ui.settings

data class LiquidEffectConfig(
    val blurRadius: Float = 16f,
    val lensRx: Float = 32f,
    val lensRy: Float = 32f,
    val alpha: Float = 0.4f,
    val chromaticAberration: Boolean = false,
    val vibrancy: Boolean = true
)
