package com.rchdliang.copymanga_neo.ui.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import kotlin.math.roundToInt

@Composable
internal fun NovelSheetHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title, Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭") }
    }
}

/** 分类和分组参考 Book's Story 的设置组织；界面使用本项目的 Material 3 组件。 */
@Composable
internal fun ColumnScope.NovelReaderSettings(
    preferences: NovelReaderPreferences,
    state: NovelUiState,
    onPreferences: (NovelReaderPreferences) -> Unit,
    onCache: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    PrimaryTabRow(selectedTabIndex = tab) {
        listOf("排版", "阅读", "离线").forEachIndexed { index, title ->
            Tab(
                selected = tab == index, onClick = { tab = index },
                text = { Text(title, color = MaterialTheme.colorScheme.onSurface) })
        }
    }
    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (tab == 0) {
            item {
                NovelSettingsGroup("文字") {
                    NovelSettingsSlider(
                        "字号",
                        "${preferences.fontSize.roundToInt()} sp",
                        preferences.fontSize,
                        12f..36f, steps = 23
                    ) {
                        onPreferences(preferences.copy(fontSize = it))
                    }
                    NovelChoices(
                        "字体",
                        preferences.font,
                        listOf("SYSTEM" to "系统", "SERIF" to "衬线", "SANS" to "无衬线")
                    ) {
                        onPreferences(preferences.copy(font = it))
                    }
                    Text(
                        "故事在每一次翻页后继续。找到舒服的字号与行距，慢慢读下去。",
                        style = novelTextStyle(preferences),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                NovelSettingsGroup("间距") {
                    NovelSettingsSlider(
                        "行距",
                        String.format(
                            LocalLocale.current.platformLocale,
                            "%.2f 倍",
                            preferences.lineHeight
                        ),
                        preferences.lineHeight,
                        1.1f..2.4f, steps = 25
                    ) {
                        onPreferences(preferences.copy(lineHeight = it))
                    }
                    NovelSettingsSlider(
                        "段间距",
                        "${preferences.paragraphSpacing.roundToInt()} dp",
                        preferences.paragraphSpacing,
                        0f..24f, steps = 11
                    ) {
                        onPreferences(preferences.copy(paragraphSpacing = it))
                    }
                    NovelSettingsSlider(
                        "左右边距",
                        "${preferences.margin.roundToInt()} dp",
                        preferences.margin,
                        8f..48f, steps = 19
                    ) {
                        onPreferences(preferences.copy(margin = it))
                    }
                }
            }
        } else if (tab == 1) {
            item {
                NovelSettingsGroup("阅读方式") {
                    NovelChoices(
                        "翻阅",
                        preferences.mode,
                        listOf("SCROLL" to "上下滚动", "PAGED" to "左右翻页")
                    ) {
                        onPreferences(preferences.copy(mode = it))
                    }
                    NovelChoices(
                        "分页布局",
                        preferences.columns,
                        listOf("AUTO" to "自动", "SINGLE" to "单页", "DOUBLE" to "双页")
                    ) {
                        onPreferences(preferences.copy(columns = it))
                    }
                    Text(
                        "自动布局在横屏大窗口使用双页；滚动模式始终使用完整阅读区域。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                NovelSettingsGroup("外观") {
                    NovelChoices(
                        "阅读配色", preferences.theme,
                        listOf(
                            "SYSTEM" to "跟随应用",
                            "LIGHT" to "浅色",
                            "DARK" to "深色",
                            "PAPER" to "柔和底色"
                        )
                    ) {
                        onPreferences(preferences.copy(theme = it))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "保持屏幕常亮", Modifier
                                .weight(1f)
                                .padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = preferences.keepScreenOn,
                            onCheckedChange = { onPreferences(preferences.copy(keepScreenOn = it)) })
                    }
                }
            }
        } else {
            item {
                NovelSettingsGroup("本卷缓存") {
                    Text(
                        if (state.reader?.offlineReady == true) "正文与插图已保存，可离线阅读。"
                        else "正文成功打开后会保存；下载本卷可补齐全部插图。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.downloadProgress?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalButton(
                        onClick = onCache,
                        enabled = !state.refreshing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.caching) "暂停下载" else "下载 / 继续本卷",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !state.refreshing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.refreshing) "正在检查…" else "检查官方更新",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onDelete, enabled = !state.refreshing) {
                        Text("删除本卷缓存", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun NovelChoices(
    title: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, name) ->
                FilterChip(
                    selected = value == key, onClick = { onChange(key) },
                    label = { Text(name, color = LocalContentColor.current) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelSettingsSlider(
    title: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            Text(label, color = MaterialTheme.colorScheme.primary)
        }
        val slider = rememberSliderState(value = value, trackRange = range, steps = steps)
        slider.value = value
        Slider(state = slider, onValueChange = { slider.value = it; onChange(it) })
    }
}

@Composable
internal fun ColumnScope.NovelReaderBookmarks(
    bookmarks: List<LocalReadingRecord>,
    volume: NovelReadingVolume,
    onOpen: (LocalReadingRecord) -> Unit,
    onDelete: (LocalReadingRecord) -> Unit
) {
    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        if (bookmarks.isEmpty()) item {
            Text(
                "还没有书签。点击阅读器右上角的书签按钮保存当前位置。",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(
            bookmarks,
            key = { "${it.chapter}:${it.section}:${it.line}:${it.character}:${it.fraction}" }) { bookmark ->
            ListItem(
                content = {
                    Text(
                        bookmark.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = {
                    Text(
                        if (bookmark.chapter == volume.volume.id) {
                            val image =
                                volume.sections.firstOrNull { it.type == 2 && it.key == bookmark.section }
                            val line = volume.document.lines.getOrNull(bookmark.line)
                            image?.name ?: line?.substring(
                                0,
                                volume.document.clampCharacter(bookmark.line, 80)
                            )
                                ?.takeIf { it.isNotBlank() } ?: "点击继续阅读"
                        } else "点击回到这卷继续阅读",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clickable { onOpen(bookmark) },
                trailingContent = {
                    IconButton(onClick = { onDelete(bookmark) }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除书签")
                    }
                }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "小说书签",
    showBackground = true,
    widthDp = 412,
    heightDp = 800
)
@Composable
private fun NovelReaderBookmarksPreview() {
    val volume = previewNovelVolume()
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface {
            Column(Modifier.fillMaxSize()) {
                NovelSheetHeader("本书书签") {}
                NovelReaderBookmarks(
                    listOf(
                        LocalReadingRecord(
                            "preview",
                            "novel",
                            volume.book.path,
                            volume.volume.id,
                            volume.volume.name,
                            line = 2
                        ),
                        LocalReadingRecord(
                            "preview",
                            "novel",
                            volume.book.path,
                            volume.volume.id,
                            volume.volume.name,
                            section = "image-2",
                            line = 4
                        )
                    ), volume, {}, {})
            }
        }
    }
}
