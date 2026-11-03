package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    ReaderInlineChoice(Icons.Outlined.ViewWeek, "页面布局", pageLayout, listOf("SINGLE", "DOUBLE", "AUTO"),
        { when(it) { "DOUBLE" -> "双页"; "AUTO" -> "自动"; else -> "单页" } }, onSelect = onPageLayoutChange)
}

@Composable
internal fun ReaderPairingOption(tools: ReaderToolsSettings, onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit,
    chapter: String?, page: Int, count: Int) {
    val navigate = LocalReaderSettingsNavigation.current
    val currentTools by rememberUpdatedState(tools)
    ReaderSettingsLink(Icons.Outlined.ViewWeek, "本章配页", index = 0, count = 2, enabled = !chapter.isNullOrBlank()) {
        navigate(ReaderSettingsPage("本章配页") { ReaderPairingEditor(currentTools, onChange, chapter, page, count) { navigate(null) } })
    }
}

internal fun validPairingInput(input: String, count: Int): Int? = input.toIntOrNull()?.minus(1)?.takeIf { it in 0 until (count - 1) }

@Composable
internal fun ReaderPairingEditor(
    tools: ReaderToolsSettings,
    onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit,
    chapter: String?,
    page: Int,
    count: Int,
    done: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    if (chapter.isNullOrBlank() || count < 2) {
        Text("本章没有可调整的配页", color = colors.onSurfaceVariant)
        return
    }

    var input by remember(chapter, page) { mutableStateOf((page + 1).toString()) }
    val target = validPairingInput(input, count)
    val boundaries = tools.pairingRestarts[chapter].orEmpty()
    val duplicate = target in boundaries
    val currentPage = input.toIntOrNull()
    val canStepBack = currentPage != null && currentPage in 2..count
    val canStepForward = target != null && target < count - 2

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.secondaryContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ViewWeek, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "当前原图 ${page + 1} / $count",
                    style = typography.titleMedium,
                    color = colors.onSecondaryContainer
                )
                Text(
                    "从指定原图开始重新两两配对。横图保持单页，调整仅对本章生效。",
                    style = typography.bodySmall,
                    color = colors.onSecondaryContainer
                )
            }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("新增起始页", style = typography.titleMedium, color = colors.onSurface)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("起始页") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = target == null
                )
                IconButton(
                    enabled = canStepBack,
                    onClick = { currentPage?.let { input = (it - 1).toString() } }
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "起始页减一")
                }
                IconButton(
                    enabled = canStepForward,
                    onClick = { currentPage?.let { input = (it + 1).toString() } }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "起始页加一")
                }
            }
            Text(
                text = when {
                    target == null -> "请输入 1–${count - 1} 的页码"
                    duplicate -> "此处已调整"
                    else -> "可用原图页码 1–${count - 1}"
                },
                style = typography.bodySmall,
                color = if (target == null) colors.error else colors.onSurfaceVariant
            )
            Button(
                enabled = target != null && !duplicate,
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    val start = target ?: return@Button
                    onChange { current ->
                        val updated = (current.pairingRestarts[chapter].orEmpty() + start).distinct().sorted()
                        current.copy(pairingRestarts = current.pairingRestarts + (chapter to updated))
                    }
                    done()
                }
            ) {
                Text("应用配页")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (boundaries.isEmpty()) "已保存的调整" else "已保存的调整 · ${boundaries.size}",
                style = typography.titleMedium,
                color = colors.onSurface
            )
            Spacer(Modifier.height(8.dp))
            if (boundaries.isEmpty()) {
                Text("本章使用默认配页", style = typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                boundaries.forEach { start ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "从第 ${start + 1} 页起",
                            modifier = Modifier.weight(1f),
                            style = typography.bodyMedium,
                            color = colors.onSurface
                        )
                        TextButton(onClick = {
                            onChange { current ->
                                val remaining = current.pairingRestarts[chapter].orEmpty() - start
                                val restarts = if (remaining.isEmpty()) {
                                    current.pairingRestarts - chapter
                                } else {
                                    current.pairingRestarts + (chapter to remaining)
                                }
                                current.copy(pairingRestarts = restarts)
                            }
                            done()
                        }) {
                            Text("移除")
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = {
                        onChange { it.copy(pairingRestarts = it.pairingRestarts - chapter) }
                        done()
                    }
                ) {
                    Text("恢复默认配页")
                }
            }
        }
    }
}

@Composable
internal fun ReaderOperationHelp() {
    val navigate = LocalReaderSettingsNavigation.current
    ReaderSettingsLink(Icons.AutoMirrored.Outlined.HelpOutline, "操作说明", index = 1, count = 2, onClick = { navigate(ReaderSettingsPage("操作说明") {
        Text("触控", style = MaterialTheme.typography.titleSmall)
        Text("轻点两侧或滑动翻页；双击缩放，放大后拖动。双指可缩放，复原后点击左右 15% 区域翻页。")
        Text("键盘与鼠标", style = MaterialTheme.typography.titleSmall)
        Text("方向键随阅读方向翻页；PageUp / PageDown 上一组、下一组；空格下一组，Shift＋空格上一组。")
        Text("＋／－缩放，0 复原。放大后方向键、滚轮平移；Ctrl＋滚轮以指针为中心缩放。输入框中不响应阅读快捷键。")
    }) })
}
