package com.paiwand.translate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paiwand.translate.data.HistoryItem
import com.paiwand.translate.data.Language
import com.paiwand.translate.ui.AppViewModel
import com.paiwand.translate.ui.components.GradientButton
import com.paiwand.translate.ui.components.IconAction
import com.paiwand.translate.ui.components.MonogramBadge
import com.paiwand.translate.ui.components.SoftCard

@Composable
fun TranslateScreen(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    hasUpdate: Boolean
) {
    val settings by vm.settings.collectAsState()
    val history by vm.history.collectAsState()
    val clipboard = LocalClipboardManager.current

    var picking by remember { mutableStateOf<PickerTarget?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Header(onOpenSettings = onOpenSettings, hasUpdate = hasUpdate)
        Spacer(Modifier.height(24.dp))
        LanguageBar(
            source = vm.sourceLanguage,
            target = vm.targetLanguage,
            onSwap = vm::swapLanguages,
            onPickSource = { picking = PickerTarget.Source },
            onPickTarget = { picking = PickerTarget.Target }
        )
        Spacer(Modifier.height(16.dp))
        InputCard(
            value = vm.input,
            onValueChange = vm::onInputChange,
            onClear = vm::clearInput
        )
        Spacer(Modifier.height(14.dp))
        GradientButton(
            text = "Translate",
            loading = vm.busy,
            enabled = vm.input.isNotBlank(),
            onClick = vm::translate
        )

        vm.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        AnimatedVisibility(
            visible = vm.result != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 },
            exit = fadeOut(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                ResultCard(
                    result = vm.result.orEmpty(),
                    targetName = vm.targetLanguage.name,
                    onCopy = {
                        clipboard.setText(AnnotatedString(vm.result.orEmpty()))
                        vm.toast("Copied")
                    },
                    onClose = { vm.dismissResult() }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (history.isEmpty()) {
            Suggestions(onPick = { vm.onInputChange(it) })
        } else {
            RecentHeader(onClearAll = vm::clearHistory)
            Spacer(Modifier.height(10.dp))
            history.take(12).forEach { item ->
                RecentRow(
                    item = item,
                    onOpen = { vm.useHistory(item) },
                    onDelete = { vm.deleteHistory(item) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (!settings.configured) {
            Spacer(Modifier.height(8.dp))
            SetupHint(onOpenSettings = onOpenSettings)
        }

        Spacer(Modifier.height(28.dp))
    }

    picking?.let { target ->
        LanguageSheet(
            title = if (target == PickerTarget.Source) "Translate from" else "Translate into",
            selected = if (target == PickerTarget.Source) vm.sourceCode else vm.targetCode,
            allowAuto = target == PickerTarget.Source,
            onDismiss = { picking = null },
            onSelect = { code ->
                if (target == PickerTarget.Source) vm.setSource(code) else vm.setTarget(code)
                picking = null
            }
        )
    }
}

enum class PickerTarget { Source, Target }

@Composable
private fun Header(onOpenSettings: () -> Unit, hasUpdate: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF12A07C))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "پ",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Paiwand",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Bridge every language",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconAction(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings
            )
            if (hasUpdate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        }
    }
}

@Composable
private fun LanguageBar(
    source: Language,
    target: Language,
    onSwap: () -> Unit,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguagePill(
            language = source,
            modifier = Modifier.weight(1f),
            onClick = onPickSource
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSwap() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Swap languages",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        LanguagePill(
            language = target,
            modifier = Modifier.weight(1f),
            onClick = onPickTarget
        )
    }
}

@Composable
private fun LanguagePill(
    language: Language,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonogramBadge(text = language.badge, size = 26)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = language.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = language.native,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    SoftCard {
        Column(Modifier.padding(18.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Type or paste text…",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${value.length} characters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (value.isNotEmpty()) {
                    IconAction(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Clear",
                        onClick = onClear
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: String,
    targetName: String,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = targetName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    letterSpacing = 1.1.sp
                )
                Spacer(Modifier.weight(1f))
                IconAction(
                    icon = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onCopy
                )
                IconAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onClose
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = result,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 19.sp,
                    lineHeight = 29.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column {
        Text(
            text = "Start with",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.1.sp
        )
        Spacer(Modifier.height(10.dp))
        SuggestionRow("Good morning, how are you?", onPick)
        Spacer(Modifier.height(8.dp))
        SuggestionRow("Where is the nearest hospital?", onPick)
        Spacer(Modifier.height(8.dp))
        SuggestionRow("Please write this down for me.", onPick)
    }
}

@Composable
private fun SuggestionRow(text: String, onPick: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(text) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentHeader(onClearAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Recent",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Clear",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onClearAll() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RecentRow(
    item: HistoryItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconAction(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SetupHint(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSettings() },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "One step left",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Add a DeepSeek API key in Settings to enable translation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun RowSpacing(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
