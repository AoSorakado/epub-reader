package com.example.epubreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.epubreader.ui.theme.EpubReaderTheme
import com.example.epubreader.ui.components.MainScaffold
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EpubReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    MainScaffold(navController = navController)
                    StartupShaderPrewarmer()
                }
            }
        }
    }
}

@Composable
private fun StartupShaderPrewarmer() {
    val prewarmBackdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .size(1.dp)
            .alpha(0.001f)
            .drawBackdrop(
                backdrop = prewarmBackdrop,
                shape = { androidx.compose.foundation.shape.RoundedCornerShape(0.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx(), depthEffect = false, chromaticAberration = true)
                }
            )
    )
}