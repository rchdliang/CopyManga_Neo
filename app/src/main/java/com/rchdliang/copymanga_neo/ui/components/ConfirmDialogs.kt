package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun UnsavedChangesDialog(
    busy: Boolean = false,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onContinue() },
        title = { Text(if (onSave != null) "保存更改？" else "放弃未保存的更改？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("更改尚未保存。")
            if (onSave != null) Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("保存并返回") }
            OutlinedButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("不保存") }
        } },
        confirmButton = { TextButton(onClick = onContinue, enabled = !busy) { Text("继续编辑") } }
    )
}

@Composable
internal fun ConfirmRemovalDialog(title: String, description: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String = "删除") {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
