package com.paiwand.translate.overlay

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Makes an overlay window movable by dragging one of its views. The container stays where the
 * user put it; nothing re-centres it behind their back.
 */
object OverlayDrag {

    fun attach(
        handle: View,
        windowManager: WindowManager?,
        params: WindowManager.LayoutParams,
        onMoved: () -> Unit = {}
    ) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f

        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    onMoved()
                    runCatching { windowManager?.updateViewLayout(view.rootView, params) }
                    true
                }

                else -> true
            }
        }
    }
}
