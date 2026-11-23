package com.paiwand.translate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.paiwand.translate.overlay.BubbleService
import com.paiwand.translate.ui.AppViewModel
import com.paiwand.translate.ui.screens.SettingsScreen
import com.paiwand.translate.ui.screens.TranslateScreen
import com.paiwand.translate.ui.screens.UpdateSheet
import com.paiwand.translate.ui.theme.PaiwandTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readSharedText(intent)
        setContent {
            PaiwandTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) vm.onInputChange(shared.trim())
        }
    }
}

private enum class Screen { Home, Settings }

@Composable
private fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }

    val settings = vm.settings.collectAsState().value
    val update by vm.updateState.collectAsState()

    var firstCheck by remember { mutableStateOf(true) }
    LaunchedEffect(settings.autoCheckUpdates) {
        if (firstCheck && settings.autoCheckUpdates) {
            firstCheck = false
            vm.checkForUpdate(manual = false)
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            BubbleService.start(context, result.resultCode, data)
            vm.enableBubble()
        } else {
            vm.toast("Screen access was declined")
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            startProjectionRequest(context, projectionLauncher)
        } else {
            vm.toast("Allow “display over other apps” to use the bubble")
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val enableBubble: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayLauncher.launch(intent)
        } else {
            startProjectionRequest(context, projectionLauncher)
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == Screen.Settings) {
                (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)))
                    .togetherWith(fadeOut(tween(120)))
            } else {
                (fadeIn(tween(220))).togetherWith(
                    slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160))
                )
            }
        },
        label = "screens"
    ) { current ->
        when (current) {
            Screen.Home -> TranslateScreen(
                vm = vm,
                onOpenSettings = { screen = Screen.Settings },
                hasUpdate = update.visible
            )

            Screen.Settings -> SettingsScreen(
                vm = vm,
                onBack = { screen = Screen.Home },
                onEnableBubble = enableBubble
            )
        }
    }

    if (update.visible) {
        UpdateSheet(
            state = update,
            onDismiss = { vm.dismissUpdate() },
            onInstall = { vm.installUpdate() },
            onGrantPermission = { vm.requestInstallPermission() },
            onOpenDownload = { vm.openDownloadPage() }
        )
    }
}

private fun startProjectionRequest(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val manager = context.getSystemService(MediaProjectionManager::class.java)
    val intent = manager?.createScreenCaptureIntent()
    if (intent == null) {
        return
    }
    runCatching { launcher.launch(intent) }
}
