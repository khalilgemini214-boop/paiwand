package com.paiwand.translate.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives the bubble two things screen capture cannot: which element sits under a point,
 * and the ability to write text back into another app's input field.
 */
class TranslateAccessibilityService : AccessibilityService() {

    data class Target(val text: String, val editable: Boolean, val node: AccessibilityNodeInfo)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Walks down the node tree to the deepest element containing the point, then walks back up
     * until it finds something with readable text — a button's label often lives on a parent.
     */
    fun targetAt(x: Int, y: Int): Target? {
        val root = rootInActiveWindow ?: return null
        val chain = ArrayList<AccessibilityNodeInfo>(8)
        if (!descend(root, x, y, chain, 0)) return null

        for (index in chain.indices.reversed()) {
            val node = chain[index]
            val text = node.text?.toString()?.trim().orEmpty()
                .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
            if (text.isNotBlank()) {
                return Target(text = text, editable = node.isEditable, node = node)
            }
        }
        return null
    }

    private fun descend(
        node: AccessibilityNodeInfo,
        x: Int,
        y: Int,
        chain: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ): Boolean {
        if (depth > 48) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return false

        chain.add(node)
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (descend(child, x, y, chain, depth + 1)) return true
        }
        return true
    }

    fun replace(node: AccessibilityNodeInfo, value: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return true

        // Some editors ignore ACTION_SET_TEXT but still accept a paste.
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("paiwand", value))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    companion object {
        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
