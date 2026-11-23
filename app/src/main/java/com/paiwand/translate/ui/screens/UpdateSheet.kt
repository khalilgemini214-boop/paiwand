package com.paiwand.translate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paiwand.translate.ui.components.GradientButton
import com.paiwand.translate.update.UpdatePhase
import com.paiwand.translate.update.UpdateUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val info = state.info

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            val title = when (state.phase) {
                UpdatePhase.Downloading -> "Downloading update"
                UpdatePhase.InstallLaunched -> "Finish the install"
                UpdatePhase.NeedsPermission -> "Allow installs"
                UpdatePhase.ReinstallRequired -> "Manual update needed"
                UpdatePhase.Failed -> "Update failed"
                else -> "Version ${info?.versionName ?: ""} is ready"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (state.phase) {
                    UpdatePhase.Downloading -> "${(state.progress * 100).roundToInt()}%"
                    UpdatePhase.InstallLaunched -> "Tap Update in the system dialog. Your data stays."
                    UpdatePhase.NeedsPermission -> "Android needs your permission to install the new version."
                    UpdatePhase.ReinstallRequired -> "This build changes its signature, so it must be installed fresh."
                    UpdatePhase.Failed -> state.message ?: "Something went wrong."
                    else -> info?.let { "New in this build" } ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (info != null && info.changelog.isNotEmpty() && state.phase == UpdatePhase.Available) {
                Spacer(Modifier.height(16.dp))
                info.changelog.take(6).forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (info != null && info.sizeBytes > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Download size ${"%.1f".format(info.sizeBytes / 1_048_576f)} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.phase == UpdatePhase.Downloading) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))

            when (state.phase) {
                UpdatePhase.Available -> {
                    GradientButton(text = "Update now", onClick = onInstall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Later")
                    }
                }

                UpdatePhase.NeedsPermission -> {
                    GradientButton(text = "Open settings", onClick = onGrantPermission)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text("I allowed it — continue")
                    }
                }

                UpdatePhase.ReinstallRequired -> {
                    GradientButton(text = "Download APK", onClick = onOpenDownload)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Not now")
                    }
                }

                UpdatePhase.InstallLaunched -> {
                    GradientButton(text = "Close", onClick = onDismiss)
                }

                UpdatePhase.Failed -> {
                    GradientButton(text = "Try again", onClick = onInstall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onOpenDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Download in browser")
                    }
                }

                else -> {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}
