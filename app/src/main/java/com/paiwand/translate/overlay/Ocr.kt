package com.paiwand.translate.overlay

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object Ocr {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        runCatching {
            client.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text.trim())
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume("")
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume("")
                }
        }.onFailure {
            if (continuation.isActive) continuation.resume("")
        }
    }

    /**
     * Reads only the text block sitting under a point, which is what the bubble means when it is
     * dropped on a label or a paragraph. If nothing contains the point, the nearest block wins.
     */
    suspend fun readAt(bitmap: Bitmap, x: Int, y: Int): String =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                client.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(blockAt(result, x, y))
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume("")
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume("")
                    }
            }.onFailure {
                if (continuation.isActive) continuation.resume("")
            }
        }

    private fun blockAt(result: Text, x: Int, y: Int): String {
        val blocks = result.textBlocks.filter { it.boundingBox != null }
        if (blocks.isEmpty()) return ""

        blocks.firstOrNull { block ->
            block.boundingBox?.let { it.contains(x, y) } == true
        }?.let { return it.text.trim() }

        var nearest: Text.TextBlock? = null
        var bestDistance = Long.MAX_VALUE
        for (block in blocks) {
            val box: Rect = block.boundingBox ?: continue
            val dx = (box.centerX() - x).toLong()
            val dy = (box.centerY() - y).toLong()
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                nearest = block
            }
        }
        return nearest?.text?.trim().orEmpty()
    }
}
