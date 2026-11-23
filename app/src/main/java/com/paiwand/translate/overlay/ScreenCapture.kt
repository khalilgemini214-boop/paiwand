package com.paiwand.translate.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns a single MediaProjection session. Android 14 allows only one VirtualDisplay per
 * projection, so the display is created once and frames are drained continuously. A frame
 * is only converted to a Bitmap when someone asks for one.
 */
object ScreenCapture {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var handler: Handler? = null

    @Volatile
    private var pending: CompletableDeferred<Bitmap?>? = null

    val isActive: Boolean get() = projection != null

    fun start(context: Context, resultCode: Int, data: Intent): Boolean {
        stop()
        val manager = context.getSystemService(MediaProjectionManager::class.java) ?: return false
        val created = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull() ?: return false

        val main = Handler(Looper.getMainLooper())
        created.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, main)

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        imageReader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull()
            if (image == null) return@setOnImageAvailableListener

            val waiter = pending
            if (waiter != null) {
                pending = null
                val bitmap = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                waiter.complete(bitmap)
            } else {
                image.close()
            }
        }, main)

        val virtual = runCatching {
            created.createVirtualDisplay(
                "paiwand-capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                main
            )
        }.getOrNull()

        if (virtual == null) {
            runCatching { imageReader.close() }
            runCatching { created.stop() }
            return false
        }

        projection = created
        reader = imageReader
        display = virtual
        handler = main
        return true
    }

    suspend fun grab(timeoutMillis: Long = 2500): Bitmap? {
        if (reader == null) return null
        val waiter = CompletableDeferred<Bitmap?>()
        pending = waiter
        val result = withTimeoutOrNull(timeoutMillis) { waiter.await() }
        if (result == null) pending = null
        return result
    }

    fun stop() {
        pending?.complete(null)
        pending = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        handler = null
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }
}
