package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.model.AppReleaseTarget
import com.rchdliang.copymanga_neo.domain.model.WindowsReleasePackage
import com.rchdliang.copymanga_neo.domain.model.downloadUrlFor
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 启动检查与关于页共用；说明保留发布原文，长内容滚动且不挤占操作按钮。 */
@Composable
fun AppUpdateDialog(
    release: AppRelease,
    onDismiss: () -> Unit,
    downloadTarget: AppReleaseTarget? = LocalUiPlatform.current?.appReleaseTarget
) {
    val uri = LocalUriHandler.current
    val toast = LocalToastHost.current
    val downloads = when (downloadTarget) {
        is AppReleaseTarget.Windows -> listOf(
            "ZIP" to release.downloadUrlFor(downloadTarget.copy(packageType = WindowsReleasePackage.PORTABLE)),
            "EXE" to release.downloadUrlFor(downloadTarget.copy(packageType = WindowsReleasePackage.INSTALLER))
        ).mapNotNull { (label, url) -> url?.let { label to it } }
        else -> listOf("GitHub" to (release.downloadUrlFor(downloadTarget) ?: release.pageUrl))
    }.ifEmpty { listOf("GitHub" to release.pageUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScrollWithMouse(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(release.version, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                SelectionContainer {
                    Text(
                        release.releaseNotes.takeIf { it.isNotBlank() } ?: "此版本未提供更新说明，可前往发布页查看。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                downloads.forEach { (label, url) ->
                    TextButton(onClick = {
                        runCatching { uri.openUri(url) }.recoverCatching { error ->
                            if (url == release.pageUrl) throw error
                            uri.openUri(release.pageUrl)
                        }.onFailure { toast.showToast("无法打开浏览器") }
                        onDismiss()
                    }) { Text(label, color = MaterialTheme.colorScheme.primary) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

/** 预览说明为示例内容，不代表真实发布记录。 */
internal val appUpdatePreviewRelease = AppRelease(
    tag = "v1.2.3",
    version = "1.2.3",
    pageUrl = "https://github.com/rchdliang/CopyManga_Neo/releases/tag/v1.2.3",
    releaseNotes = """
        优化漫画双页阅读体验
        修复章节切换后阅读位置偏移的问题
        改善 Windows 鼠标滚轮与键盘操作
        支持下载 Windows 免安装 ZIP
        调整发布附件命名，方便识别构建版本
        更新前建议备份阅读记录与个人设置
    """.trimIndent()
)

@Composable
internal fun AppUpdateDialogPreviewContent(darkTheme: Boolean, onDismiss: () -> Unit = {}) {
    CopyManga_NeoTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            AppUpdateDialog(appUpdatePreviewRelease, onDismiss)
        }
    }
}

@Preview(name = "更新提示 · 浅色", widthDp = 412, heightDp = 640, showBackground = true)
@Composable
private fun AppUpdateDialogLightPreview() = AppUpdateDialogPreviewContent(darkTheme = false)

@Preview(name = "更新提示 · 深色", widthDp = 412, heightDp = 640, showBackground = true)
@Composable
private fun AppUpdateDialogDarkPreview() = AppUpdateDialogPreviewContent(darkTheme = true)
