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
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
    private var capturing = false
    private var card: TranslateCard? = null

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
            .setContentText("Tap for the whole screen, drag onto a word to translate just that")
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
        val size = (48 * density).toInt()

        val shape = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0E7C63.toInt(), 0xFF12A07C.toInt())
        ).apply {
            this.shape = GradientDrawable.OVAL
            setStroke((1.2f * density).toInt(), 0x33FFFFFF)
        }

        val view = FrameLayout(this).apply {
            background = shape
            elevation = 10f * density
            contentDescription = "Translate"
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_stat_paiwand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = (14 * density).toInt()
            setPadding(inset, inset, inset, inset)
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

                    val metrics = resources.displayMetrics
                    val centerX = (params.x + size / 2).coerceIn(0, metrics.widthPixels - 1)
                    val centerY = (params.y + size / 2).coerceIn(0, metrics.heightPixels - 1)

                    if (moved) {
                        handleDrop(centerX, centerY)
                    } else {
                        translateWholeScreen(centerX, centerY)
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { manager.addView(view, params) }

        windowManager = manager
        bubble = view
    }

    /**
     * Drag-release: the element under the bubble wins. Text fields get the translation written
     * straight into them; anything else opens the floating card.
     */
    private fun handleDrop(x: Int, y: Int) {
        val service = TranslateAccessibilityService.instance

        if (service == null) {
            toast("Enable accessibility to translate single words")
            translateRegionAt(x, y)
            return
        }

        val target = runCatching { service.targetAt(x, y) }.getOrNull()

        if (target == null) {
            translateRegionAt(x, y)
            return
        }

        if (target.isField) {
            replaceInPlace(target)
            return
        }

        translateIntoCard(target.text, x, y)
    }

    private fun replaceInPlace(target: TranslateAccessibilityService.Target) {
        val field = target.editable ?: return

        scope.launch {
            try {
                val result = QuickTranslator.translate(this@BubbleService, target.text)
                val done = TranslateAccessibilityService.instance?.replace(field, result.text) == true

                toast(
                    if (done) "Replaced with ${result.target.name}"
                    else "This app's text field refused the change"
                )
            } catch (e: Exception) {
                toast(e.message ?: "Translation failed")
            }
        }
    }

    private fun translateIntoCard(source: String, x: Int, y: Int) {
        card?.dismiss()
        card = TranslateCard(this).also { it.show(source, x, y) }

        scope.launch {
            try {
                val result = QuickTranslator.translate(this@BubbleService, source)
                card?.setResult(result.target.name, result.text)
            } catch (e: Exception) {
                card?.setError(e.message ?: "Translation failed")
            }
        }
    }

    /** Whole screen on a plain tap. */
    private fun translateWholeScreen(x: Int, y: Int) {
        if (capturing) return
        capturing = true

        scope.launch {
            try {
                val bitmap = captureWithoutBubble()
                val text = if (bitmap != null) Ocr.read(bitmap) else ""
                bitmap?.recycle()

                if (text.isBlank()) {
                    card?.dismiss()
                    card = TranslateCard(this@BubbleService).also {
                        it.show("", x, y)
                        it.setError("No readable text on this screen")
                    }
                } else {
                    translateIntoCard(text, x, y)
                }
            } finally {
                capturing = false
            }
        }
    }

    /** Fallback when the app hides its accessibility tree: OCR just the block under the point. */
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
                    translateIntoCard(text, x, y)
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

    override fun onDestroy() {
        card?.dismiss()
        card = null
        bubble?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubble = null
        windowManager = null
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
