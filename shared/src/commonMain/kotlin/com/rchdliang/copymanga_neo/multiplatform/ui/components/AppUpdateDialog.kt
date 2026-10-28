package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.AppRelease

/** 启动检查与关于页共用；说明保留发布原文，长内容滚动且不挤占操作按钮。 */
@Composable
fun AppUpdateDialog(release: AppRelease, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val toast = LocalToastHost.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("最新版本：${release.version}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("更新说明", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                SelectionContainer {
                    Text(
                        release.releaseNotes.takeIf { it.isNotBlank() } ?: "此版本未提供更新说明，可前往发布页查看。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { uri.openUri(release.pageUrl) }.onFailure { toast.showToast("无法打开浏览器") }
                onDismiss()
            }) { Text("查看发布页", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
