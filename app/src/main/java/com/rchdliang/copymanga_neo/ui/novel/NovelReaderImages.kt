package com.rchdliang.copymanga_neo.ui.novel

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.reading.novelImagePanLimits
import kotlinx.coroutines.launch
import java.io.File

/** 正文、分页与图集共用原地址/本地缓存；预览只读取本地示例图。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NovelReaderImage(
    volume: NovelReadingVolume,
    image: NovelSection,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    translation: Offset = Offset.Zero,
    onDecodedSize: (IntSize) -> Unit = {}
) {
    if (LocalInspectionMode.current) {
        Image(
            painterResource(R.drawable.preview_reader_page), image.name, modifier,
            contentScale = ContentScale.Fit
        )
        return
    }
    val context = LocalContext.current
    var retry by remember(image.key) { mutableIntStateOf(0) }
    val request = remember(image.image, volume.cachedImages[image.image], retry) {
        ImageRequest.Builder(context)
            .data(volume.cachedImages[image.image]?.let(::File) ?: image.image)
            .setParameter("novel_reader_retry", retry)
            .build()
    }
    SubcomposeAsyncImage(
        model = request, contentDescription = image.name,
        modifier = modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = translation.x; translationY = translation.y
        },
        contentScale = ContentScale.Fit,
        onSuccess = {
            onDecodedSize(
                IntSize(
                    it.result.drawable.intrinsicWidth,
                    it.result.drawable.intrinsicHeight
                )
            )
        },
        loading = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }
        },
        error = {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("插图暂时无法加载", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { retry++ }) {
                    Text(
                        "重试",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
internal fun ColumnScope.NovelReaderImageGrid(
    volume: NovelReadingVolume,
    images: List<NovelSection>,
    onOpen: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(images, key = { _, image -> image.key }) { index, image ->
            Surface(
                onClick = { onOpen(image.key) }, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    NovelReaderImage(
                        volume, image, Modifier
                            .fillMaxWidth()
                            .aspectRatio(.75f)
                    )
                    Text(
                        image.name.ifBlank { "插图 ${index + 1}" },
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** 图集是独立查看层，不覆盖正文锚点；缩放时暂停横向翻图，关闭后原位继续阅读。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelReaderGallery(
    volume: NovelReadingVolume,
    images: List<NovelSection>,
    selectedKey: String,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = images.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0),
        pageCount = { images.size }
    )
    val scope = rememberCoroutineScope()
    var zoomed by remember(pager.currentPage) { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            ) {
                TopAppBar(
                    title = {
                        Text(
                            images[pager.currentPage].name,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Outlined.Close,
                                "返回正文"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            reset++; zoomed = false
                        }) { Icon(Icons.Outlined.RestartAlt, "重置缩放") }
                    },
                    windowInsets = WindowInsets(0)
                )
                HorizontalPager(
                    state = pager, modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    userScrollEnabled = !zoomed
                ) { index ->
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    var imageSize by remember(images[index].key) { mutableStateOf(IntSize.Zero) }
                    var scale by remember(
                        images[index].key,
                        reset,
                        pager.currentPage
                    ) { mutableFloatStateOf(1f) }
                    var offset by remember(
                        images[index].key,
                        reset,
                        pager.currentPage
                    ) { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged { size = it }
                            .pointerInput(images[index].key, reset) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } > 1 || scale > 1f) {
                                            val pan = event.calculatePan()
                                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                            val (maxX, maxY) = novelImagePanLimits(
                                                size.width, size.height,
                                                imageSize.width, imageSize.height, scale
                                            )
                                            offset = if (scale == 1f) Offset.Zero else Offset(
                                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                                            )
                                            if (pager.currentPage == index) zoomed = scale > 1f
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(images[index].key, reset) {
                                detectTapGestures(onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offset = Offset.Zero
                                    if (pager.currentPage == index) zoomed = scale > 1f
                                })
                            }) {
                        NovelReaderImage(
                            volume,
                            images[index],
                            Modifier.fillMaxSize(),
                            scale,
                            offset,
                            onDecodedSize = {
                                imageSize = it
                                val (maxX, maxY) = novelImagePanLimits(
                                    size.width,
                                    size.height,
                                    it.width,
                                    it.height,
                                    scale
                                )
                                offset = Offset(
                                    offset.x.coerceIn(-maxX, maxX),
                                    offset.y.coerceIn(-maxY, maxY)
                                )
                            }
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0
                    ) { Icon(Icons.Outlined.ChevronLeft, "上一张插图") }
                    Text(
                        "${pager.currentPage + 1} / ${images.size}",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < images.lastIndex
                    ) { Icon(Icons.Outlined.ChevronRight, "下一张插图") }
                }
            }
        }
    }
}
