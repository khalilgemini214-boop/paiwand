package com.paiwand.translate.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.paiwand.translate.MainActivity
import com.paiwand.translate.R
import com.paiwand.translate.accessibility.TranslateAccessibilityService
import com.paiwand.translate.translate.QuickTranslator
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var capturing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForegroundCompat()

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (data == null || !ScreenCapture.start(this, resultCode, data)) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                showBubble()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating translate",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the translate bubble available"
            manager.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_paiwand)
            .setContentTitle("Paiwand is ready")
            .setContentText("Tap the bubble to translate what is on screen")
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBubble() {
        if (bubble != null) return

        val manager = getSystemService(WindowManager::class.java) ?: return
        val density = resources.displayMetrics.density
        val size = (58 * density).toInt()

        val shape = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0E7C63.toInt(), 0xFF12A07C.toInt())
        ).apply {
            this.shape = GradientDrawable.OVAL
            setStroke((1.5f * density).toInt(), 0x33FFFFFF)
        }

        val view = FrameLayout(this).apply {
            background = shape
            elevation = 10f * density
            contentDescription = "Translate screen"
        }

        val icon = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_stat_paiwand)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val inset = (17 * density).toInt()
            setPadding(inset, inset, inset, inset)
            contentDescription = "Translate screen"
        }
        view.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (4 * density).toInt()
            y = resources.displayMetrics.heightPixels / 3
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    val slop = (12 * density).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { manager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()

                    if (moved) {
                        val metrics = resources.displayMetrics
                        val centerX = (params.x + size / 2)
                            .coerceIn(0, metrics.widthPixels - 1)
                        val centerY = (params.y + size / 2)
                            .coerceIn(0, metrics.heightPixels - 1)
                        handleDrop(centerX, centerY)
                    } else {
                        captureScreen()
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { manager.addView(view, params) }

        windowManager = manager
        bubble = view
        bubbleParams = params
    }

    /**
     * A drag-release. The element under the bubble wins; if the app hides its accessibility
     * tree we fall back to reading just the text block under the point with OCR.
     */
    private fun handleDrop(x: Int, y: Int) {
        val target = TranslateAccessibilityService.instance?.targetAt(x, y)

        if (target != null && target.editable && target.text.isNotBlank()) {
            replaceInPlace(target)
            return
        }

        if (target != null && target.text.isNotBlank()) {
            TranslatePopupActivity.start(this, target.text)
            return
        }

        translateRegionAt(x, y)
    }

    private fun replaceInPlace(target: TranslateAccessibilityService.Target) {
        scope.launch {
            try {
                val result = QuickTranslator.translate(this@BubbleService, target.text)
                val replaced = TranslateAccessibilityService.instance
                    ?.replace(target.node, result.text) == true

                toast(
                    if (replaced) "Replaced with ${result.target.name}"
                    else "This field does not accept text from other apps"
                )
            } catch (e: Exception) {
                toast(e.message ?: "Translation failed")
            }
        }
    }

    private fun translateRegionAt(x: Int, y: Int) {
        if (capturing) return
        capturing = true

        scope.launch {
            try {
                val bitmap = captureWithoutBubble()
                val text = if (bitmap != null) Ocr.readAt(bitmap, x, y) else ""
                bitmap?.recycle()

                if (text.isBlank()) {
                    toast("No text under the bubble")
                } else {
                    TranslatePopupActivity.start(this@BubbleService, text)
                }
            } finally {
                capturing = false
            }
        }
    }

    private suspend fun captureWithoutBubble(): Bitmap? {
        bubble?.visibility = View.INVISIBLE
        delay(140)
        val bitmap = ScreenCapture.grab()
        bubble?.visibility = View.VISIBLE
        return bitmap
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun captureScreen() {
        if (capturing) return
        capturing = true

        scope.launch {
            try {
                val bitmap = captureWithoutBubble()
                val text = if (bitmap != null) Ocr.read(bitmap) else ""
                bitmap?.recycle()
                TranslatePopupActivity.start(this@BubbleService, text)
            } finally {
                capturing = false
            }
        }
    }

    override fun onDestroy() {
        bubble?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        bubble = null
        windowManager = null
        bubbleParams = null
        ScreenCapture.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "paiwand_overlay"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.paiwand.translate.action.START_BUBBLE"
        private const val ACTION_STOP = "com.paiwand.translate.action.STOP_BUBBLE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, BubbleService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
