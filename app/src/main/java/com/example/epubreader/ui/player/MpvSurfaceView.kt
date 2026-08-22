package com.example.epubreader.ui.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

private const val TAG = "MpvSurfaceView"

/**
 * A SurfaceView that bridges its Surface lifecycle to MpvPlayerManager.
 *
 * Unlike the TextureView-based MpvVideoView, SurfaceView renders via a dedicated hardware
 * compositor layer that supports HDR color spaces (BT.2020 / PQ / HLG).
 * Combined with mpv's `target-colorspace-hint=yes` setting, this enables true HDR
 * passthrough for content with DTS-HD MA / TrueHD audio that ExoPlayer cannot decode.
 */
class MpvSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var mpvPlayer: MpvPlayerManager? = null

    init {
        holder.addCallback(this)
        // Render below Compose UI so Danmaku / controls can draw on top
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                setDesiredHdrHeadroom(4.0f)
            } catch (e: Throwable) {}
        }
    }

    fun setPlayer(player: MpvPlayerManager) {
        if (mpvPlayer === player && player.isSurfaceAttached) return
        mpvPlayer = player
        val surface = holder.surface
        if (surface != null && surface.isValid) {
            Log.d(TAG, "setPlayer: attaching existing surface")
            player.attachSurface(surface)
            if (width > 0 && height > 0) {
                player.setSurfaceSize(width, height)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        val surface = holder.surface ?: return
        mpvPlayer?.attachSurface(surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: ${width}x${height} format=$format")
        val surface = holder.surface ?: return
        mpvPlayer?.attachSurface(surface)
        if (width > 0 && height > 0) {
            mpvPlayer?.setSurfaceSize(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        mpvPlayer?.detachSurface()
    }
}
