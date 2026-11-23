package com.paiwand.translate.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.paiwand.translate.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val updatesConfigured: Boolean get() = BuildConfig.UPDATE_URL.isNotBlank()

    suspend fun check(manual: Boolean = false) {
        val url = BuildConfig.UPDATE_URL
        if (url.isBlank()) {
            if (manual) {
                _state.update {
                    it.copy(phase = UpdatePhase.Failed, message = "Update channel is not configured yet")
                }
            }
            return
        }

        _state.update { it.copy(checking = true) }

        val result = runCatching { withContext(Dispatchers.IO) { fetch(url) } }

        result.onSuccess { info ->
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                _state.update {
                    it.copy(
                        checking = false,
                        info = info,
                        phase = if (info.requiresReinstall) UpdatePhase.ReinstallRequired else UpdatePhase.Available,
                        message = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        checking = false,
                        info = null,
                        phase = UpdatePhase.Idle,
                        message = if (manual) "You are on the latest version" else null
                    )
                }
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    checking = false,
                    phase = if (manual) UpdatePhase.Failed else UpdatePhase.Idle,
                    message = if (manual) (error.message ?: "Could not reach the update server") else null
                )
            }
        }
    }

    private fun fetch(url: String): UpdateInfo {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update check failed (${response.code})")
            val payload = response.body?.string().orEmpty()
            if (payload.isBlank()) throw IOException("Update manifest is empty")
            return json.decodeFromString(UpdateInfo.serializer(), payload)
        }
    }

    fun dismiss() {
        _state.update { it.copy(phase = UpdatePhase.Idle, info = null, progress = 0f, message = null) }
    }

    fun install() {
        val info = _state.value.info ?: return

        if (!canInstallPackages()) {
            _state.update { it.copy(phase = UpdatePhase.NeedsPermission, message = null) }
            return
        }

        if (info.requiresReinstall || (BuildConfig.DOWNLOAD_URL.isBlank() && info.apkUrl.isBlank())) {
            _state.update { it.copy(phase = UpdatePhase.ReinstallRequired) }
            return
        }

        scope.launch { download(info) }
    }

    fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    private fun download(info: UpdateInfo) {
        val url = info.apkUrl.ifBlank { BuildConfig.DOWNLOAD_URL }
        if (url.isBlank()) {
            _state.update { it.copy(phase = UpdatePhase.Failed, message = "No download link available") }
            return
        }

        _state.update { it.copy(phase = UpdatePhase.Downloading, progress = 0f, message = null) }

        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { downloadApk(url, info) }
            }

            outcome.onSuccess { file ->
                runCatching { launchInstaller(file, info) }
                    .onSuccess {
                        _state.update { it.copy(phase = UpdatePhase.InstallLaunched, progress = 1f) }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                phase = UpdatePhase.ReinstallRequired,
                                message = error.message ?: "Install could not start"
                            )
                        }
                    }
            }.onFailure { error ->
                _state.update {
                    it.copy(phase = UpdatePhase.Failed, message = error.message ?: "Download failed")
                }
            }
        }
    }

    private fun downloadApk(url: String, info: UpdateInfo): File {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            val body = response.body ?: throw IOException("Download was empty")
            val total = body.contentLength()
            val target = File(context.cacheDir, "paiwand-${info.versionCode}-${info.versionName}.apk")
            if (target.exists()) target.delete()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val progress = (done.toFloat() / total).coerceIn(0f, 1f)
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                    output.flush()
                }
            }
            return target
        }
    }

    private fun launchInstaller(file: File, info: UpdateInfo) {
        registerStatusReceiver()

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            val out = session.openWrite("base.apk", 0, file.length())
            file.inputStream().use { it.copyTo(out) }
            session.fsync(out)
            out.close()

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntentFlags.MUTABLE
            } else {
                PendingIntentFlags.UPDATE
            }

            val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            @Suppress("UnspecifiedImmutableFlag")
            val pending = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or flags
            )
            session.commit(pending.intentSender)
        }
    }

    private fun registerStatusReceiver() {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(confirm) }
                        }
                        _state.update { it.copy(phase = UpdatePhase.InstallLaunched) }
                    }

                    PackageInstaller.STATUS_SUCCESS -> {
                        _state.update { it.copy(phase = UpdatePhase.Idle, message = "Updated") }
                    }

                    else -> {
                        val signatureProblem = message.contains("INCOMPATIBLE", true) ||
                            message.contains("signature", true) ||
                            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                        _state.update {
                            it.copy(
                                phase = if (signatureProblem) UpdatePhase.ReinstallRequired else UpdatePhase.Failed,
                                message = message.ifBlank { "Install failed ($status)" }
                            )
                        }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            listener,
            IntentFilter(ACTION_INSTALL_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = listener
    }

    fun openDownloadPage() {
        val info = _state.value.info
        val url = info?.releasePage?.takeIf { it.isNotBlank() }
            ?: BuildConfig.RELEASE_PAGE.takeIf { it.isNotBlank() }
            ?: info?.apkUrl?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DOWNLOAD_URL.takeIf { it.isNotBlank() }
            ?: return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private object PendingIntentFlags {
        const val MUTABLE = android.app.PendingIntent.FLAG_MUTABLE
        const val UPDATE = 0
    }

    private companion object {
        const val ACTION_INSTALL_STATUS = "com.paiwand.translate.INSTALL_STATUS"
    }
}
