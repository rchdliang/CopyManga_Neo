package com.rchdliang.copymanga_neo.multiplatform.ui.history

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryItem
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onComicClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onHistoryCleared: () -> Unit,
    viewModel: HistoryViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toastHost = LocalToastHost.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    ActiveRouteLaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }
    ActiveRouteLaunchedEffect(state.clearCompleted) {
        if (state.clearCompleted) {
            onHistoryCleared()
            viewModel.consumeClearCompleted()
        }
    }
    HistoryContent(
        uiState = state,
        onNavigateBack = onNavigateBack,
        onComicClick = onComicClick,
        onLoginClick = onLoginClick,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRequestClear = viewModel::requestClear,
        onDismissClear = viewModel::dismissClearConfirmation,
        onConfirmClear = viewModel::confirmClear
    )
}

/** 浏览记录纯 UI，云端删除只由确认按钮触发。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryContent(
    uiState: HistoryUiState,
    onNavigateBack: () -> Unit,
    onComicClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRequestClear: () -> Unit,
    onDismissClear: () -> Unit,
    onConfirmClear: () -> Unit
) {
    com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicPreloadEffect(uiState.items.map { it.pathWord })
    val pullState = rememberPullToRefreshState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("浏览记录", color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                TextButton(
                    onClick = onRequestClear,
                    enabled = uiState.isLoggedIn && uiState.items.isNotEmpty() && !uiState.isClearing
                ) {
                    Text(
                        if (uiState.isClearing) "清空中…" else "清空全部",
                        color = if (uiState.isLoggedIn && uiState.items.isNotEmpty() && !uiState.isClearing)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            // 主界面已处理状态栏边距。
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                if (uiState.isLoggedIn && !uiState.isClearing && !uiState.isRefreshing) onRefresh()
            },
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            when {
                uiState.isCheckingLogin || (uiState.isRefreshing && uiState.items.isEmpty()) -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }

                !uiState.isLoggedIn -> HistoryMessage(
                    "登录后查看云端浏览记录",
                    "前往登录",
                    onLoginClick
                )

                uiState.error != null && uiState.items.isEmpty() -> {
                    HistoryMessage(uiState.error, "重试", onRefresh)
                }

                uiState.items.isEmpty() -> HistoryMessage("暂无云端漫画浏览记录")
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 840.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            HistoryRow(
                                item,
                                enabled = !uiState.isClearing,
                                onClick = { onComicClick(item.pathWord) })
                        }
                        if (uiState.nextOffset != null) {
                            item(key = "load-more") {
                                if (uiState.loadMoreError == null && !uiState.isClearing) {
                                    ActiveRouteLaunchedEffect(uiState.nextOffset) { onLoadMore() }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoadingMore) {
                                        LoadingIndicator()
                                    } else if (uiState.loadMoreError != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                uiState.loadMoreError,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            TextButton(
                                                onClick = onLoadMore,
                                                enabled = !uiState.isClearing
                                            ) {
                                                Text(
                                                    "重试加载更多",
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (uiState.showClearConfirmation && uiState.isLoggedIn) {
        if (LocalRouteActive.current) AlertDialog(
            onDismissRequest = onDismissClear,
            title = { Text("清空浏览记录？", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "将清空当前账号的全部云端漫画浏览记录，此操作无法撤销。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmClear, enabled = !uiState.isClearing) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@Composable
private fun HistoryMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) TextButton(onClick = onAction) {
                Text(
                    action,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: BrowseHistoryItem, enabled: Boolean, onClick: () -> Unit) {
    com.rchdliang.copymanga_neo.multiplatform.ui.components.ReadingHistoryRow(
        comicPathWord = item.pathWord,
        title = item.name,
        cover = item.cover,
        authors = item.authors.joinToString(" / ") { it.name },
        enabled = enabled,
        onClick = onClick
    ) {
        Text("上次读到：${item.lastReadChapterName?.takeIf { it.isNotBlank() } ?: "暂无章节记录"}",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        item.latestChapterName?.takeIf { it.isNotBlank() }?.let {
            Text(
                "最新章节：$it", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, name = "浏览记录 - 手机")
@Preview(showBackground = true, widthDp = 840, heightDp = 700, name = "浏览记录 - 平板")
@Composable
private fun HistoryPreview() {
    CopyManga_NeoTheme {
        HistoryContent(
            uiState = HistoryUiState(
                isCheckingLogin = false, isLoggedIn = true, items = listOf(
                    BrowseHistoryItem(
                        1, "示例漫画", "sample", null, listOf(Author("示例作者", null)),
                        "chapter-1", "第01话", "chapter-68", "第68话"
                    )
                )
            ),
            onNavigateBack = {},
            onComicClick = {},
            onLoginClick = {},
            onRefresh = {},
            onLoadMore = {},
            onRequestClear = {},
            onDismissClear = {},
            onConfirmClear = {}
        )
    }
}
