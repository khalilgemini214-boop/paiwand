package com.paiwand.translate.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paiwand.translate.BuildConfig
import com.paiwand.translate.ui.AppViewModel
import com.paiwand.translate.ui.components.DotDivider
import com.paiwand.translate.ui.components.GradientButton
import com.paiwand.translate.ui.components.SectionLabel
import com.paiwand.translate.ui.components.SoftCard
import com.paiwand.translate.update.UpdatePhase

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEnableBubble: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val update by vm.updateState.collectAsState()

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var apiBase by remember { mutableStateOf(settings.apiBase) }
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(settings.apiKey, settings.apiBase, settings.model) {
        apiKey = settings.apiKey
        apiBase = settings.apiBase
        model = settings.model
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("Translation engine")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Column(Modifier.padding(18.dp)) {
                PaiwandField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API key",
                    placeholder = "sk-…",
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    trailing = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                PaiwandField(
                    value = apiBase,
                    onValueChange = { apiBase = it },
                    label = "API base URL",
                    placeholder = "https://api.deepseek.com/v1",
                    keyboardType = KeyboardType.Uri
                )
                Spacer(Modifier.height(12.dp))
                PaiwandField(
                    value = model,
                    onValueChange = { model = it },
                    label = "Model",
                    placeholder = "deepseek-chat"
                )
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = "Save",
                    enabled = apiKey.isNotBlank()
                ) { vm.saveEngine(apiKey, apiBase, model) }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { vm.testEngine(apiKey, apiBase, model) },
                        enabled = apiKey.isNotBlank() && !vm.engineTesting
                    ) {
                        Text(if (vm.engineTesting) "Testing…" else "Test connection")
                    }
                    Spacer(Modifier.weight(1f))
                }

                vm.engineStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (vm.engineOk) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        maxLines = 4
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "For personal use keep the key here. Before sharing the app, move it to a small proxy server so it is never shipped inside the APK.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("Magic translate")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Floating bubble",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.bubbleEnabled) "Active — tap the bubble to read the screen"
                            else "Reads text on screen and shows the translation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = settings.bubbleEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) onEnableBubble() else vm.disableBubble()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                DotDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Android asks for two permissions: draw over other apps, and screen capture. Both are needed — nothing is recorded or sent anywhere except the text you translate.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("Updates")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { vm.checkForUpdate(manual = true) },
                        enabled = !update.checking
                    ) {
                        Text(if (update.checking) "Checking…" else "Check now")
                    }
                }
                Spacer(Modifier.height(8.dp))
                DotDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Check automatically on start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = { vm.setAutoCheckUpdates(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                update.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (update.phase == UpdatePhase.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (update.info != null) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { vm.installUpdate() }) { Text("Update available — install") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("About")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Paiwand means “connection”. A quiet, fast translator for the languages you actually speak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Translation by DeepSeek · screen reading on-device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PaiwandField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        )
    }
}
