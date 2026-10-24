package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.reading.*

/** 生产设置与 Preview 共用渲染；下载、推理、持久化由调用者管理。 */
@Composable
internal fun ReaderSrSettingsBody(
    settings: SrUserSettings, unavailableReason: String?, busyDeleting: Boolean,
    onEnable: (Boolean) -> Unit, onSettings: (SrUserSettings) -> Unit,
    currentPage: @Composable () -> Unit, model: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("AI 增强", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(settings.enabled, onEnable, enabled = unavailableReason == null && !busyDeleting,
            modifier = Modifier.testTag("reader-setting-超分辨率"))
    }
    Text(unavailableReason ?: "实验 · 处理较慢，完成前显示原图。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (unavailableReason == null) {
        Spacer(Modifier.height(4.dp))
        ReaderInlineChoice(Icons.Outlined.Memory, "处理方式", settings.mode, SrExecutionMode.entries,
            { when (it) { SrExecutionMode.AUTO -> "自动"; SrExecutionMode.CPU -> "CPU"; SrExecutionMode.GPU -> "GPU" } },
            contained = false) { onSettings(settings.copy(mode = it)) }
        if (settings.mode == SrExecutionMode.AUTO) ReaderSettingSwitch("GPU 不可用时使用 CPU", settings.cpuFallback) {
            onSettings(settings.copy(cpuFallback = it))
        }
        currentPage()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    model()
}
