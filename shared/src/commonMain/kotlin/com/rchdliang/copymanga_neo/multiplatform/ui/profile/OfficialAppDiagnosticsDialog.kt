package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost

/** 直接在实机显示设备属性；不触发网络访问或修改身份。 */
@Composable
internal fun OfficialAppDiagnosticsDialog(
    report: String,
    busy: Boolean,
    refresh: () -> Unit,
    close: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val toast = LocalToastHost.current
    if (LocalRouteActive.current) Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "实机属性检查",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "报告含设备标识，分享前请检查。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = refresh, enabled = !busy) {
                        Text(
                            if (busy) "读取中…" else "重新读取",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(report))
                        toast.showToast("报告已复制")
                    }) { Text("复制报告", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = close) {
                        Text(
                            "关闭",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                SelectionContainer(Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        report,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
