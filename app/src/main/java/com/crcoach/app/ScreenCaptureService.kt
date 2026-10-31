package com.crcoach.app

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.concurrent.thread

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        private const val CAP_W = 1080
        private const val CAP_H = 2400
        private const val TARGET_FPS = 12
    }

    private lateinit var projection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private var captureThread: Thread? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationHelper.ensureChannel(this)
        val notif = NotificationHelper.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID, notif)
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        val densityDpi = resources.displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(CAP_W, CAP_H, PixelFormat.RGBA_8888, 2)

        virtualDisplay = projection.createVirtualDisplay(
            "cr-coach-capture",
            CAP_W, CAP_H, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        running = true
        startCaptureLoop()

        return START_STICKY
    }

    private fun startCaptureLoop() {
        captureThread = thread(name = "capture-loop") {
            val sleepMs = (1000L / TARGET_FPS)
            var frameCount = 0L
            while (running) {
                val img = try {
                    imageReader.acquireLatestImage()
                } catch (_: Throwable) {
                    null
                }
                if (img == null) {
                    Thread.sleep(8)
                    continue
                }
                try {
                    frameCount++
                    if (frameCount % TARGET_FPS == 0L) {
                        OverlayService.pulse()
                    }
                } finally {
                    img.close()
                }
                Thread.sleep(sleepMs)
            }
        }
    }

    override fun onDestroy() {
        running = false
        try { captureThread?.join(500) } catch (_: Throwable) {}
        try { virtualDisplay.release() } catch (_: Throwable) {}
        try { imageReader.close() } catch (_: Throwable) {}
        try { projection.stop() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
