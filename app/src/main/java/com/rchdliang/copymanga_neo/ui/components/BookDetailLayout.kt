package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ShareTarget

/** 详情页共享背景与容器转场，业务层只提供转场标识。 */
@Composable
fun BookDetailSurface(
    transitionKey: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .comicContainerSharedBounds(transitionKey, hideOnExit = true)
                .matchParentSize()
        ) {}
        content()
    }
}

/** 漫画与小说共用下拉刷新反馈。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookDetailRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = onRefresh, state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

/** 以漫画详情为基准的页面骨架：紧凑顶栏、单一网格和随滚动收展的阅读入口。 */
@Composable
fun BookDetailLayout(
    bookKey: String,
    title: String,
    onBack: () -> Unit,
    readingLabel: String,
    readingTargetName: String?,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    headerKey: String = "book_header",
    topBar: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomPaddingOverride: Dp? = null,
    header: @Composable (Modifier) -> Unit,
    catalogue: LazyGridScope.() -> Unit
) {
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var titleHeight by remember(bookKey) { mutableIntStateOf(1) }
    var expandReadingButton by rememberSaveable(bookKey) { mutableStateOf(true) }
    val hasReadingTarget = !readingTargetName.isNullOrBlank()
    val showAppBarTitle by remember(gridState, headerKey) {
        derivedStateOf {
            val headerItem =
                gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == headerKey }
            if (headerItem == null) gridState.firstVisibleItemIndex > 0
            else -headerItem.offset.y >= titleHeight.coerceAtLeast(1)
        }
    }
    val atTop by remember(gridState) { derivedStateOf { !gridState.canScrollBackward } }
    val scrollDirection = remember(density, bookKey) {
        object : NestedScrollConnection {
            var distance = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (distance * available.y < 0f) distance = 0f
                    distance += available.y
                    if (kotlin.math.abs(distance) >= with(density) { 8.dp.toPx() }) {
                        expandReadingButton = distance > 0f
                        distance = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    val bottomPadding = bottomPaddingOverride
        ?: if (hasReadingTarget) 104.dp + navigationBottom else 16.dp + navigationBottom
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (atTop) 0.dp else 3.dp
            ) {
                if (topBar != null) topBar() else Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        androidx.compose.animation.AnimatedVisibility(
                            showAppBarTitle,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    actions()
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(90.dp), state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(scrollDirection)
            ) {
                item(
                    key = headerKey,
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "book_header"
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        header(Modifier.onSizeChanged {
                            titleHeight = it.height
                        })
                    }
                }
                catalogue()
            }
        }
        if (bottomBar != null) bottomBar() else if (hasReadingTarget) {
            val expanded = atTop || expandReadingButton
            ExtendedFloatingActionButton(
                onClick = onRead, expanded = expanded,
                icon = { Icon(Icons.Default.PlayArrow, if (expanded) null else readingLabel) },
                text = {
                    Text(
                        readingTargetName.orEmpty(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 280.dp)
            )
        }
    }
}

@Composable
fun RowScope.BookDetailToolbar(
    target: ShareTarget,
    onComments: (() -> Unit)?,
    commentsEnabled: Boolean = true,
    onDownload: (() -> Unit)? = null
) {
    ShareAction(target)
    if (onComments != null) IconButton(onClick = onComments, enabled = commentsEnabled) {
        Icon(
            Icons.AutoMirrored.Outlined.Comment,
            "作品评论",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
    if (onDownload != null) IconButton(onClick = onDownload) {
        Icon(Icons.Default.Download, "管理下载", tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun BookDetailActions(
    readingLabel: String, canRead: Boolean, onRead: () -> Unit,
    isInShelf: Boolean, secondaryEnabled: Boolean, onSecondary: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
            enabled = secondaryEnabled
        ) {
            Text(
                if (isInShelf) "已在书架" else "加入书架",
                color = LocalContentColor.current, maxLines = 1
            )
        }
        Button(onClick = onRead, modifier = Modifier.weight(1f), enabled = canRead) {
            Text(
                readingLabel,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class BookDetailTag(
    val name: String,
    val key: String,
    val blocked: Boolean = false,
    val enabled: Boolean = true
)

/** 标签的尺寸与间距共用，漫画可额外提供长按屏蔽操作。 */
@Composable
fun BookDetailTags(
    tags: List<BookDetailTag>,
    onClick: (BookDetailTag) -> Unit,
    onLongClick: ((BookDetailTag) -> Unit)? = null
) {
    if (tags.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(if (tag.blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                    .combinedClickable(
                        enabled = tag.enabled,
                        role = Role.Button,
                        onClickLabel = "查看${tag.name}作品",
                        onLongClickLabel = if (onLongClick == null) null else if (tag.blocked) "取消屏蔽此标签" else "屏蔽此标签",
                        onClick = { onClick(tag) },
                        onLongClick = onLongClick?.let { action -> { action(tag) } }
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tag.name, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = if (tag.blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun BookDetailCatalogueHeader(
    title: String,
    totalLabel: String,
    ascending: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        TextButton(
            onClick = onToggleSort,
            modifier = Modifier.semantics {
                stateDescription = if (ascending) "正序" else "倒序"
            }) {
            Text(
                "$totalLabel · ${if (ascending) "正序" else "倒序"}",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.SwapVert, null)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookDetailStatus(
    loading: Boolean,
    message: String,
    onRetry: () -> Unit,
    retryEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading) LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        else Icon(
            Icons.Default.Warning,
            "错误",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (!loading) FilledTonalButton(onClick = onRetry, enabled = retryEnabled) {
            Text("重试", color = LocalContentColor.current)
        }
    }
}
