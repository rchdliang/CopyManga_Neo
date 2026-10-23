package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComicExportSelectionScreen(
    viewModel: ComicExportSelectionViewModel,
    exportViewModel: ComicExportViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportUi by exportViewModel.uiState.collectAsStateWithLifecycle()
    val progress by exportViewModel.progress.collectAsStateWithLifecycle()
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var rangeGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    ActiveRouteLaunchedEffect(exportUi.started) {
        if (exportUi.started) onBack()
    }
    ComicExportDialogs(exportViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择导出章节") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { viewModel.selectedPlan()?.let(exportViewModel::preparePlan) },
                        enabled = state.selected.isNotEmpty() && !state.loading && !exportUi.visible && !progress.running,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                    ) { Text("导出 ${state.selected.size} 章") }
                    if (progress.running) Text("已有导出任务正在执行", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("检查章节…")
                }
                state.error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text("重试") }
                }
                else -> LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "selection_summary") {
                        Column {
                            Text(
                                "${state.plan?.comics?.size ?: 0} 部漫画 · ${state.allKeys.size} 章可导出",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = viewModel::selectAll) { Text("全选") }
                                TextButton(onClick = viewModel::invert) { Text("反选") }
                                TextButton(onClick = viewModel::clear) { Text("清空") }
                            }
                        }
                    }
                    if (state.plan?.skipped?.isNotEmpty() == true) {
                        item(key = "skipped") { SkippedExportChapters(state.plan!!.skipped) }
                    }
                    state.groups.forEach { group ->
                        val expanded = group.id !in collapsed
                        item(key = "group:${group.id}") {
                            val count = group.keys.count { it in state.selected }
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TriStateCheckbox(
                                        state = when (count) {
                                            0 -> ToggleableState.Off
                                            group.keys.size -> ToggleableState.On
                                            else -> ToggleableState.Indeterminate
                                        },
                                        onClick = { viewModel.toggleGroup(group) },
                                        modifier = Modifier.semantics {
                                            contentDescription = "${group.comicName} · ${group.displayName()} · 全选本组"
                                        }
                                    )
                                    Column(Modifier.weight(1f).clickable {
                                        collapsed = if (expanded) collapsed + group.id else collapsed - group.id
                                    }.padding(vertical = 8.dp)) {
                                        Text(group.comicName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text((if (group.name == "default") "" else "${group.displayName()} · ") + "$count / ${group.chapters.size} 章",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { rangeGroupId = group.id }) { Text("区间") }
                                    IconButton(onClick = {
                                        collapsed = if (expanded) collapsed + group.id else collapsed - group.id
                                    }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        if (expanded) "收起章节" else "展开章节") }
                                }
                            }
                        }
                        if (expanded) itemsIndexed(group.chapters, key = { _, chapter -> "chapter:${group.id}:${chapter.uuid}" }) { index, chapter ->
                            val key = ExportChapterKey(group.comic, chapter.uuid)
                            val selected = key in state.selected
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .toggleable(selected, role = Role.Checkbox, onValueChange = { viewModel.toggle(key) })
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(selected, onCheckedChange = null)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${index + 1}. ${chapter.name}", style = MaterialTheme.typography.bodyLarge)
                                    Text("${chapter.pages.size} 张图片", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.groups.firstOrNull { it.id == rangeGroupId }?.let { group ->
        if (LocalRouteActive.current) key(group.id) {
            ExportRangeDialog(group, onDismiss = { rangeGroupId = null }) { first, last ->
                viewModel.selectRange(group, first, last)
                rangeGroupId = null
            }
        }
    }
}

private fun ExportChapterGroup.displayName(): String = if (name == "default") "默认分组" else name

@Composable
internal fun SkippedExportChapters(skipped: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text("${skipped.size} 项未纳入")
            Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起原因" else "查看原因")
        }
        if (expanded) skipped.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun ExportRangeDialog(group: ExportChapterGroup, onDismiss: () -> Unit, onSelect: (Int, Int) -> Unit) {
    var first by rememberSaveable { mutableStateOf("1") }
    var last by rememberSaveable { mutableStateOf(group.chapters.size.toString()) }
    val start = first.toIntOrNull()
    val end = last.toIntOrNull()
    val valid = start != null && end != null && start in 1..group.chapters.size && end in start..group.chapters.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择区间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${group.comicName} · ${group.displayName()}")
                Text("序号 1–${group.chapters.size} · 替换本组选择", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(first, { first = it }, label = { Text("起始序号") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(last, { last = it }, label = { Text("结束序号") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                if (valid) {
                    Text("${group.chapters[start - 1].name} 至 ${group.chapters[end - 1].name} · 共 ${end - start + 1} 章",
                        style = MaterialTheme.typography.bodySmall)
                } else Text("序号范围无效", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(start!!, end!!) }, enabled = valid) { Text("选择") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
