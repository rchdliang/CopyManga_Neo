package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ConfirmRemovalDialog
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onPreferences: (NovelReaderPreferences) -> Unit
) {
    val platform = novelReaderPlatform()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // A restored selection may still point at the removed Offline tab.
    val selectedTab = tab.coerceIn(0, 1)
    PrimaryTabRow(selectedTabIndex = selectedTab) {
        listOf("排版", "阅读").forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index, onClick = { tab = index },
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
        if (selectedTab == 0) {
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
                        platform?.formatLineHeight(preferences.lineHeight) ?: novelLineHeightLabel(preferences.lineHeight),
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
        } else {
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
                        listOf("AUTO" to "自动", "SINGLE" to "单页", "DOUBLE" to "双页"),
                        enabled = preferences.mode == "PAGED"
                    ) {
                        onPreferences(preferences.copy(columns = it))
                    }
                    Text(
                        if (preferences.mode == "PAGED") "自动布局在横屏大窗口使用双页。"
                        else "上下滚动时分页布局不可用，切换为左右翻页后可设置。",
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
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, name) ->
                FilterChip(
                    selected = value == key, onClick = { onChange(key) }, enabled = enabled,
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
        // 拖动时立即更新本地数值，忽略偏好 Flow 回传的中间值。
        var sliderValue by remember(range, steps) { mutableFloatStateOf(value) }
        var isChanging by remember(range, steps) { mutableStateOf(false) }
        LaunchedEffect(value, range, steps) {
            if (!isChanging) sliderValue = value
        }
        val sliderColors = SliderDefaults.colors(
            activeTickColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = sliderValue,
            onValueChange = { isChanging = true; sliderValue = it; onChange(it) },
            onValueChangeFinished = { isChanging = false },
            valueRange = range,
            steps = steps,
            colors = sliderColors
        )
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
                "暂无书签。阅读时点“添加书签”保存位置。",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(
            bookmarks,
            key = { "${it.chapter}:${it.section}:${it.line}:${it.character}:${it.fraction}" }) { bookmark ->
            var confirmDelete by remember(bookmark) { mutableStateOf(false) }
            if (confirmDelete) ConfirmRemovalDialog("删除书签？", "将移除这个阅读位置，阅读历史保留。",
                onConfirm = { confirmDelete = false; onDelete(bookmark) }, onDismiss = { confirmDelete = false })

            ListItem(
                headlineContent = {
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
                    IconButton(onClick = { confirmDelete = true }) {
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
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
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

/** Fixed two-decimal reader setting, independent of JVM platformLocale/String.format. */
internal fun novelLineHeightLabel(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} 倍"
}
