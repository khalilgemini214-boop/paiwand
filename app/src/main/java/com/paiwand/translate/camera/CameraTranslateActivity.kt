package com.paiwand.translate.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.paiwand.translate.overlay.Ocr
import com.paiwand.translate.translate.QuickTranslator
import com.paiwand.translate.ui.components.IconAction
import com.paiwand.translate.ui.theme.PaiwandTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraTranslateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PaiwandTheme {
                CameraScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun CameraScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val capture = remember { mutableStateOf<ImageCapture?>(null) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var targetName by remember { mutableStateOf("") }

    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        activity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    capture.value = imageCapture
                }.onFailure {
                    status = it.message ?: "Camera unavailable"
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { runCatching { provider?.unbindAll() } }
    }

    fun shoot() {
        val camera = capture.value ?: return
        if (busy) return

        busy = true
        status = "Reading…"
        original = null
        result = null

        val file = File(context.cacheDir, "scan-${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        camera.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(file.absolutePath)
                        }
                        file.delete()

                        if (bitmap == null) {
                            busy = false
                            status = "Could not read that photo"
                            return@launch
                        }

                        val text = Ocr.read(bitmap)
                        bitmap.recycle()

                        if (text.isBlank()) {
                            busy = false
                            status = "No readable text in the photo"
                            return@launch
                        }

                        original = text
                        status = null

                        try {
                            val translated = QuickTranslator.translateStreaming(context, text) { partial ->
                                result = partial
                            }
                            targetName = translated.target.name
                            result = translated.text
                        } catch (e: Exception) {
                            status = e.message ?: "Translation failed"
                        } finally {
                            busy = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    status = exception.message ?: "Camera error"
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera access is needed to read text from the world.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Allow camera")
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            IconAction(
                icon = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White,
                onClick = onClose
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (result != null || original != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (targetName.isBlank()) "PAIWAND" else targetName.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(Modifier.weight(1f))
                            IconAction(
                                icon = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                onClick = {
                                    clipboard.setText(AnnotatedString(result.orEmpty()))
                                }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = result.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 27.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        original?.let { source ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = source,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                result = null
                                original = null
                                status = null
                            }) {
                                Text("Scan again")
                            }
                            Spacer(Modifier.weight(1f))
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Shutter(enabled = !busy && capture.value != null, onClick = { shoot() })
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = status ?: "Point at text and tap",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White.copy(alpha = if (enabled) 0.95f else 0.35f), CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onClick() }
    )
}
