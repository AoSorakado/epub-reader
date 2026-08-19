package com.example.epubreader.ui.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView

private const val TAG = "MpvVideoView"

/**
 * A TextureView that bridges its SurfaceTexture lifecycle to MpvPlayerManager.
 * Used inside AndroidView in Jetpack Compose.
 *
 * Strictly synchronizes Surface lifecycle with libmpv's gpu-context=android.
 */
class MpvVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var mpvPlayer: MpvPlayerManager? = null
    private var currentSurface: Surface? = null
    private var currentTexture: SurfaceTexture? = null

    init {
        surfaceTextureListener = this
    }

    fun setPlayer(player: MpvPlayerManager) {
        if (mpvPlayer === player && currentSurface != null && player.isSurfaceAttached) {
            return
        }
        mpvPlayer = player
        val texture = surfaceTexture
        if (isAvailable && texture != null) {
            val surface = if (currentTexture === texture && currentSurface?.isValid == true) {
                currentSurface!!
            } else {
                currentSurface?.release()
                val newSurface = Surface(texture)
                currentSurface = newSurface
                currentTexture = texture
                newSurface
            }
            Log.d(TAG, "setPlayer: attaching surface $surface")
            player.attachSurface(surface)
            if (width > 0 && height > 0) {
                player.setSurfaceSize(width, height)
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable: ${width}x${height}")
        currentSurface?.release()
        val surface = Surface(surfaceTexture)
        currentSurface = surface
        currentTexture = surfaceTexture
        mpvPlayer?.attachSurface(surface)
        if (width > 0 && height > 0) {
            mpvPlayer?.setSurfaceSize(width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: ${width}x${height}")
        if (width > 0 && height > 0) {
            mpvPlayer?.setSurfaceSize(width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        val oldSurface = currentSurface
        currentSurface = null
        currentTexture = null
        if (oldSurface != null) {
            mpvPlayer?.detachSurface(oldSurface)
            oldSurface.release()
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }
}
