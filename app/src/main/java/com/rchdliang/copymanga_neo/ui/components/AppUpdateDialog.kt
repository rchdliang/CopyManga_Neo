package com.rchdliang.copymanga_neo.ui.components

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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.rchdliang.copymanga_neo.domain.model.AppReleaseChannel
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.AppRelease

/** 启动检查与关于页共用；说明保留发布原文，长内容滚动且不挤占操作按钮。 */
@Composable
fun AppUpdateDialog(release: AppRelease, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val migration = release.channel == AppReleaseChannel.CMP
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (migration) "CMP 正式版已发布" else "发现新版本", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (migration) "CMP 版本：${release.version}" else "最新版本：${release.version}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (migration) {
                    Text(
                        "建议迁移至 CMP 版，两版可独立安装、同时保留。迁移前可在旧版“设置备份”中选择“导出设置与阅读数据”，再到 CMP 导入设置、本地历史和书签。完整用户数据备份不兼容；批注、下载文件和小说缓存不会随设置备份转移。账号记录需登录同一账号查看，请确认迁移结果后再决定是否卸载旧版。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                runCatching { uri.openUri(release.pageUrl) }.onFailure { Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
                onDismiss()
            }) { Text(if (migration) "查看 CMP 发布页" else "查看发布页", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
