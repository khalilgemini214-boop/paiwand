package com.paiwand.translate.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * A floating card in its own overlay window. It never takes focus and never opens an activity,
 * so the app underneath stays visible and keeps running.
 */
class TranslateCard(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private val isDark: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val surface = if (isDark) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    private val onSurface = if (isDark) 0xFFF2F3F5.toInt() else 0xFF16181C.toInt()
    private val muted = if (isDark) 0xFFA8AEBB.toInt() else 0xFF5B6270.toInt()
    private val primary = if (isDark) 0xFF4ED9A9.toInt() else 0xFF0E7C63.toInt()
    private val border = if (isDark) 0xFF262A31.toInt() else 0xFFE7E2D7.toInt()

    private var root: LinearLayout? = null
    private var sourceView: TextView? = null
    private var resultView: TextView? = null
    private var labelView: TextView? = null
    private var copyView: TextView? = null

    fun show(source: String, anchorX: Int, anchorY: Int) {
        dismiss()

        val container = build()
        sourceView?.text = source
        sourceView?.visibility = if (source.isBlank()) View.GONE else View.VISIBLE
        resultView?.text = "Translating…"
        resultView?.setTextColor(muted)
        copyView?.visibility = View.GONE

        val metrics = context.resources.displayMetrics
        val width = min((metrics.widthPixels - dp(24)), dp(340))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchorX - width / 2).coerceIn(dp(8), max(dp(8), metrics.widthPixels - width - dp(8)))
            y = anchorY + dp(46)
        }

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
                true
            } else {
                false
            }
        }

        runCatching { windowManager?.addView(container, params) }
        root = container
    }

    fun setResult(targetLanguage: String, text: String) {
        labelView?.text = targetLanguage.uppercase()
        resultView?.setTextColor(onSurface)
        resultView?.text = text
        copyView?.visibility = View.VISIBLE
        copyView?.text = "Copy"
    }

    fun setError(message: String) {
        labelView?.text = "PAIWAND"
        resultView?.setTextColor(if (isDark) 0xFFFFB4AB.toInt() else 0xFFB3261E.toInt())
        resultView?.text = message
        copyView?.visibility = View.GONE
    }

    fun dismiss() {
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
    }

    private fun build(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = 22f * density
                setColor(surface)
                setStroke(dp(1), border)
            }
            elevation = 16f * density
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primary)
            }
        }
        header.addView(dot, LinearLayout.LayoutParams(dp(7), dp(7)))

        labelView = TextView(context).apply {
            text = "PAIWAND"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(muted)
            setPadding(dp(8), 0, 0, 0)
            letterSpacing = 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        header.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val close = TextView(context).apply {
            text = "✕"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(muted)
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { dismiss() }
        }
        header.addView(close)

        container.addView(header)

        sourceView = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(muted)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        }
        container.addView(sourceView)

        val divider = View(context).apply {
            background = GradientDrawable().apply { setColor(border) }
        }
        container.addView(
            divider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(12)
            }
        )

        resultView = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(onSurface)
            setLineSpacing(dp(6).toFloat(), 1f)
            setPadding(0, dp(12), 0, 0)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        container.addView(resultView)

        copyView = TextView(context).apply {
            text = "Copy"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(primary)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
            visibility = View.GONE
            setOnClickListener {
                val value = resultView?.text?.toString().orEmpty()
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("paiwand", value))
                text = "Copied"
                runCatching {
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(copyView)

        return container
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
