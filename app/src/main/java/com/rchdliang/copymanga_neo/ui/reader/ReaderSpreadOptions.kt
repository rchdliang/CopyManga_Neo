package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings

internal fun readerLayoutStatus(mode: String, active: Boolean, continuous: Boolean): String = when {
    continuous -> "连续阅读使用单页"
    mode == "SINGLE" -> "当前使用单页"
    mode == "AUTO" && active -> "当前使用双页 · 随窗口调整"
    mode == "AUTO" -> "当前使用单页 · 窗口较宽时显示双页"
    active -> "当前使用双页"
    else -> "当前使用单页 · 横向宽屏可显示双页"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSpreadOptions(
    tools: ReaderToolsSettings, onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit,
    chapter: String?, page: Int, count: Int, spreadActive: Boolean, continuous: Boolean,
    pageLayout: String, onPageLayoutChange: (String) -> Unit
) {
    val navigate = LocalReaderSettingsNavigation.current
    Text("页面布局", style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SINGLE" to "单页", "DOUBLE" to "双页", "AUTO" to "自动").forEach { (mode, label) ->
            FilterChip(selected = pageLayout == mode, onClick = { onPageLayoutChange(mode) }, label = { Text(label) }, modifier = Modifier.weight(1f))
        }
    }
    Text(readerLayoutStatus(pageLayout, spreadActive, continuous), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val boundaries = tools.pairingRestarts[chapter].orEmpty()
    TextButton(enabled = !chapter.isNullOrBlank(), onClick = {
        navigate(ReaderSettingsPage("本章配页") { ReaderPairingEditor(tools, onChange, chapter, page, count) { navigate(null) } })
    }) { Text("本章配页 · " + if (boundaries.isEmpty()) "默认配页" else "已调整 ${boundaries.size} 处") }
    Text("默认封面单独显示，横图保持完整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
}

internal fun validPairingInput(input: String, count: Int): Int? = input.toIntOrNull()?.minus(1)?.takeIf { it in 0 until (count - 1) }

@Composable
private fun ReaderPairingEditor(tools: ReaderToolsSettings, onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit,
    chapter: String?, page: Int, count: Int, done: () -> Unit) {
    if (chapter.isNullOrBlank() || count < 2) { Text("本章没有可调整的配页"); return }
    var input by remember(chapter, page) { mutableStateOf((page + 1).toString()) }
    val target = validPairingInput(input, count)
    val boundaries = tools.pairingRestarts[chapter].orEmpty()
    val duplicate = target in boundaries
    Text("从指定页重新两两配对，横图保持完整。", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
        label = { Text("起始页") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = target == null,
        supportingText = { if (target == null) Text("请输入 1–${count - 1} 的页码") else if (duplicate) Text("此处已调整") })
    TextButton(enabled = target != null && !duplicate, onClick = {
        val start = target ?: return@TextButton
        onChange { it.copy(pairingRestarts = it.pairingRestarts + (chapter to (it.pairingRestarts[chapter].orEmpty() + start).distinct().sorted())) }
        done()
    }) { Text("应用配页") }
    boundaries.forEach { start ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("从第 ${start + 1} 页起")
            TextButton(onClick = { onChange { current ->
                val remaining = current.pairingRestarts[chapter].orEmpty() - start
                current.copy(pairingRestarts = if (remaining.isEmpty()) current.pairingRestarts - chapter else current.pairingRestarts + (chapter to remaining))
            }; done() }) { Text("移除") }
        }
    }
    TextButton(enabled = boundaries.isNotEmpty(), onClick = { onChange { it.copy(pairingRestarts = it.pairingRestarts - chapter) }; done() }) { Text("恢复默认配页") }
}

@Composable
internal fun ReaderOperationHelp() {
    val navigate = LocalReaderSettingsNavigation.current
    TextButton(onClick = { navigate(ReaderSettingsPage("阅读操作") {
        Text("触控", style = MaterialTheme.typography.titleSmall)
        Text("轻点两侧或滑动翻页；双击缩放，放大后拖动。双指可缩放，复原后点击左右 15% 区域翻页。")
        Text("键盘与鼠标", style = MaterialTheme.typography.titleSmall)
        Text("方向键随阅读方向翻页；PageUp / PageDown 上一组、下一组；空格下一组，Shift＋空格上一组。")
        Text("＋／－缩放，0 复原。放大后方向键、滚轮平移；Ctrl＋滚轮以指针为中心缩放。输入框中不响应阅读快捷键。")
    }) }) { Text("阅读操作") }
}
