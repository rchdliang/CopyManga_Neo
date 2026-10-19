package com.rchdliang.copymanga_neo.multiplatform.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect

@Composable
fun OriginalWorkScreen(
    viewModel: OriginalWorkViewModel,
    onComic: (String) -> Unit,
    onBack: () -> Unit
) {
    ActiveRouteLaunchedEffect(viewModel) { viewModel.start() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OriginalWorkContent(viewModel.title, viewModel.path, state, onComic, onBack,
        viewModel::refresh, viewModel::loadMore, viewModel::toggleLayout, viewModel::selectOrdering)
}

/** 标题即原作名，复用结果网格，不包含搜索输入、字段选择或分享入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OriginalWorkContent(
    title: String, path: String, state: SearchUiState,
    onComic: (String) -> Unit, onBack: () -> Unit,
    onRefresh: () -> Unit, onMore: () -> Unit,
    onLayout: () -> Unit, onOrdering: (String) -> Unit
) {
    Surface(Modifier.fillMaxSize().windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        color = MaterialTheme.colorScheme.surface) {
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
            ComicResultsGrid(state, onComic, onMore, onRefresh,
                criteriaKey = path, transitionGroup = "original:$path", enableExpansion = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(Modifier.weight(1f)) {
                        SearchResultsHeader(false, state.total, "漫画", state.compactLayout,
                            onLayout, ordering = state.ordering, onOrdering = onOrdering, title = title)
                    }
                }
            }
        }
    }
}
