package com.rchdliang.copymanga_neo.ui.novel

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import com.rchdliang.copymanga_neo.domain.reading.NovelBookMatch
import com.rchdliang.copymanga_neo.domain.reading.NovelBookSearchProgress
import com.rchdliang.copymanga_neo.domain.reading.NovelSearchScope
import com.rchdliang.copymanga_neo.domain.reading.NovelTextMatch

/** 搜索是覆盖在正文上的独立面板，IME 和结果数量不会改变阅读区尺寸。 */
@Composable
internal fun NovelReaderSearchOverlay(
    state: NovelReaderSearchUiState,
    onSearch: (String, NovelSearchScope) -> Unit,
    onCancel: () -> Unit,
    onSelect: (NovelBookMatch) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .union(WindowInsets.ime)
                )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f))
                    .clickable(onClick = onClose)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .height((maxHeight * .84f).coerceAtMost(640.dp)),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                NovelReaderSearchPanel(state, onSearch, onCancel, onSelect, onClose)
            }
        }
    }
}

@Composable
internal fun NovelReaderSearchPanel(
    state: NovelReaderSearchUiState,
    onSearch: (String, NovelSearchScope) -> Unit,
    onCancel: () -> Unit,
    onSelect: (NovelBookMatch) -> Unit,
    onClose: () -> Unit
) {
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var scope by rememberSaveable(state.scope) { mutableStateOf(state.scope) }
    var scopeMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val dirty = query.trim() != state.query || scope != state.scope
    fun submit() {
        query = query.trim()
        keyboard?.hide()
        onSearch(query, scope)
    }

    val groups =
        remember(state.progress.matches) { state.progress.matches.groupBy { it.paragraphKey }.values.toList() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "全文搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭搜索") }
        }
        OutlinedTextField(
            query, { query = it; if (state.running) onCancel() },
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.large, maxLines = 2,
            placeholder = { Text("查找正文", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton(onClick = {
                        query = ""; onSearch(
                        "",
                        scope
                    )
                    }) {
                        Icon(Icons.Outlined.Close, "清空搜索", Modifier.size(18.dp))
                    }
                    IconButton(onClick = ::submit, enabled = query.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "开始搜索")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() })
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "搜索范围", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                TextButton(onClick = { scopeMenu = true }) {
                    Text(
                        if (scope == NovelSearchScope.BOOK) "全书" else "本卷",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(20.dp))
                }
                DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                    listOf(
                        NovelSearchScope.VOLUME to "本卷",
                        NovelSearchScope.BOOK to "全书"
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { RadioButton(selected = scope == key, onClick = null) },
                            onClick = { scopeMenu = false; scope = key; submit() }
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = when {
                dirty -> "点击右侧按钮开始新的搜索"
                state.query.isBlank() -> "输入文字，或从长按菜单搜索"
                state.running && state.progress.totalVolumes == 0 -> "正在获取分卷目录…"
                state.progress.totalHits == 0 && !state.running && state.progress.complete -> "没有匹配的文字"
                else -> state.progress.paragraphs.toString() + " 段 · " + state.progress.totalHits + " 处"
            }
            Text(
                text, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.running) TextButton(onClick = onCancel) {
                Text("停止", color = MaterialTheme.colorScheme.primary)
            } else if (!dirty && state.query.isNotBlank() && !state.progress.complete) TextButton(
                onClick = ::submit
            ) {
                Text(
                    if (state.progress.error == null) "继续" else "重试",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (!dirty && state.scope == NovelSearchScope.BOOK && state.progress.totalVolumes > 0) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                if (state.running) LinearProgressIndicator(
                    progress = { state.progress.completedVolumes.toFloat() / state.progress.totalVolumes },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (state.running) "已检索 " + state.progress.completedVolumes + " / " + state.progress.totalVolumes +
                            " 卷 · " + state.progress.currentVolume
                    else "已检索 " + state.progress.completedVolumes + " / " + state.progress.totalVolumes + " 卷",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        } else if (!dirty && state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!dirty) state.progress.error?.let { error ->
            Text(
                error, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
        }
        if (!dirty && state.progress.totalHits > state.progress.matches.size) {
            Text(
                "显示前 " + state.progress.matches.size + " 处，可缩小关键词继续查找。",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!dirty) itemsIndexed(
                groups,
                key = { _, group -> group.first().paragraphKey }) { index, group ->
                val hit = group.firstOrNull { it.key == state.selected?.key } ?: group.first()
                val active =
                    group.any { it.key == state.selected?.key } || state.selected == null && index == 0
                val colors = MaterialTheme.colorScheme
                val excerpt = remember(hit, colors) {
                    AnnotatedString.Builder(hit.match.excerpt).apply {
                        val start = hit.match.matchStart.coerceIn(0, length)
                        val end = hit.match.matchEnd.coerceIn(start, length)
                        if (start < end) addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            ), start, end
                        )
                    }.toAnnotatedString()
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (active) colors.secondaryContainer else colors.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keyboard?.hide(); onSelect(hit) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            (index + 1).toString(),
                            Modifier
                                .width(24.dp)
                                .padding(top = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (hit.chapterName.isBlank() || hit.chapterName == hit.volumeName) hit.volumeName
                                else hit.volumeName + " · " + hit.chapterName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface
                            )
                            Text(
                                excerpt,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (group.size > 1) Text(
                            group.size.toString() + "处", Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = colors.primary
                        )
                        else Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelReaderSearchNavigation(
    state: NovelReaderSearchUiState, onMove: (Int) -> Unit, onOpen: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val index = state.progress.matches.indexOfFirst { it.key == state.selected?.key }
    val more = !state.progress.complete || state.progress.totalHits > state.progress.matches.size
    Surface(
        modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMove(-1) },
                enabled = index > 0
            ) { Icon(Icons.Outlined.ChevronLeft, "上一处匹配") }
            TextButton(onClick = onOpen) {
                Text(
                    (index + 1).coerceAtLeast(1).toString() + " / " + state.progress.matches.size +
                            if (more) "+" else "", color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { onMove(1) },
                enabled = index >= 0 && index < state.progress.matches.lastIndex
            ) {
                Icon(Icons.Outlined.ChevronRight, "下一处匹配")
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "结束搜索") }
        }
    }
}

@Preview(name = "全文搜索 · 手机", showBackground = true, widthDp = 412, heightDp = 620)
@Preview(name = "全文搜索 · 平板", showBackground = true, widthDp = 640, heightDp = 620)
@Preview(
    name = "全文搜索 · 深色",
    showBackground = true,
    widthDp = 412,
    heightDp = 620,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun NovelSearchPreview() {
    val hits = listOf(
        NovelBookMatch(
            "one", "第一卷", "第二章 · 旅途",
            NovelTextMatch(
                NovelTextRange(2, 1, 2, 3),
                "街边的店铺刚刚打开，下一段旅途已经在等待。",
                13,
                15
            )
        ),
        NovelBookMatch(
            "two", "第二卷", "第三章 · 新的城市",
            NovelTextMatch(
                NovelTextRange(4, 0, 4, 2),
                "沿着道路，她继续这场旅途。桥对岸传来钟声。",
                10,
                12
            )
        )
    )
    val state = NovelReaderSearchUiState(
        query = "旅途", scope = NovelSearchScope.BOOK,
        visible = true, progress = NovelBookSearchProgress(hits, 2, 2, 2, 2, complete = true)
    )
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            NovelReaderSearchPanel(state, { _, _ -> }, {}, {}, {})
        }
    }
}
