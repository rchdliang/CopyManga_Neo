package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.RouteImageScope
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeImageModel
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
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
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.edgeToEdgeDialogProperties
import com.rchdliang.copymanga_neo.multiplatform.ui.components.requireAppImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.preview_reader_page
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.reading.novelImagePanLimits
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.readerImageData
import kotlinx.coroutines.launch

/** 正文、分页与图集共用原地址/本地缓存；预览只读取本地示例图。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NovelReaderImage(
    volume: NovelReadingVolume,
    image: NovelSection,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    translation: Offset = Offset.Zero,
    blurred: Boolean = false,
    onDecodedSize: (IntSize) -> Unit = {}
) {
    if (LocalInspectionMode.current) {
        Image(
            painterResource(Res.drawable.preview_reader_page), image.name,
            modifier.then(if (blurred) Modifier.blur(24.dp) else Modifier),
            contentScale = ContentScale.Fit
        )
        return
    }
    val context = LocalPlatformContext.current
    var retry by remember(image.key) { mutableIntStateOf(0) }
    val request = remember(image.image, volume.cachedImages[image.image], retry) {
        ImageRequest.Builder(context)
            .data((volume.cachedImages[image.image] ?: image.image)?.let(::readerImageData))
            .memoryCacheKeyExtra("novel_reader_retry", retry.toString())
            .build()
    }
    RouteImageScope {
        SubcomposeAsyncImage(
            imageLoader = requireAppImageLoader(),
            model = routeImageModel(request), contentDescription = image.name,
            modifier = modifier.graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = translation.x; translationY = translation.y
            },
            contentScale = ContentScale.Fit,
            onSuccess = {
                onDecodedSize(
                    IntSize(
                        it.result.image.width,
                        it.result.image.height
                    )
                )
            },
            success = {
                SubcomposeAsyncImageContent(modifier = if (blurred) Modifier.blur(24.dp) else Modifier)
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
}

@Composable
internal fun NovelReaderImageHeader(
    count: Int,
    blurred: Boolean?,
    onBlurChange: (Boolean) -> Unit,
    exporting: Boolean,
    onExport: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "本卷插图 · $count 张", Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconToggleButton(
            checked = blurred == true,
            enabled = blurred != null,
            onCheckedChange = onBlurChange
        ) {
            Icon(
                if (blurred == true) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (blurred == true) "关闭本书插图预览模糊" else "模糊本书插图预览"
            )
        }
        IconButton(onClick = onExport, enabled = count > 0 && !exporting) {
            if (exporting) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.FolderZip, "打包导出本卷全部插图")
        }
    }
}

@Composable
internal fun ColumnScope.NovelReaderImageGrid(
    volume: NovelReadingVolume,
    images: List<NovelSection>,
    blurred: Boolean = false,
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
                            .padding(8.dp)
                            .fillMaxWidth()
                            .aspectRatio(.75f)
                            .clip(MaterialTheme.shapes.medium),
                        blurred = blurred
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
    saving: Boolean = false,
    onSave: (NovelSection) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = images.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0),
        pageCount = { images.size }
    )
    val scope = rememberCoroutineScope()
    var zoomed by remember(pager.currentPage) { mutableStateOf(false) }
    if (LocalRouteActive.current) Dialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = true)
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
                        IconButton(
                            onClick = { onSave(images[pager.currentPage]) },
                            enabled = !saving
                        ) {
                            if (saving) CircularProgressIndicator(
                                Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            else Icon(Icons.Outlined.SaveAlt, "另存为当前插图")
                        }
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
                        pager.currentPage
                    ) { mutableFloatStateOf(1f) }
                    var offset by remember(
                        images[index].key,
                        pager.currentPage
                    ) { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged { size = it }
                            .pointerInput(images[index].key) {
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
                            .pointerInput(images[index].key) {
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
