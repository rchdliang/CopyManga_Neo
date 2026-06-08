package com.rchdliang.copymanga_neo.ui.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseDto
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.DisplayAndValue
import com.rchdliang.copymanga_neo.data.remote.dto.GroupsDto
import com.rchdliang.copymanga_neo.data.remote.dto.LastChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ThemeDto
import com.rchdliang.copymanga_neo.data.remote.util.toCompactLocale
import com.rchdliang.copymanga_neo.ui.components.ChapterItem
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    pathWord: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onChapterClick: (String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    LaunchedEffect(pathWord) {
        viewModel.loadDetail(pathWord)
    }

    LaunchedEffect(uiState.shelfMessage) {
        uiState.shelfMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearShelfMessage()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refreshDetail() },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isLoading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.error != null && uiState.comicDetail == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "错误",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "加载失败：${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = { viewModel.loadDetail(pathWord) },
                    ) { Text("重试") }
                }
            }

            uiState.riskControlReason != null && uiState.comicDetail == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "风控",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "触发风控",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.riskControlReason!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            uiState.comicDetail != null -> {
                val stableOnGroupSelect =
                    remember { { group: String -> viewModel.selectGroup(group) } }
                val stableOnToggleSortOrder =
                    remember { { viewModel.toggleSortOrder() } }
                val stableOnToggleShelf = remember(uiState.isLoggedIn) {
                    {
                        if (uiState.isLoggedIn) {
                            viewModel.toggleShelf()
                        } else {
                            onNavigateToProfile()
                        }
                    }
                }
                val stableOnStartReading = remember {
                    {
                        val info = viewModel.getStartChapter()
                        if (info != null) {
                            onChapterClick(
                                info.uuid,
                                uiState.comicDetail!!.pathWord,
                                info.name,
                                info.groupPathWord
                            )
                        }
                    }
                }
                DetailContent(
                    comicDetail = uiState.comicDetail!!,
                    chapters = uiState.chapters,
                    groups = uiState.groups,
                    selectedGroup = uiState.selectedGroup,
                    sortAscending = uiState.sortAscending,
                    isLoggedIn = uiState.isLoggedIn,
                    isInShelf = uiState.isInShelf,
                    isShelfLoading = uiState.isShelfLoading,
                    browse = uiState.browse,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedChapterUuids = uiState.selectedChapterUuids,
                    chapterDownloadStatuses = uiState.chapterDownloadStatuses,
                    chapterDownloadProgresses = uiState.chapterDownloadProgresses,
                    onChapterClick = onChapterClick,
                    onChapterLongPress = { uuid -> viewModel.enterSelectionMode(uuid) },
                    onChapterToggleSelect = { uuid -> viewModel.toggleChapterSelection(uuid) },
                    onGroupSelect = stableOnGroupSelect,
                    onToggleSortOrder = stableOnToggleSortOrder,
                    onToggleShelf = stableOnToggleShelf,
                    onStartReading = stableOnStartReading,
                    onNavigateBack = onNavigateBack,
                    onExitSelection = { viewModel.exitSelectionMode() },
                    onSelectAll = { viewModel.selectAll() },
                    onInvertSelection = { viewModel.invertSelection() },
                    onSelectToLatest = { viewModel.selectToLatest() },
                    onSelectBeforeLatest = { viewModel.selectBeforeLatest() },
                    onSelectBetween = { viewModel.selectBetween() },
                    canSelectBetween = { viewModel.canSelectBetween() },
                    onClearSelection = { viewModel.clearSelection() },
                    onEnterSelectionModeEmpty = { viewModel.enterSelectionModeEmpty() },
                    onStartDownload = {
                        viewModel.startDownload()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    comicDetail: ComicDetailDto,
    chapters: ImmutableList<ChapterDto>,
    groups: ImmutableList<GroupsDto>,
    selectedGroup: String,
    sortAscending: Boolean,
    isLoggedIn: Boolean,
    isInShelf: Boolean,
    isShelfLoading: Boolean,
    browse: BrowseDto?,
    isSelectionMode: Boolean,
    selectedChapterUuids: Set<String>,
    chapterDownloadStatuses: Map<String, ChapterDownloadStatus>,
    chapterDownloadProgresses: Map<String, Float>,
    onChapterClick: (String, String, String, String) -> Unit,
    onChapterLongPress: (String) -> Unit,
    onChapterToggleSelect: (String) -> Unit,
    onGroupSelect: (String) -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleShelf: () -> Unit,
    onStartReading: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectToLatest: () -> Unit,
    onSelectBeforeLatest: () -> Unit,
    onSelectBetween: () -> Unit,
    canSelectBetween: () -> Boolean,
    onClearSelection: () -> Unit,
    onEnterSelectionModeEmpty: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier
) {
    val currentPathWord = remember(comicDetail.pathWord) { comicDetail.pathWord }
    val currentOnChapterClick by rememberUpdatedState(onChapterClick)
    val currentOnChapterLongPress by rememberUpdatedState(onChapterLongPress)
    val currentOnChapterToggleSelect by rememberUpdatedState(onChapterToggleSelect)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        BackHandler(enabled = isSelectionMode) {
            onExitSelection()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = rememberScrollableState { 0f },
                    orientation = Orientation.Vertical
                )
        ) {
            if (isSelectionMode) {
                SelectionTopRow(
                    selectedCount = selectedChapterUuids.size,
                    canSelectBetween = canSelectBetween(),
                    onExit = onExitSelection,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    onSelectToLatest = onSelectToLatest,
                    onSelectBeforeLatest = onSelectBeforeLatest,
                    onSelectBetween = onSelectBetween,
                    onClearSelection = onClearSelection
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (chapters.isNotEmpty()) {
                            onEnterSelectionModeEmpty()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SubcomposeAsyncImage(
                    model = comicDetail.cover,
                    contentDescription = comicDetail.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .aspectRatio(1f),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "加载失败",
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        is AsyncImagePainter.State.Success -> {
                            Image(
                                painter = painter,
                                contentDescription = comicDetail.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> Unit
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = comicDetail.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "作者",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        comicDetail.author?.let { authors ->
                            Text(
                                text = authors.joinToString { author -> author.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pending,
                            contentDescription = "状态",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (comicDetail.status.value == 1) "已完结" else "连载中",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Update,
                            contentDescription = "最后更新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = comicDetail.lastUpdate ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "热度",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = comicDetail.popular?.toCompactLocale() ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleShelf,
                    modifier = Modifier.weight(1f),
                    enabled = !isShelfLoading
                ) {
                    Text(
                        text = if (isInShelf) "已在书架" else "加入书架",
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onStartReading,
                    modifier = Modifier.weight(1f),
                    enabled = chapters.isNotEmpty()
                ) {
                    Text(
                        text = if (browse != null && browse.chapterUuid != null) "继续阅读" else "开始阅读",
                        maxLines = 1
                    )
                }
            }

            comicDetail.theme?.let { themes ->
                if (themes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = theme.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            comicDetail.brief?.let { brief ->
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                        .animateContentSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = brief,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    groups.forEachIndexed { index, group ->
                        key(group.pathWord) {
                            ToggleButton(
                                checked = selectedGroup == group.pathWord,
                                onCheckedChange = { if (it) onGroupSelect(group.pathWord) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    groups.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.semantics { role = Role.RadioButton }
                            ) {
                                Text(text = group.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "章节列表",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (groups.isNotEmpty()) {
                    val currentGroup =
                        remember(
                            groups,
                            selectedGroup
                        ) { groups.find { it.pathWord == selectedGroup } }
                    if (currentGroup != null) {
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = 0.dp,
                                    minHeight = 0.dp
                                ),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = ButtonDefaults.buttonColors(Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "共${currentGroup.total}话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.UnfoldMore,
                                    contentDescription = "排序菜单",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("正序") },
                                    onClick = {
                                        if (!sortAscending) onToggleSortOrder()
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("倒序") },
                                    onClick = {
                                        if (sortAscending) onToggleSortOrder()
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val displayedChapters = remember(chapters, sortAscending) {
                if (sortAscending) chapters else chapters.reversed()
            }

            val bottomPadding = if (isSelectionMode) 80.dp else 4.dp

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = bottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    count = displayedChapters.size,
                    key = { displayedChapters[it].uuid },
                    contentType = { "chapter" }
                ) { index ->
                    val chapter = displayedChapters[index]
                    val status = chapterDownloadStatuses[chapter.uuid]
                    val progress = chapterDownloadProgresses[chapter.uuid]
                    ChapterItem(
                        chapter = chapter,
                        isSelected = chapter.uuid in selectedChapterUuids,
                        isSelectionMode = isSelectionMode,
                        downloadStatus = status,
                        downloadProgress = progress,
                        onClick = {
                            if (isSelectionMode) {
                                currentOnChapterToggleSelect(chapter.uuid)
                            } else {
                                currentOnChapterClick(
                                    chapter.uuid,
                                    currentPathWord,
                                    chapter.name,
                                    chapter.groupPathWord ?: "default"
                                )
                            }
                        },
                        onLongPress = {
                            currentOnChapterLongPress(chapter.uuid)
                        }
                    )
                }
            }
        }

        if (isSelectionMode) {
            SelectionBottomBar(
                selectedCount = selectedChapterUuids.size,
                onStartDownload = onStartDownload,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopRow(
    selectedCount: Int,
    canSelectBetween: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectToLatest: () -> Unit,
    onSelectBeforeLatest: () -> Unit,
    onSelectBetween: () -> Unit,
    onClearSelection: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消选择",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "已选 $selectedCount 章",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "全选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Default.Deselect,
                    contentDescription = "反选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 2)
                    ) {
                        MenuDefaults.Label {
                            Text(
                                "选择",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
//                        HorizontalDivider(
//                            modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding)
//                        )
                        DropdownMenuItem(
                            text = { Text("到最新") },
                            onClick = {
                                onSelectToLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("这之前") },
                            onClick = {
                                onSelectBeforeLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("之间") },
                            onClick = {
                                onSelectBetween()
                                menuExpanded = false
                            },
                            enabled = canSelectBetween
                        )
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(1, 2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("取消全部选择") },
                            onClick = {
                                onClearSelection()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            Button(
                onClick = onStartDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Downloading,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("下载 $selectedCount 章")
            }
        }
    )
}

// region Previews
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PreviewScreenSizes
@Composable
private fun DetailContentPreview() {
    CopyManga_NeoTheme {
        val mockDetail = remember {
            ComicDetailDto(
                uuid = "preview-uuid",
                b404 = false,
                bHidden = false,
                ban = 0,
                name = "示例漫画：冒险者的奇幻旅途",
                comicAlias = "Adventure Journey",
                pathWord = "preview-comic",
                closeComment = false,
                closeRoast = false,
                freeType = DisplayAndValue("免费", 0),
                restrict = DisplayAndValue("一般向", 0),
                reclass = DisplayAndValue("热血", 1),
                females = listOf("女主A", "女主B"),
                males = listOf("男主C"),
                clubs = listOf("汉化社"),
                region = DisplayAndValue("日本", 1),
                status = DisplayAndValue("连载中", 0),
                author = listOf(
                    AuthorDto("山田太郎", "yamada-taro"),
                    AuthorDto("作画助手", "sakuga")
                ),
                theme = listOf(
                    ThemeDto("冒险", "adventure"),
                    ThemeDto("奇幻", "fantasy"),
                    ThemeDto("热血", "hot-blood")
                ),
                parodies = null,
                brief = "这是一个关于少年冒险者在大陆上探索未知世界的故事。",
                lastUpdate = "2025-03-15",
                cover = "https://example.com/cover_detail.jpg",
                lastChapter = LastChapterDto("last-ch-id", "第 128 话"),
                popular = 10000,
                channel = null
            )
        }

        val mockChapters = remember {
            persistentListOf(
                ChapterDto(
                    1,
                    "ch-uuid-1",
                    25,
                    1,
                    1024,
                    "第 128 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-15",
                    "prev-ch-127",
                    "next-ch-129"
                ),
                ChapterDto(
                    2,
                    "ch-uuid-2",
                    25,
                    2,
                    2048,
                    "第 127 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-08",
                    "prev-ch-126",
                    "ch-uuid-1"
                ),
                ChapterDto(
                    3,
                    "ch-uuid-3",
                    25,
                    3,
                    1536,
                    "第 126 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-01",
                    "prev-ch-125",
                    "ch-uuid-2"
                )
            )
        }

        val mockGroups = remember {
            persistentListOf(
                GroupsDto("group-alpha", 128, "Alpha 汉化组"),
                GroupsDto("group-beta", 85, "Beta 汉化组")
            )
        }

        var selectedGroup by remember { mutableStateOf("group-alpha") }
        var sortAscending by remember { mutableStateOf(true) }

        DetailContent(
            comicDetail = mockDetail,
            chapters = mockChapters,
            groups = mockGroups,
            selectedGroup = selectedGroup,
            sortAscending = sortAscending,
            isLoggedIn = true,
            isInShelf = false,
            isShelfLoading = false,
            browse = null,
            isSelectionMode = false,
            selectedChapterUuids = emptySet(),
            chapterDownloadStatuses = emptyMap(),
            chapterDownloadProgresses = emptyMap(),
            onChapterClick = { _, _, _, _ -> },
            onChapterLongPress = {},
            onChapterToggleSelect = {},
            onGroupSelect = { selectedGroup = it },
            onToggleSortOrder = { sortAscending = !sortAscending },
            onToggleShelf = {},
            onStartReading = {},
            onNavigateBack = {},
            onExitSelection = {},
            onSelectAll = {},
            onInvertSelection = {},
            onSelectToLatest = {},
            onSelectBeforeLatest = {},
            onSelectBetween = {},
            canSelectBetween = { false },
            onClearSelection = {},
            onEnterSelectionModeEmpty = {},
            onStartDownload = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
// endregion
