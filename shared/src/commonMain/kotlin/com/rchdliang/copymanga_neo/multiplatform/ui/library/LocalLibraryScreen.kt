package com.rchdliang.copymanga_neo.multiplatform.ui.library

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.domain.model.safeBookTitle
import com.rchdliang.copymanga_neo.multiplatform.ui.components.formattedReadingDate

@Composable
fun LocalLibraryScreen(
    onBack: () -> Unit, onCloud: () -> Unit,
    onComic: (String, String, String, String, Int, Double) -> Unit,
    onNovel: (LocalReadingRecord) -> Unit, viewModel: LocalLibraryViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LocalLibraryContent(
        state, onBack, onCloud,
        onOpen = { r ->
            if (r.kind == "comic") onComic(
                r.path,
                r.chapter,
                r.name,
                r.group,
                r.page,
                r.fraction
            ) else onNovel(r)
        },
        onRemove = viewModel::remove,
        onClear = viewModel::clear, onRetry = viewModel::retry
    )
}

/** 无仓库依赖的本地书库，供手机、平板及深色预览共用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLibraryContent(
    state: LocalLibraryState,
    onBack: () -> Unit = {},
    onCloud: () -> Unit = {},
    onOpen: (LocalReadingRecord) -> Unit = {},
    onRemove: (LocalReadingRecord, Boolean) -> Unit = { _, _ -> },
    onClear: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var marks by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    val records =
        (if (marks) state.bookmarks else state.history).filter { kind.isEmpty() || it.kind == kind }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("本地书库", color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "返回"
                    )
                }
            },
            actions = { IconButton(onClick = onCloud) { Icon(Icons.Outlined.Cloud, "云端历史") } })
    }) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    if (state.owner == "guest") "游客书库 · 保存在本机" else if (state.owner == null) "正在读取本地书库…" else "当前账号的本地书库",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryTabRow(selectedTabIndex = if (marks) 1 else 0) {
                    listOf(
                        "阅读历史" to state.history.size,
                        "我的书签" to state.bookmarks.size
                    ).forEachIndexed { index, entry ->
                        Tab(
                            selected = marks == (index == 1), onClick = { marks = index == 1 },
                            text = {
                                Text(
                                    "${entry.first}  ${entry.second}",
                                    color = if (marks == (index == 1)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "" to "全部",
                        "comic" to "漫画",
                        "novel" to "小说"
                    ).forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { kind = value }, label = {
                            Text(
                                label,
                                color = if (kind == value) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        })
                    }
                    Spacer(Modifier.weight(1f))
                    if (!marks) IconButton(
                        onClick = { clear = true },
                        enabled = state.history.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, "清空当前账号本地历史")
                    }
                }
                state.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRetry) {
                                Text(
                                    "重试读取",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                if (state.owner == null && state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (records.isEmpty() && state.owner != null && state.error == null) item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                if (marks) Icons.Outlined.Bookmarks else Icons.Outlined.History,
                                null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (marks) "把喜欢的地方留在这里" else "从下一次阅读开始",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (marks) "阅读时添加书签，就能直接回到那一页。" else "漫画和小说的阅读位置会自动保存在本机。",
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(
                        records,
                        key = { if (marks) "${it.key}:${it.section}:${it.line}:${it.character}:${it.page}" else it.bookKey }) { record ->
                        ReadingRecordCard(
                            record,
                            marks,
                            { onOpen(record) },
                            { onRemove(record, marks) })
                    }
                }
            }
        }
    }
    if (clear) if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { clear = false },
        icon = { Icon(Icons.Outlined.DeleteSweep, null) },
        title = { Text("清空本地历史？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "清除当前账号的本地位置和完成状态，并屏蔽旧章节续读回退。书签、云端历史和缓存保留；新阅读会重新保存。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = { clear = false; onClear() }) {
                Text(
                    "清空",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { clear = false }) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        })
}

@Composable
private fun ReadingRecordCard(
    record: LocalReadingRecord,
    bookmark: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val reading = !record.completed
    com.rchdliang.copymanga_neo.multiplatform.ui.components.ReadingHistoryRow(
        title = displayBookTitle(record.path, record.title),
        cover = record.cover, authors = record.authors, onClick = onOpen,
        containerColor = if (reading) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.DeleteOutline, if (bookmark) "删除书签" else "删除阅读历史",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Text(record.name.ifBlank { "章节信息暂缺" } + if (record.kind == "comic") " · 第 ${record.page + 1} 页" else " · 第 ${record.line + 1} 行",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (if (reading) "阅读中" else "已读完") + " · " +
                    formattedReadingDate(record.updatedAt),
            style = MaterialTheme.typography.labelMedium,
            color = if (reading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (safeBookTitle(record.path, record.title).isBlank()) {
            Text(
                "本机没有作品信息，联网阅读后保存", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "本地书库 · 手机", showBackground = true, widthDp = 412, heightDp = 860)
@Preview(
    name = "本地书库 · 平板深色",
    showBackground = true,
    widthDp = 840,
    heightDp = 900,
    uiMode = 0x20
)
@Composable
private fun LocalLibraryPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        LocalLibraryContent(
            LocalLibraryState(
                owner = "guest", history = listOf(
                    LocalReadingRecord(
                        "guest",
                        "comic",
                        "sample-comic",
                        "chapter-3",
                        "第三话",
                        title = "葬送的芙莉莲",
                        cover = "preview://comic-cover", authors = "山田钟人 / 阿部司",
                        page = 12
                    ),
                    LocalReadingRecord(
                        "guest",
                        "novel",
                        "sample-novel",
                        "volume-1",
                        "第一卷",
                        title = "狼与香辛料",
                        cover = "preview://novel-cover", authors = "支仓冻砂",
                        line = 248,
                        completed = true
                    )
                )
            )
        )
    }
}

@Preview(name = "书库 · 空状态", showBackground = true)
@Composable
private fun EmptyLibraryPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        LocalLibraryContent(
            LocalLibraryState(owner = "guest")
        )
    }
}

@Preview(name = "书库 · 加载", showBackground = true)
@Composable
private fun LoadingLibraryPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        LocalLibraryContent(LocalLibraryState())
    }
}
