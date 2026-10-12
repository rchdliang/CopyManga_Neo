package com.rchdliang.copymanga_neo.ui.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** 使用原始行、UTF-16 字符及行内比例，在字号和窗口变化后恢复阅读位置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelReader(state: NovelUiState, viewModel: NovelViewModel, onBack: () -> Unit) {
    val volume = state.reader ?: return
    val document = volume.document
    val lines = document.lines
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fontSize by rememberSaveable { mutableFloatStateOf(18f) }
    var catalogue by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var deleteCache by remember { mutableStateOf(false) }
    var imageKey by rememberSaveable(volume.volume.id) { mutableStateOf(viewModel.currentPosition()?.section?.takeIf { key -> volume.sections.any { it.key == key && it.type == 2 } }) }
    var currentAnchor by remember(volume.volume.id) { mutableStateOf(viewModel.currentPosition()) }
    var restoring by remember { mutableStateOf(true) }
    var appliedRevision by remember(volume.volume.id) { mutableIntStateOf(state.anchorRevision) }
    val width = LocalWindowInfo.current.containerSize.width
    val layouts = remember(
        volume.volume.id,
        document,
        fontSize,
        width
    ) { mutableStateMapOf<Int, TextLayoutResult>() }
    val image = volume.sections.firstOrNull { it.key == imageKey && it.type == 2 }
    var imageHeight by remember(imageKey) { mutableIntStateOf(0) }
    var imageLoaded by remember(imageKey) { mutableStateOf(false) }
    var imageError by remember(imageKey) { mutableStateOf(false) }
    var imageRetry by remember(imageKey) { mutableIntStateOf(0) }
    var pendingImageFraction by remember(imageKey) {
        mutableStateOf<Double?>(
            currentAnchor?.takeIf { it.section == imageKey }?.fraction ?: 0.0
        )
    }
    val imageList = rememberLazyListState()

    LaunchedEffect(volume.volume.id, state.anchorRevision, fontSize, width, imageKey) {
        if (appliedRevision != state.anchorRevision) {
            currentAnchor = state.restore
            appliedRevision = state.anchorRevision
            imageKey =
                state.restore?.section?.takeIf { key -> volume.sections.any { it.key == key && it.type == 2 } }
            pendingImageFraction = state.restore?.fraction ?: 0.0
        }
        if (imageKey != null) return@LaunchedEffect
        val anchor = currentAnchor ?: state.restore
        restoring = true
        val target = document.clampLine(anchor?.line ?: 0)
        list.scrollToItem(target)
        val layout = snapshotFlow { layouts[target] }.first { it != null } ?: return@LaunchedEffect
        val character = document.clampCharacter(target, anchor?.character ?: 0)
        val wrapped =
            layout.getLineForOffset(character.coerceAtMost(layout.layoutInput.text.length))
        val height = layout.getLineBottom(wrapped) - layout.getLineTop(wrapped)
        val offset = layout.getLineTop(wrapped).toInt() + ReadingPositionPolicy.offset(
            anchor?.fraction ?: 0.0, height.toInt()
        )
        list.scrollToItem(target, offset)
        restoring = false
    }
    LaunchedEffect(document, layouts, imageKey) {
        if (imageKey != null) return@LaunchedEffect
        snapshotFlow {
            Triple(
                list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                Triple(
                    list.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?.let { it.index == lines.lastIndex && it.offset + it.size <= list.layoutInfo.viewportEndOffset } == true && !list.isScrollInProgress,
                    restoring, layouts[list.firstVisibleItemIndex]))
        }.collect { (line, offset, flags) ->
            if (flags.second) return@collect
            val layout = flags.third ?: return@collect
            val completed = flags.first
            val wrapped = layout.getLineForVerticalPosition(offset.toFloat())
            val character = document.clampCharacter(line, layout.getLineStart(wrapped))
            val fraction = ReadingPositionPolicy.fraction(
                (offset - layout.getLineTop(wrapped)).toInt(),
                (layout.getLineBottom(wrapped) - layout.getLineTop(wrapped)).toInt()
            )
            val section =
                volume.sections.firstOrNull { it.type == 1 && line >= it.start && line < it.end }?.key.orEmpty()
            val previous = currentAnchor
            currentAnchor = (previous ?: LocalReadingRecord(
                "",
                "novel",
                volume.book.path,
                volume.volume.id,
                volume.volume.name
            )).copy(section = section, line = line, character = character, fraction = fraction)
            viewModel.position(section, line, character, fraction, completed)
        }
    }
    LaunchedEffect(imageKey, imageLoaded, imageHeight, state.anchorRevision) {
        val selected = image ?: return@LaunchedEffect
        if (!imageLoaded || imageHeight <= 0) return@LaunchedEffect
        val pending = pendingImageFraction
        if (pending != null) {
            imageList.scrollToItem(0, ReadingPositionPolicy.offset(pending, imageHeight))
            pendingImageFraction = null
        }
        snapshotFlow { imageList.firstVisibleItemScrollOffset }.collect { offset ->
            val fraction = ReadingPositionPolicy.fraction(offset, imageHeight)
            val previous = currentAnchor
            currentAnchor = (previous ?: LocalReadingRecord(
                "",
                "novel",
                volume.book.path,
                volume.volume.id,
                volume.volume.name
            )).copy(section = selected.key, fraction = fraction)
            viewModel.position(
                selected.key,
                previous?.line ?: 0,
                previous?.character ?: 0,
                fraction,
                false
            )
        }
    }
    fun jumpText(line: Int, section: String) {
        val old = currentAnchor
        currentAnchor = (old ?: LocalReadingRecord(
            "",
            "novel",
            volume.book.path,
            volume.volume.id,
            volume.volume.name
        )).copy(line = line, character = 0, fraction = 0.0, section = section)
        imageKey = null
        scope.launch { list.scrollToItem(document.clampLine(line)) }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        displayBookTitle(volume.book.path, volume.book.name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        volume.volume.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "返回目录"
                    )
                }
            },
            actions = {
                IconButton(onClick = viewModel::bookmark) {
                    Icon(
                        Icons.Outlined.BookmarkAdd,
                        "添加或移除当前位置书签"
                    )
                }
                IconButton(onClick = { settings = true }) {
                    Icon(
                        Icons.Outlined.Tune,
                        "阅读设置与缓存"
                    )
                }
            })
        if (state.caching || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (image != null) {
            LazyColumn(Modifier.weight(1f), state = imageList) {
                item(key = image.key) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { imageHeight = it.height }) {
                        Text(
                            image.name,
                            Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (imageError) {
                            Text(
                                "插图加载失败，可重试或缓存本卷。",
                                Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = {
                                imageError = false; imageRetry++
                            }) { Text("重试插图", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(volume.cachedImages[image.image]?.let(::File) ?: image.image)
                                .setParameter("novel_retry", imageRetry).build(),
                            contentDescription = image.name,
                            modifier = Modifier.fillMaxWidth(),
                            onError = { imageError = true },
                            onSuccess = { imageLoaded = true; imageError = false })
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                state = list,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    lines,
                    key = { index, _ -> "${volume.volume.id}:$index" }) { index, text ->
                    Text(
                        text.ifEmpty { "\u00a0" },
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.65f).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onTextLayout = { layouts[index] = it },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                volume.volume.previous?.let {
                    viewModel.openVolume(
                        volume.book.path,
                        it
                    )
                }
            }, enabled = volume.volume.previous != null) {
                Text(
                    "上一卷",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { catalogue = true }) {
                Text(
                    "卷内目录",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = {
                volume.volume.next?.let {
                    viewModel.openVolume(
                        volume.book.path,
                        it
                    )
                }
            }, enabled = volume.volume.next != null) {
                Text(
                    "下一卷",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        NovelReaderSettings(
            fontSize = fontSize,
            onFontSize = { fontSize = it },
            offlineReady = volume.offlineReady,
            caching = state.caching,
            refreshing = state.refreshing,
            onCache = viewModel::cache,
            onRefresh = viewModel::refreshVolume,
            onDeleteCache = { settings = false; deleteCache = true },
            downloadStatus = state.downloadInfo?.status.takeIf {
                state.downloadTask?.volumes?.any { it.volume.id == volume.volume.id } == true
            }
        )
    }
    if (catalogue) AlertDialog(
        onDismissRequest = { catalogue = false },
        title = { Text("卷内目录", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            LazyColumn {
                if (image != null) item {
                    TextButton(onClick = {
                        catalogue = false; jumpText(
                        currentAnchor?.line ?: 0,
                        ""
                    )
                    }) { Text("回到正文阅读位置", color = MaterialTheme.colorScheme.primary) }
                }
                item {
                    TextButton(onClick = {
                        catalogue = false; jumpText(
                        0,
                        ""
                    )
                    }) { Text("卷首 / 完整正文", color = MaterialTheme.colorScheme.onSurface) }
                }
                itemsIndexed(volume.sections, key = { _, section -> section.key }) { _, section ->
                    TextButton(onClick = {
                        catalogue = false
                        if (section.type == 1) jumpText(section.start, section.key)
                        else {
                            imageKey = section.key; currentAnchor =
                                currentAnchor?.copy(section = section.key, fraction = 0.0)
                        }
                    }) { Text(section.name, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { catalogue = false }) {
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        })
    if (deleteCache) AlertDialog(
        onDismissRequest = { deleteCache = false },
        title = { Text("删除本卷缓存？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "正文和插图缓存会删除；本地位置、历史与书签保留。",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = {
                deleteCache = false; viewModel.deleteCache()
            }) { Text("删除", color = MaterialTheme.colorScheme.onSurface) }
        },
        dismissButton = {
            TextButton(onClick = { deleteCache = false }) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        })
}

/** 阅读设置使用独立状态，便于预览和大字体检查。 */
@Composable
private fun NovelReaderSettings(
    fontSize: Float, onFontSize: (Float) -> Unit, offlineReady: Boolean,
    caching: Boolean, refreshing: Boolean, onCache: () -> Unit,
    onRefresh: () -> Unit, onDeleteCache: () -> Unit,
    downloadStatus: DownloadTaskStatus? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "阅读设置",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "正文字号",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("${fontSize.toInt()} sp", color = MaterialTheme.colorScheme.primary)
        }
        val sliderState = rememberSliderState(value = fontSize, steps = 9, trackRange = 12f..32f)
        sliderState.value = fontSize
        Slider(
            state = sliderState,
            onValueChange = {
                sliderState.value = it
                onFontSize(it)
            }
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                "故事在每一次翻页之后继续。调整到适合你的字号，安心读下去。",
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.65f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider()
        Text(
            "离线阅读",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (offlineReady) "正文和插图均已下载，可离线打开。" else "正文会在成功打开后保存；下载本卷可补齐插图，离开页面后仍会继续。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = onCache,
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    caching -> "暂停下载"
                    downloadStatus == DownloadTaskStatus.PAUSED || downloadStatus == DownloadTaskStatus.PAUSED_BY_RISK -> "继续下载"
                    downloadStatus == DownloadTaskStatus.ERROR -> "重试下载"
                    offlineReady -> "检查本卷下载"
                    else -> "下载本卷及插图"
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = onRefresh,
                enabled = !refreshing
            ) {
                Text(
                    if (refreshing) "检查中…" else "检查官方更新",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onDeleteCache, enabled = !refreshing) {
                Text(
                    "删除本卷缓存",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(name = "小说阅读设置", showBackground = true, widthDp = 412)
@Composable
private fun NovelReaderSettingsPreview() {
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelReaderSettings(
            18f,
            {},
            false,
            false,
            false,
            {},
            {},
            {})
    }
}
