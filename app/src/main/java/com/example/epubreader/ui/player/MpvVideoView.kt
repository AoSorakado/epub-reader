package com.example.epubreader.ui.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView

/**
 * A TextureView that bridges its SurfaceTexture lifecycle to MpvPlayerManager.
 * Used inside AndroidView in Jetpack Compose.
 *
 * Using TextureView allows Compose's layerBackdrop to sample live video frames,
 * restoring all liquid glass refraction, chromatic aberration, and dynamic lighting.
 */
class MpvVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var mpvPlayer: MpvPlayerManager? = null
    private var currentSurface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    fun setPlayer(player: MpvPlayerManager) {
        mpvPlayer = player
        if (isAvailable && surfaceTexture != null) {
            val surface = Surface(surfaceTexture)
            currentSurface = surface
            player.attachSurface(surface)
            player.setSurfaceSize(width, height)
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(surfaceTexture)
        currentSurface = surface
        mpvPlayer?.attachSurface(surface)
        mpvPlayer?.setSurfaceSize(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        mpvPlayer?.setSurfaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        mpvPlayer?.detachSurface()
        currentSurface?.release()
        currentSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }
}
