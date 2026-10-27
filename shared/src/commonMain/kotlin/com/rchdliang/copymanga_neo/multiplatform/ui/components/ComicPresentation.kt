package com.rchdliang.copymanga_neo.multiplatform.ui.components

import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ComicCoverVisibility
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.withFallback
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage
import kotlin.math.roundToInt

internal data class ComicExpansion(
    val id: String, val path: String, val name: String, val cover: String?,
    val subtitle: String?, val status: String?, val bounds: Rect, val onClick: () -> Unit,
    val novel: NovelBook? = null
)

internal data class ComicPresentationContext(
    val state: ComicPresentationUiState,
    val preload: (List<String>, Boolean) -> Unit,
    val retry: (String) -> Unit,
    val expand: (ComicExpansion) -> Unit,
    val expandedId: String?,
    val dismiss: () -> Unit
)

internal val LocalComicPresentation = staticCompositionLocalOf<ComicPresentationContext?> { null }

/** 根层绘制预览，独立于 LazyRow 的测量与裁剪，不挤动相邻卡片。 */
@Composable
fun ComicPresentationHost(
    viewModel: ComicPresentationViewModel,
    novelPreview: NovelPreviewViewModel,
    content: @Composable () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val novelState by novelPreview.state.collectAsStateWithLifecycle()
    BookPresentationHost(state, viewModel::preload, viewModel::retry,
        novelState, novelPreview::show, novelPreview::retry, content)
}

@Composable
internal fun BookPresentationHost(
    state: ComicPresentationUiState,
    preload: (List<String>, Boolean) -> Unit,
    retry: (String) -> Unit,
    novelState: NovelPreviewUiState,
    onNovelPreview: (NovelBook?) -> Unit,
    onNovelRetry: () -> Unit,
    content: @Composable () -> Unit
) {
    var expansion by remember { mutableStateOf<ComicExpansion?>(null) }
    var opened by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress by animateFloatAsState(
        if (opened) 1f else 0f, tween(240), label = "作品预览展开",
        finishedListener = { if (it == 0f) expansion = null }
    )
    val dismiss = { opened = false }
    val scroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available != Offset.Zero) opened = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state.cacheScope, novelState.epoch) { opened = false; expansion = null }
    LaunchedEffect(opened, expansion?.id) {
        onNovelPreview(expansion?.novel.takeIf { opened })
    }
    val context = ComicPresentationContext(state, preload, retry, {
        expansion = it
        opened = true
        if (it.novel == null && !it.path.startsWith("novel:")) preload(listOf(it.path), true)
    }, expansion?.id, dismiss)
    CompositionLocalProvider(LocalComicPresentation provides context) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                .nestedScroll(scroll)
                .pointerInput(expansion, origin) {
                    if (expansion != null) awaitEachGesture {
                        val down = awaitFirstDown(false, PointerEventPass.Initial)
                        val source = expansion ?: return@awaitEachGesture
                        val targetWidth = minOf(source.bounds.width * 3, size.width - 32.dp.toPx())
                        val left = (source.bounds.left - origin.x).coerceIn(
                            16.dp.toPx(),
                            (size.width - targetWidth - 16.dp.toPx()).coerceAtLeast(16.dp.toPx())
                        )
                        val rect = Rect(
                            left,
                            source.bounds.top - origin.y,
                            left + targetWidth,
                            source.bounds.bottom - origin.y
                        )
                        if (!rect.contains(down.position)) {
                            down.consume()
                            waitForUpOrCancellation()
                            opened = false
                        }
                    }
                }) {
            content()
            // 在导航内容之后注册，返回键优先收起预览。
            BackHandler(expansion != null) { opened = false }
            val selected = expansion
            if (selected != null) {
                val density = LocalDensity.current
                val coverWidth = with(density) { selected.bounds.width.toDp() }
                val height = with(density) { selected.bounds.height.toDp() }
                val fullWidth = minOf(coverWidth * 3, maxWidth - 32.dp)
                val endX = with(density) {
                    (selected.bounds.left - origin.x).coerceIn(
                        16.dp.toPx(),
                        (constraints.maxWidth - fullWidth.toPx() - 16.dp.toPx()).coerceAtLeast(16.dp.toPx())
                    )
                }
                val startX = selected.bounds.left - origin.x
                val startY = selected.bounds.top - origin.y
                Row(
                    Modifier
                        .offset {
                            IntOffset(
                                (startX + (endX - startX) * progress).roundToInt(),
                                startY.roundToInt()
                            )
                        }
                        .width(coverWidth + (fullWidth - coverWidth) * progress)
                        .height(height)
                        .zIndex(10f)
                        .shadow(8.dp, MaterialTheme.shapes.large)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { opened = false; expansion = null; selected.onClick() }) {
                    ComicCard(
                        name = selected.name,
                        cover = selected.cover,
                        onClick = {
                            opened = false; expansion = null; selected.onClick()
                        },
                        subtitle = selected.subtitle,
                        statusText = selected.status,
                        comicPathWord = selected.path,
                        modifier = Modifier.width(coverWidth),
                        floating = true
                    )
                    val informationModifier = Modifier
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(fullWidth - coverWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                alpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            }
                    val novel = selected.novel
                    if (novel != null) {
                        val resolved = novelState.book?.takeIf { it.path == novel.path }
                        val book = resolved?.withFallback(novel) ?: novel
                        BookCardInformation(
                            modifier = informationModifier,
                            themes = book.themes.map { it.name }, brief = book.brief,
                            popular = book.popular, updated = book.updated,
                            loading = if (resolved != null) novelState.loading else book.brief.isBlank(),
                            error = novelState.error.takeIf { resolved != null },
                            onRetry = onNovelRetry
                        )
                    } else ComicCardInformation(selected.path, informationModifier)
                }
            }
        }
    }
}

/** 页内所有已加载条目都参与补全，未加载分页不产生详情请求。 */
@Composable
fun ComicPreloadEffect(paths: List<String>) {
    val context = LocalComicPresentation.current
    LaunchedEffect(paths, context?.state?.cacheScope) { context?.preload?.invoke(paths, false) }
}

@Composable
internal fun comicVisibility(path: String?): ComicCoverVisibility {
    val context = LocalComicPresentation.current
    LaunchedEffect(path, context?.state?.cacheScope) {
        path?.takeUnless { it.startsWith("novel:") }?.let { context?.preload?.invoke(listOf(it), true) }
    }
    return context?.state?.visibility(path) ?: ComicCoverVisibility.VISIBLE
}

/** 未确认时只显示占位；失败后可主动重试，不泄露封面或标题。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComicPending(path: String?, modifier: Modifier = Modifier) {
    val context = LocalComicPresentation.current
    val failed = context?.state?.failures?.containsKey(path) == true
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (failed && path != null) TextButton(onClick = { context?.retry?.invoke(path) }) {
            Text("重试", color = MaterialTheme.colorScheme.primary)
        } else LoadingIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
    }
}

/** 仅对图像施加模糊，图标和标题不参与模糊。 */
@Composable
internal fun ComicCoverImage(
    path: String?, model: Any?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    error: androidx.compose.ui.graphics.painter.Painter? = null
) {
    val visibility = comicVisibility(path)
    Box(modifier.clip(MaterialTheme.shapes.large)) {
        if (visibility == ComicCoverVisibility.PENDING) ComicPending(
            path,
            Modifier.matchParentSize()
        )
        else {
            PreviewAwareAsyncImage(
                coverImageModel(model), contentDescription, Modifier
                    .matchParentSize()
                    .then(
                        if (visibility == ComicCoverVisibility.BLURRED) Modifier.blur(18.dp) else Modifier
                    ), contentScale, error = error
            )
            if (visibility == ComicCoverVisibility.BLURRED) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.VisibilityOff, "已模糊黑名单封面", Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
