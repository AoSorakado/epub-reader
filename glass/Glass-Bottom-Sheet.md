Glass Bottom Sheet
Create a glass bottom sheet

Goals
Create a glass bottom sheet based on the code:


Ask

Copy
Box(Modifier.fillMaxSize()) {
    val backgroundColor = Color.White
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    MainNavHost(
        modifier = Modifier.layerBackdrop(backdrop)
    )

    GlassBottomSheet(backdrop = backdrop)
}
What you will learn
Handle the case of "glass on glass"

Make use of exportedBackdrop parameter of the drawBackdrop modifier

Steps
Create a GlassBottomSheet
GlassBottomSheet.kt

Ask

Copy
// your package

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun BoxScope.GlassBottomSheet(backdrop: Backdrop) {
    Column(
        Modifier
            .safeContentPadding()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(44f.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), true)
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
            )
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        Spacer(Modifier.height(256f.dp))
        // glass button
        Box(
            Modifier
                .padding(16f.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    shadow = null,
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
                )
                .height(56f.dp)
                .fillMaxWidth()
        )
    }
}

The backdrop for the glass button is backdrop, but we want to include the bottom sheet.

Use the bottom sheet as a backdrop for the glass button (WRONG code)
The WRONG idea is to set a new LayerBackdrop after drawBackdrop .


Ask

Copy
val bottomSheetBackdrop = rememberLayerBackdrop()
Column(
    Modifier
        .layerBackdrop(bottomSheetBackdrop)
        .drawBackdrop()
) {
    // glass button
    Box(
        Modifier
            .drawBackdrop(
                backdrop = bottomSheetBackdrop,
            )
    )
}
You will get a crash:

Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x__ in tid __ (RenderThread), pid __

Because the layerBackdrop modifier will draw the content to the bottomSheetBackdrop, and the content will draw the bottomSheetBackdrop, it's a loop!

Use the bottom sheet as a backdrop for the glass button (CORRECT code)
Use exportedBackdrop in drawBackdrop modifier, it will skip drawing the content.


Ask

Copy
val bottomSheetBackdrop = rememberLayerBackdrop()
Column(
    Modifier
        .drawBackdrop(
            backdrop = backdrop,
            exportedBackdrop = bottomSheetBackdrop,
        )
) {
    // glass button
    Box(
        Modifier
            .drawBackdrop(
                backdrop = bottomSheetBackdrop,
            )
    )
}

Final code
GlassBottomSheet.kt

Ask

Copy
// your package

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun BoxScope.GlassBottomSheet(backdrop: Backdrop) {
    val bottomSheetBackdrop = rememberLayerBackdrop()
    Column(
        Modifier
            .safeContentPadding()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(44f.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), true)
                },
                exportedBackdrop = bottomSheetBackdrop,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
            )
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        Spacer(Modifier.height(256f.dp))
        Box(
            Modifier
                .padding(16f.dp)
                .drawBackdrop(
                    backdrop = bottomSheetBackdrop,
                    shape = { CircleShape },
                    shadow = null,
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
                )
                .height(56f.dp)
                .fillMaxWidth()
        )
    }
}
