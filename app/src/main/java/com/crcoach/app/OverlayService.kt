package com.crcoach.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    companion object {
        @Volatile private var instance: OverlayService? = null

        fun start(ctx: Context): Boolean {
            if (!Settings.canDrawOverlays(ctx)) return false
            val i = Intent(ctx, OverlayService::class.java)
            ContextCompat.startForegroundService(ctx, i)
            return true
        }

        fun pulse() {
            instance?.runOnUi {
                val ring = instance?.rootView?.findViewById<View>(R.id.ringTest)
                    ?: return@runOnUi
                ring.animate().cancel()
                ring.alpha = 1f
                ring.animate().alpha(0f).setDuration(500).setStartDelay(250).start()
            }
        }
    }

    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.ensureChannel(this)
        startForeground(NotificationHelper.NOTIF_ID, NotificationHelper.build(this))

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        rootView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        wm.addView(rootView, params)
    }

    private fun runOnUi(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        rootView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        rootView = null
        super.onDestroy()
    }
}
