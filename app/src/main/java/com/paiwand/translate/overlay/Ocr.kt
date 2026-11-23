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
     * Reads only the text block sitting under a point. Unlike a tap on a blank area, a miss here
     * must return nothing — picking the nearest block would translate whatever happened to be
     * elsewhere on the screen.
     */
    suspend fun readAt(bitmap: Bitmap, x: Int, y: Int, tolerancePx: Int): String =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                client.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(blockAt(result, x, y, tolerancePx))
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

    private fun blockAt(result: Text, x: Int, y: Int, tolerancePx: Int): String {
        for (block in result.textBlocks) {
            val box: Rect = block.boundingBox ?: continue
            box.inset(-tolerancePx, -tolerancePx)
            if (box.contains(x, y)) return block.text.trim()
        }
        return ""
    }
}
