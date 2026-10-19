package com.rchdliang.copymanga_neo.ui.search

import com.rchdliang.copymanga_neo.ui.components.metadataText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ComicFilterDefaults
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/** 全部题材作为独立页面展示；标签检索只过滤本地列表，不请求漫画搜索。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTagsScreen(
    tags: List<ComicFilterOption>,
    selectedPath: String?,
    onSelect: (ComicFilterOption?) -> Unit,
    onBack: () -> Unit,
    bookLabel: String = "漫画",
    isLoading: Boolean = false,
    error: String? = null,
    onRefresh: (() -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)
    val displayNames = tags.associateWith { metadataText(it.name) }
    val visible = tags.filter {
        it.name.contains(query.trim(), ignoreCase = true) ||
                displayNames.getValue(it).contains(query.trim(), ignoreCase = true) ||
                it.pathWord.contains(query.trim(), ignoreCase = true)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("全部题材", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    onRefresh?.let { refresh ->
                        IconButton(onClick = refresh, enabled = !isLoading) {
                            Icon(
                                Icons.Default.Refresh, "刷新题材数量",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let {
                    TextButton(
                        onClick = { onRefresh?.invoke() },
                        enabled = onRefresh != null && !isLoading,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            "题材数量更新失败，保留已有目录，点击重试",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    placeholder = {
                        Text(
                            "查找题材",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Text(
                    "共 ${tags.size} 个题材",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (query.isBlank()) item(key = "all") {
                        TagChoice(ComicFilterOption("全部", ""), selectedPath == null, bookLabel) {
                            onSelect(
                                null
                            )
                        }
                    }
                    items(visible, key = { it.pathWord }) { tag ->
                        TagChoice(tag, tag.pathWord == selectedPath, bookLabel) { onSelect(tag) }
                    }
                    if (visible.isEmpty()) item {
                        Text(
                            "没有匹配的题材", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChoice(
    tag: ComicFilterOption,
    selected: Boolean,
    bookLabel: String,
    onClick: () -> Unit
) {
    val color = when {
        !tag.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    FilterChip(
        selected = selected, enabled = tag.enabled, onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, null, tint = color)
        },
        label = {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    metadataText(tag.name), color = color, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                tag.count?.let {
                    Text(
                        "$it 部$bookLabel",
                        color = color,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "全部题材 - 手机")
@Composable
private fun AllTagsPreview() {
    CopyManga_NeoTheme { AllTagsScreen(ComicFilterDefaults.themes, "qihuan", {}, {}) }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 800, name = "全部题材 - 平板")
@Composable
private fun AllTagsWidePreview() {
    CopyManga_NeoTheme { AllTagsScreen(ComicFilterDefaults.themes, "qihuan", {}, {}) }
}
