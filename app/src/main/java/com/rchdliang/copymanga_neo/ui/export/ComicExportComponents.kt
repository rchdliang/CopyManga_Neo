package com.rchdliang.copymanga_neo.ui.export

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri =
                result.data?.data.takeIf { result.resultCode == android.app.Activity.RESULT_OK }
            if (uri != null) {
                val flags = result.data?.flags ?: 0
                if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
                                if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                else Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            else Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (error: SecurityException) {
                        // 本次授权仍可写入；重启后的授权失效会在重试时让用户重新选位置。
                        android.util.Log.w("ComicExport", "文档授权无法持久化", error)
                    }
                }
            }
            viewModel.saveDestination(uri)
        }
    LaunchedEffect(state.pickLocation) {
        val location = viewModel.claimLocation() ?: return@LaunchedEffect
        try {
            picker.launch(Intent(if (location.directory) Intent.ACTION_OPEN_DOCUMENT_TREE else Intent.ACTION_CREATE_DOCUMENT).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                if (!location.directory) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = location.mimeType
                    putExtra(Intent.EXTRA_TITLE, location.name)
                }
            })
        } catch (error: Exception) {
            viewModel.saveDestination(null, "无法打开系统文件选择器：${error.message}")
        }
    }
    if (!state.visible) return
    ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
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
                    "原图约 ${Formatter.formatFileSize(context, plan.bytes)}",
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
