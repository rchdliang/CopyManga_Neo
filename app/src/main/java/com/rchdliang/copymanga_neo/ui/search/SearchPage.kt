package com.rchdliang.copymanga_neo.ui.search

import com.rchdliang.copymanga_neo.ui.components.metadataText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.ui.components.ShareAction

/** 漫画和轻小说共用的搜索页；嵌入小说导航时由外层处理系统边距。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    shareTarget: ShareTarget?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    placeholder: String = "搜索漫画",
    applySystemInsets: Boolean = true,
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit
) {
    val noInsets = WindowInsets(0, 0, 0, 0)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = if (applySystemInsets)
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else noInsets,
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = 1040.dp),
                    windowInsets = if (applySystemInsets) TopAppBarDefaults.windowInsets else noInsets,
                    title = { Text("搜索", color = MaterialTheme.colorScheme.onSurface) },
                    actions = { shareTarget?.let { ShareAction(it) } }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
            ) {
                SearchInput(query, onQueryChange, onSearch, onClear, onBack, placeholder)
                PullToRefreshBox(
                    isRefreshing = isRefreshing, onRefresh = onRefresh,
                    state = pullToRefreshState, modifier = Modifier.weight(1f)
                ) { content() }
            }
        }
    }
}

/** 各内容类型只声明其支持的查询字段，共用按钮形态与单选语义。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchCategorySelector(
    selected: QueryType?,
    onSelected: (QueryType?) -> Unit,
    queryTypes: List<QueryType> = listOf(QueryType.NAME, QueryType.AUTHOR, QueryType.LOCAL)
) {
    val choices = listOf("全部" to null) + queryTypes.map { type ->
        when (type) {
            QueryType.NAME -> "名称"
            QueryType.AUTHOR -> "作者"
            QueryType.LOCAL -> "汉化组"
        } to type
    }
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        choices.forEachIndexed { index, (label, type) ->
            ToggleButton(
                checked = selected == type,
                onCheckedChange = { if (it) onSelected(type) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.RadioButton }
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current
                )
            }
        }
    }
}

/** 结果数量来自服务端总数；发现模式才提供排序。 */
@Composable
internal fun SearchResultsHeader(
    searchMode: Boolean,
    total: Int?,
    bookLabel: String,
    compactLayout: Boolean,
    onLayoutToggle: () -> Unit,
    ordering: String? = null,
    onOrdering: (String) -> Unit = {},
    allowAscending: Boolean = true,
    title: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                metadataText(title ?: if (searchMode) "搜索结果" else "发现"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall
            )
            total?.let {
                Text(
                    "共 $it 部$bookLabel", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (!searchMode && ordering != null) DiscoveryOrdering(ordering, onOrdering, allowAscending)
        SearchToolButton(
            if (compactLayout) "切换为列表布局" else "切换为紧凑布局",
            if (compactLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
            onLayoutToggle
        )
    }
}
