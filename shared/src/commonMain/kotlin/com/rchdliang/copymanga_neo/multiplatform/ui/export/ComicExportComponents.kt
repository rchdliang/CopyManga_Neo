package com.rchdliang.copymanga_neo.multiplatform.ui.export

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat

/** 所有入口共用格式面板和系统选择器；选择器启动事件先消费，避免旋转后重复打开。 */
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
    if (!state.visible) return
    if (LocalRouteActive.current) ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (state.retry) "重试未完成的导出" else "导出下载内容",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.preparing) {
                CircularProgressIndicator()
                Text("正在检查本地章节与图片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.plan?.let { plan ->
                Text(
                    "${plan.comics.size} 部漫画 · ${plan.chapterCount} 章 · ${plan.pageCount} 张图片",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "原图约 ${platform.formatFileSize(plan.bytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plan.skipped.isNotEmpty()) {
                    Text("以下内容未纳入导出：", color = MaterialTheme.colorScheme.error)
                    plan.skipped.forEach {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                ComicExportFormat.entries.forEach { format ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.format == format,
                                enabled = !state.retry,
                                role = Role.RadioButton,
                                onClick = { viewModel.setFormat(format) }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.format == format,
                            onClick = null,
                            enabled = !state.retry
                        )
                        Text(
                            format.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                Text(
                    when (state.format) {
                        ComicExportFormat.ZIP -> "每部漫画一个 ZIP，保留分组和章节目录。"
                        ComicExportFormat.CBZ -> "每章一个 CBZ，图片按页序平铺；阅读器需支持原图格式。"
                        ComicExportFormat.IMAGES -> "按漫画、分组、章节保存原图文件夹。"
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = viewModel::pickLocation,
                    enabled = !progress.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择保存位置", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
