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

    data class Target(
        val text: String,
        val node: AccessibilityNodeInfo,
        val editable: AccessibilityNodeInfo?
    ) {
        val isField: Boolean get() = editable != null && text.isNotBlank()
    }

    enum class ReplaceResult {
        SetText,
        SetTextOnParent,
        Pasted,
        Failed
    }

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

    fun targetAt(x: Int, y: Int): Target? {
        val root = rootForTargeting() ?: return null

        // The user's finger is usually already inside the field they were typing in. Asking the
        // window who holds input focus is far more reliable than guessing from bounds.
        val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (focused != null && focused.isEditable) {
            val bounds = Rect()
            focused.getBoundsInScreen(bounds)
            val slack = (20 * resources.displayMetrics.density).toInt()
            bounds.inset(-slack, -slack)
            val focusedText = focused.text?.toString()?.trim().orEmpty()
            if (bounds.contains(x, y) && focusedText.isNotBlank()) {
                return Target(focusedText, focused, focused)
            }
        }

        val chain = ArrayList<AccessibilityNodeInfo>(8)
        if (!descend(root, x, y, chain, 0)) return null

        // Nearest editable ancestor wins: that is the field the user dropped onto.
        for (index in chain.indices.reversed()) {
            val node = chain[index]
            if (node.isEditable) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) return Target(text, node, node)
            }
        }

        // Otherwise it is a label, button or paragraph: take the deepest readable text.
        for (index in chain.indices.reversed()) {
            val node = chain[index]
            val text = node.text?.toString()?.trim().orEmpty()
                .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
            if (text.isNotBlank()) return Target(text, node, null)
        }

        return null
    }

    private fun rootForTargeting(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName != packageName) return active

        for (window in windows) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName != packageName) return root
        }
        return active
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

    /**
     * Three escalating attempts, because editors differ wildly:
     * 1. ACTION_SET_TEXT on the field
     * 2. ACTION_SET_TEXT on its parent (some custom widgets expose the action there)
     * 3. select everything, then ACTION_PASTE — WhatsApp and a few others only honour this
     */
    fun replace(node: AccessibilityNodeInfo, value: String): ReplaceResult {
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }

        if (setText(node, value)) return ReplaceResult.SetText

        val parent = runCatching { node.parent }.getOrNull()
        if (parent != null && setText(parent, value)) return ReplaceResult.SetTextOnParent

        if (paste(node, value)) return ReplaceResult.Pasted

        return ReplaceResult.Failed
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
    }

    private fun paste(node: AccessibilityNodeInfo, value: String): Boolean {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("paiwand", value))

        val length = node.text?.length ?: 0
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
        }
        runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        }

        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
    }

    companion object {
        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
