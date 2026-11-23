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

    /**
     * [editable] is the input field containing the point, if there is one. The field's text
     * often lives on a child node, so this is resolved by walking the whole chain, not just
     * the deepest node.
     */
    data class Target(
        val text: String,
        val node: AccessibilityNodeInfo,
        val editable: AccessibilityNodeInfo?
    ) {
        val isField: Boolean get() = editable != null && text.isNotBlank()
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

        val chain = ArrayList<AccessibilityNodeInfo>(8)
        if (!descend(root, x, y, chain, 0)) return null

        // Nearest editable ancestor wins: that is the field the user dropped onto.
        val field = chain.indices.reversed()
            .map { chain[it] }
            .firstOrNull { it.isEditable }

        if (field != null) {
            val fieldText = field.text?.toString()?.trim().orEmpty()
            if (fieldText.isNotBlank()) {
                return Target(text = fieldText, node = field, editable = field)
            }
        }

        // Otherwise it is a label, button or paragraph: take the deepest readable text.
        for (index in chain.indices.reversed()) {
            val node = chain[index]
            val text = node.text?.toString()?.trim().orEmpty()
                .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
            if (text.isNotBlank()) {
                return Target(text = text, node = node, editable = null)
            }
        }

        return null
    }

    /**
     * The bubble itself is one of our windows. If it is the active one, look through the other
     * interactive windows for the app the user is actually looking at.
     */
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

    fun replace(node: AccessibilityNodeInfo, value: String): Boolean {
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        val setText = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
        if (setText) return true

        // Some editors ignore ACTION_SET_TEXT but still accept a paste.
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("paiwand", value))

        val selected = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        }.getOrDefault(false)

        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false) || selected
    }

    companion object {
        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
