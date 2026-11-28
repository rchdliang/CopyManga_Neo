package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun UnsavedChangesDialog(
    busy: Boolean = false,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (() -> Unit)? = null,
    changes: (@Composable () -> Unit)? = null
) {
    if (!LocalRouteActive.current) return
    if (onSave != null) {
        SaveChangesPrompt(busy, onContinue, onDiscard, onSave, changes)
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onContinue() },
        title = { Text("放弃未保存的更改？") },
        text = { Column(Modifier.verticalScrollWithMouse(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("更改尚未保存。")
            OutlinedButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("不保存") }
        } },
        confirmButton = { TextButton(onClick = onContinue, enabled = !busy) { Text("继续编辑") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveChangesPrompt(
    busy: Boolean,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    changes: (@Composable () -> Unit)?
) {
    BasicAlertDialog(onDismissRequest = { if (!busy) onContinue() }, modifier = Modifier.widthIn(max = 448.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.padding(10.dp).size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("保存更改？", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScrollWithMouse(rememberScrollState())) {
                    if (changes != null) changes() else Text("保存后将返回上一页。", style = MaterialTheme.typography.bodyMedium)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val discard: @Composable () -> Unit = {
                        TextButton(onClick = onDiscard, enabled = !busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Text("不保存")
                        }
                    }
                    val keepEditing: @Composable () -> Unit = {
                        TextButton(onClick = onContinue, enabled = !busy) { Text("继续编辑") }
                    }
                    if (maxWidth >= 300.dp * LocalDensity.current.fontScale) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            discard()
                            Spacer(Modifier.weight(1f))
                            keepEditing()
                            Button(onClick = onSave, enabled = !busy) { Text("保存并返回") }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("保存并返回") }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                discard()
                                keepEditing()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmRemovalDialog(title: String, description: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String = "删除") {
    if (!LocalRouteActive.current) return
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
