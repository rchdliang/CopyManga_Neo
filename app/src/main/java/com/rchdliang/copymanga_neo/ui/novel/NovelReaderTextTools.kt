package com.rchdliang.copymanga_neo.ui.novel

import com.rchdliang.copymanga_neo.ui.components.UnsavedChangesDialog
import com.rchdliang.copymanga_neo.ui.components.ConfirmRemovalDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import com.rchdliang.copymanga_neo.domain.model.NovelTextSelection
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.domain.reading.textIn

private const val NOVEL_SOURCE = "novel-source"

/** 每个 UTF-16 字符携带原始坐标，选中字串后仍能区分同一段里的重复文字。 */
internal fun novelSourceText(text: String, line: Int, character: Int = 0): AnnotatedString {
    val builder = AnnotatedString.Builder(text.ifEmpty { "\u00a0" })
    text.indices.forEach { at ->
        builder.addStringAnnotation(
            NOVEL_SOURCE,
            "$line:${character + at}",
            at,
            at + 1
        )
    }
    return builder.toAnnotatedString()
}

internal fun novelSelectedSource(
    parts: List<AnnotatedString>,
    document: NovelTextDocument
): NovelTextSelection? {
    val coordinates = parts.flatMap { it.getStringAnnotations(NOVEL_SOURCE, 0, it.length) }
    fun decode(item: String): Pair<Int, Int>? {
        val split = item.split(':')
        return if (split.size == 2) {
            val line = split[0].toIntOrNull() ?: return null
            val character = split[1].toIntOrNull() ?: return null
            line to character
        } else null
    }

    val start = coordinates.firstOrNull()?.item?.let(::decode) ?: return null
    val end = coordinates.lastOrNull()?.item?.let(::decode) ?: return null
    val range = NovelTextRange(start.first, start.second, end.first, end.second + 1)
    val text = document.textIn(range)?.takeIf { it.isNotBlank() } ?: return null
    return NovelTextSelection(text, range)
}

internal fun novelRangeSegment(
    range: NovelTextRange,
    line: Int,
    character: Int,
    length: Int
): Pair<Int, Int>? {
    if (line !in range.startLine..range.endLine) return null
    val start = maxOf(character, if (line == range.startLine) range.startCharacter else 0)
    val end = minOf(
        character + length,
        if (line == range.endLine) range.endCharacter else character + length
    )
    return if (start < end) start - character to end - character else null
}

@Composable
private fun annotationColors(style: String): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (style) {
        "SECONDARY" -> colors.secondaryContainer to colors.onSecondaryContainer
        "TERTIARY" -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
}

@Composable
internal fun NovelReaderText(
    text: String, line: Int, character: Int = 0,
    preferences: NovelReaderPreferences,
    annotations: List<NovelAnnotation>,
    searchRange: NovelTextRange?,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val original = remember(text, line, character) { novelSourceText(text, line, character) }
    val colors = MaterialTheme.colorScheme
    val value = remember(original, annotations, searchRange, colors) {
        val builder = AnnotatedString.Builder(original)
        annotations.sortedBy { it.updatedAt }.forEach { annotation ->
            novelRangeSegment(annotation.range, line, character, text.length)?.let { (start, end) ->
                val (background, foreground) = when (annotation.style) {
                    "SECONDARY" -> colors.secondaryContainer to colors.onSecondaryContainer
                    "TERTIARY" -> colors.tertiaryContainer to colors.onTertiaryContainer
                    else -> colors.primaryContainer to colors.onPrimaryContainer
                }
                builder.addStyle(SpanStyle(background = background, color = foreground), start, end)
            }
        }
        searchRange?.let { range ->
            novelRangeSegment(range, line, character, text.length)?.let { (start, end) ->
                builder.addStyle(
                    SpanStyle(background = colors.tertiary, color = colors.onTertiary),
                    start,
                    end
                )
            }
        }
        builder.toAnnotatedString()
    }
    Text(
        value, modifier = Modifier.fillMaxWidth(), style = novelTextStyle(preferences),
        color = colors.onSurface, onTextLayout = onTextLayout
    )
}

@Composable
internal fun NovelAnnotationEditor(
    selected: NovelTextSelection, existing: NovelAnnotation?,
    onSave: (String, String) -> Unit, onDismiss: () -> Unit,
    saving: Boolean = false, saveFailed: Boolean = false
) {
    var style by rememberSaveable { mutableStateOf(existing?.style ?: "PRIMARY") }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val changed = note != existing?.note.orEmpty() || style != (existing?.style ?: "PRIMARY")
    val requestDismiss = { if (!saving) { if (changed) confirmDiscard = true else onDismiss() } }
    if (confirmDiscard) UnsavedChangesDialog(
        busy = saving, onContinue = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onDismiss() }
    )
    AlertDialog(
        onDismissRequest = requestDismiss,
        title = {
            Text(
                if (existing == null) "添加标注" else "编辑标注",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        selected.text,
                        Modifier.padding(12.dp),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("高亮", color = MaterialTheme.colorScheme.onSurface)
                    listOf("PRIMARY", "SECONDARY", "TERTIARY").forEachIndexed { index, key ->
                        val (container, foreground) = annotationColors(key)
                        FilledTonalIconButton(
                            onClick = { style = key },
                            modifier = Modifier.size(48.dp),
                            enabled = !saving,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = container,
                                contentColor = foreground
                            )
                        ) {
                            Icon(
                                if (style == key) Icons.Outlined.Check else Icons.Outlined.Circle,
                                "标注颜色 ${index + 1}"
                            )
                        }
                    }
                }
                OutlinedTextField(
                    note,
                    { note = it },
                    Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !saving,
                    label = {
                        Text(
                            "笔记（可选）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    })
                if (saveFailed) Text(
                    "保存失败，内容已保留，请重试。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(style, note) }, enabled = !saving) {
                Text(
                    if (saving) "保存中…" else "保存",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = requestDismiss, enabled = !saving) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
internal fun ColumnScope.NovelReaderAnnotations(
    annotations: List<NovelAnnotation>,
    onOpen: (NovelAnnotation) -> Unit,
    onEdit: (NovelAnnotation) -> Unit,
    onDelete: (NovelAnnotation) -> Unit
) {
    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        if (annotations.isEmpty()) item {
            Text(
                "还没有标注。长按正文选中文字，然后点击“标注”。", Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(annotations, key = { _, annotation -> annotation.id }) { _, annotation ->
            var confirmDelete by remember(annotation.id) { mutableStateOf(false) }
            if (confirmDelete) ConfirmRemovalDialog("删除标注？", "标注及笔记将被删除，无法撤销。",
                onConfirm = { confirmDelete = false; onDelete(annotation) }, onDismiss = { confirmDelete = false })
            val (color, foreground) = annotationColors(annotation.style)
            ListItem(
                content = {
                    Text(
                        annotation.text.replace('\n', ' '),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = {
                    Text(
                        annotation.volumeName + if (annotation.note.isNotBlank()) "\n" + annotation.note else "",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Surface(color = color, shape = MaterialTheme.shapes.small) {
                        Icon(
                            Icons.Outlined.FormatColorFill,
                            null,
                            Modifier
                                .padding(6.dp)
                                .size(20.dp),
                            tint = foreground
                        )
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onEdit(annotation) }) {
                            Icon(
                                Icons.Outlined.EditNote,
                                "编辑标注"
                            )
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                "删除标注"
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clickable { onOpen(annotation) }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "小说标注列表",
    showBackground = true,
    widthDp = 412,
    heightDp = 700
)
@androidx.compose.ui.tooling.preview.Preview(
    name = "小说标注列表 · 深色", showBackground = true, widthDp = 412, heightDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun NovelAnnotationsPreview() {
    val sample = NovelAnnotation(
        "preview", "preview", "WEB", "book", "volume", "第一卷 · 启程",
        "这里有许多值得慢慢发现的风景。", NovelTextRange(3, 0, 3, 15), note = "喜欢这句话。"
    )
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxSize()) {
                NovelSheetHeader("标注") {}
                NovelReaderAnnotations(listOf(sample), {}, {}, {})
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "小说标注编辑",
    showBackground = true,
    widthDp = 412,
    heightDp = 700
)
@Composable
private fun NovelAnnotationEditorPreview() {
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelAnnotationEditor(
            NovelTextSelection("这里有许多值得慢慢发现的风景。", NovelTextRange(3, 0, 3, 15)),
            null, { _, _ -> }, {}
        )
    }
}
