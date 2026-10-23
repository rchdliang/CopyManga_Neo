package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive

/** 打包方式先于文件格式；保留旧格式值，已有任务仍可恢复。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicExportDialogs(viewModel: ComicExportViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val platform = requireNotNull(LocalComicExportPlatform.current) {
        "Provide ComicExportPlatform with a real native document picker."
    }
    ActiveRouteLaunchedEffect(viewModel, platform, state.pickLocation) {
        if (state.pickLocation) viewModel.launchDestinationPicker(platform)
    }
    if (!state.visible || !LocalRouteActive.current) return
    ModalBottomSheet(
        onDismissRequest = viewModel::dismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            Modifier.testTag("comic-export-options").fillMaxWidth().heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (state.retry) "重试导出" else "导出", style = MaterialTheme.typography.headlineSmall)
                state.plan?.let { plan ->
                    Text("${plan.comics.size} 部漫画 · ${plan.chapterCount} 章 · 约 ${platform.formatFileSize(plan.bytes)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.preparing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("准备导出…")
                }
            }
            state.plan?.let { plan ->
                val byChapter = state.format == ComicExportFormat.CHAPTER_ZIP || state.format == ComicExportFormat.CBZ
                val mode = if (byChapter) ComicExportFormat.CHAPTER_ZIP else state.format
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ComicExportFormat.ZIP, ComicExportFormat.CHAPTER_ZIP, ComicExportFormat.IMAGES).forEach { option ->
                        val selected = mode == option
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                Modifier.fillMaxWidth().selectable(selected, enabled = !state.retry, role = Role.RadioButton,
                                    onClick = { if (!selected) viewModel.setFormat(option) }).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(selected, onClick = null, enabled = !state.retry)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.label, style = MaterialTheme.typography.titleSmall)
                                    Text(when (option) {
                                        ComicExportFormat.ZIP -> "每部一个 ZIP，章节分文件夹"
                                        ComicExportFormat.CHAPTER_ZIP -> "每章一个压缩包"
                                        else -> "按章节保存，不压缩"
                                    }, style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (byChapter) Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("格式", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    listOf(ComicExportFormat.CHAPTER_ZIP to "ZIP", ComicExportFormat.CBZ to "CBZ").forEach { (format, label) ->
                        FilterChip(selected = state.format == format, onClick = { viewModel.setFormat(format) },
                            label = { Text(label) }, enabled = !state.retry)
                    }
                }
                if (plan.skipped.isNotEmpty()) SkippedExportChapters(plan.skipped)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = viewModel::pickLocation,
                    enabled = !progress.running && !state.pickLocation && !state.preparing,
                    modifier = Modifier.fillMaxWidth()) { Text("选择保存位置") }
            }
            if (state.plan == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
