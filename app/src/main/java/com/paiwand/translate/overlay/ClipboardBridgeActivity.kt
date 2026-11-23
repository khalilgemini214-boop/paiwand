package com.paiwand.translate.overlay

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * A window that exists only to read the clipboard. Android 10+ only lets the app with focus
 * read it, and an overlay service never has focus — so the long-press and the notification
 * action both come through here.
 */
class ClipboardBridgeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()

        if (!text.isNullOrBlank()) {
            BubbleService.showClipboard(this, text)
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
