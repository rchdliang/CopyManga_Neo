package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings

@Composable
internal fun ReaderSpreadOptions(
    tools: ReaderToolsSettings, onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit,
    chapter: String?, page: Int, count: Int, spreadActive: Boolean, continuous: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Text("页面布局（全局）", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SINGLE" to "单页", "DOUBLE" to "双页", "AUTO" to "自动").forEach { (mode, label) ->
            FilterChip(selected = tools.pageLayout == mode,
                onClick = { onChange { it.copy(pageLayout = mode) } }, label = { Text(label) })
        }
    }
    Text(if (continuous) "纵向连续阅读使用单页；横向阅读应用此选择。"
        else if (spreadActive) "当前：双页 · 封面与完整横图单独显示"
        else "当前：单页" + if (tools.pageLayout != "SINGLE") " · 窄屏或竖屏暂时收起双页" else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ReaderSettingSwitch("翻页动画（全局）", tools.pageAnimation) { value -> onChange { it.copy(pageAnimation = value) } }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起配页与操作说明" else "调整本章配页 · 操作说明") }
    if (expanded) {
        var input by remember(chapter, page) { mutableStateOf((page + 1).toString()) }
        val target = input.toIntOrNull()?.minus(1)
        OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
            label = { Text("从原图第几页开始配对") }, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = chapter != null && target != null && target in 0 until (count - 1), onClick = {
            val key = chapter ?: return@TextButton
            val start = target ?: return@TextButton
            onChange { it.copy(pairingRestarts = it.pairingRestarts + (key to (it.pairingRestarts[key].orEmpty() + start).distinct().sorted())) }
        }) { Text("从此页重新配对") }
        Text("作用于本章此页起的后续页面；可再次添加位置修正广告或缺页。完整横图仍独占一屏。",
            style = MaterialTheme.typography.bodySmall)
        tools.pairingRestarts[chapter].orEmpty().forEach { start ->
            TextButton(onClick = { onChange { current ->
                val remaining = current.pairingRestarts[chapter].orEmpty() - start
                current.copy(pairingRestarts = if (remaining.isEmpty()) current.pairingRestarts - chapter.orEmpty()
                    else current.pairingRestarts + (chapter.orEmpty() to remaining))
            } }) { Text("第 ${start + 1} 页起 · 移除此调整") }
        }
        TextButton(enabled = tools.pairingRestarts[chapter].orEmpty().isNotEmpty(), onClick = {
            onChange { it.copy(pairingRestarts = it.pairingRestarts - chapter.orEmpty()) }
        }) { Text("恢复本章默认配页") }
        Text("""双击放大／复原，双指缩放，放大后拖动平移。复原后滑动或点击左右 15% 区域翻页。
←／→ 跟随阅读方向；PageUp／PageDown 上／下一组；空格下一组，Shift＋空格上一组。
＋／－ 缩放，0 复原；放大时方向键与滚轮平移，Ctrl＋滚轮以指针为中心缩放。输入框中不响应阅读快捷键。""",
            style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
}
