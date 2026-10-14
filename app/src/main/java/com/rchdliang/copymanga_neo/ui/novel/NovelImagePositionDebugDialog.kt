package com.rchdliang.copymanga_neo.ui.novel

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugSample
import kotlinx.coroutines.launch

/** 全局 Debug 弹窗；复制和选择保留完整 JSON，后台下载也可排队提示。 */
@Composable
internal fun NovelImagePositionDebugDialog(
    sample: NovelImagePositionDebugSample,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(sample.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("发现带行号的小说插图", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "以下插图的起止行号不全为 0，可作为位置样本核实。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        sample.json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("小说插图位置样本", sample.json))
                    )
                    copied = true
                }
            }) {
                Text(
                    if (copied) "已复制" else "复制 JSON",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
