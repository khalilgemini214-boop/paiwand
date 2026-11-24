package com.paiwand.translate.overlay

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
import android.widget.ScrollView
import android.widget.TextView
import com.paiwand.translate.data.Languages
import kotlin.math.max
import kotlin.math.min

/**
 * The panel the bubble turns into when tapped: pick the target language, translate the whole
 * screen, or close back to the bubble. Movable by its header.
 */
class BubbleMenu(
    private val context: Context,
    private var selected: String,
    private val onSelect: (String) -> Unit,
    private val onTranslateScreen: () -> Unit,
    private val onClose: () -> Unit
) {

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
    private var params: WindowManager.LayoutParams? = null
    private var headerView: View? = null
    private val rows = mutableListOf<Pair<String, LinearLayout>>()

    val isShowing: Boolean get() = root != null

    fun show(anchorX: Int, anchorY: Int) {
        dismiss()

        val container = build()
        val screen = context.resources.displayMetrics
        val width = min(screen.widthPixels - dp(24), dp(300))

        val layout = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (anchorX - width / 2)
                .coerceIn(dp(8), max(dp(8), screen.widthPixels - width - dp(8)))
            this.y = anchorY
        }

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                onClose()
                true
            } else {
                false
            }
        }

        runCatching { windowManager?.addView(container, layout) }
        root = container
        params = layout

        headerView?.let { OverlayDrag.attach(it, windowManager, layout) }

        highlight(selected)

        container.post {
            val height = container.height
            if (height > 0) {
                layout.y = layout.y.coerceIn(dp(8), max(dp(8), screen.heightPixels - height - dp(8)))
                runCatching { windowManager?.updateViewLayout(container, layout) }
            }
        }
    }

    fun setSelected(code: String) {
        selected = code
        highlight(code)
    }

    fun dismiss() {
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        params = null
        headerView = null
        rows.clear()
    }

    private fun build(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 22f * density
                setColor(surface)
                setStroke(dp(1), border)
            }
            elevation = 16f * density
        }

        val header = buildHeader()
        headerView = header
        container.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(TextView(context).apply {
            text = "TRANSLATE INTO"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(muted)
            letterSpacing = 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (language in Languages.targets) {
            val row = languageRow(language.code, language.name, language.native)
            rows.add(language.code to row)
            list.addView(row)
        }

        val scroll = MaxHeightScrollView(context, (context.resources.displayMetrics.heightPixels * 0.42f).toInt()).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        container.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            View(context).apply { background = GradientDrawable().apply { setColor(border) } },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
            }
        )

        container.addView(TextView(context).apply {
            text = "Translate this screen"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(primary)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
            setOnClickListener { onTranslateScreen() }
        })

        return container
    }

    private fun buildHeader(): LinearLayout {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primary)
            }
        }, LinearLayout.LayoutParams(dp(7), dp(7)))

        header.addView(TextView(context).apply {
            text = "Paiwand"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(onSurface)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        header.addView(TextView(context).apply {
            text = "✕"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(muted)
            setPadding(dp(12), 0, dp(2), 0)
            setOnClickListener { onClose() }
        })

        return header
    }

    private fun languageRow(code: String, name: String, native: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            setOnClickListener {
                selected = code
                highlight(code)
                onSelect(code)
            }
        }

        row.addView(TextView(context).apply {
            text = name
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(onSurface)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(context).apply {
            text = native
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(muted)
        })

        return row
    }

    private fun highlight(code: String) {
        for ((rowCode, row) in rows) {
            val active = rowCode == code
            row.background = if (active) {
                GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(if (isDark) 0xFF14281F.toInt() else 0xFFD6F2E6.toInt())
                }
            } else {
                null
            }
            (row.getChildAt(0) as? TextView)?.setTextColor(if (active) primary else onSurface)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class MaxHeightScrollView(
        context: Context,
        private val maxHeight: Int
    ) : ScrollView(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, capped)
        }
    }
}
