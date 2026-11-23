package com.paiwand.translate.overlay

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
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
}
