package com.rchdliang.copymanga_neo.ui.reader

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReaderContent(
    effectiveImages: List<String>,
    isContinuous: Boolean,
    isRtl: Boolean,
    contentScale: ContentScale,
    pagerState: PagerState,
    listState: LazyListState,
    continuousNestedScrollConnection: NestedScrollConnection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowMenu: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)) {
        if (effectiveImages.isEmpty()) {
            Text("暂无图片", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (isContinuous) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(continuousNestedScrollConnection)
                    .readerTapGestures(
                        isRtl = false,
                        isContinuous = true,
                        onForward = onForward,
                        onBackward = onBackward,
                        onShowMenu = onShowMenu,
                        onLongPress = onLongPress
                    )
            ) {
                itemsIndexed(effectiveImages) { index, path ->
                    var retryKey by rememberSaveable(path) { mutableIntStateOf(0) }
                    val request = remember(path, retryKey) {
                        val data: Any = if (path.startsWith("http")) path else File(path)
                        ImageRequest.Builder(context)
                            .data(data)
                            .setParameter("retry_hash", retryKey)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = contentScale
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = (index + 1).toString(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        CircularWavyProgressIndicator(color = Color.White)
                                    }
                                }
                            }

                            is AsyncImagePainter.State.Error -> {
                                val error = painter.state as AsyncImagePainter.State.Error
                                val message = error.result.throwable.message
                                    ?: error.result.throwable.javaClass.simpleName
                                    ?: "未知错误"
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clickable { retryKey++ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "加载失败:$message",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            is AsyncImagePainter.State.Success -> {
                                Image(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = contentScale
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }
        } else {
            val density = LocalDensity.current
            val thresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
            val coroutineScope = rememberCoroutineScope()

            val pagerOverscrollConnection = remember(
                coroutineScope, thresholdPx, isRtl, onPrevChapter, onNextChapter
            ) {
                PagerChapterOverscroll(
                    coroutineScope = coroutineScope,
                    thresholdPx = thresholdPx,
                    isRtl = isRtl,
                    onPrevChapter = onPrevChapter,
                    onNextChapter = onNextChapter
                )
            }

            HorizontalPager(
                state = pagerState,
                reverseLayout = isRtl,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pagerOverscrollConnection)
                    .readerTapGestures(
                        isRtl = isRtl,
                        isContinuous = false,
                        onForward = onForward,
                        onBackward = onBackward,
                        onShowMenu = onShowMenu,
                        onLongPress = onLongPress
                    ),
                beyondViewportPageCount = 2
            ) { page ->
                val path = effectiveImages[page]
                var retryKey by rememberSaveable(path) { mutableIntStateOf(0) }
                val request = remember(path, retryKey) {
                    val data: Any = if (path.startsWith("http")) path else File(path)
                    ImageRequest.Builder(context)
                        .data(data)
                        .setParameter("retry_hash", retryKey)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = (page + 1).toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    CircularWavyProgressIndicator(color = Color.White)
                                }
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            val error = painter.state as AsyncImagePainter.State.Error
                            val message = error.result.throwable.message
                                ?: error.result.throwable.javaClass.simpleName
                                ?: "未知错误"
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { retryKey++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "加载失败:$message",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        is AsyncImagePainter.State.Success -> {
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = contentScale
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

private class PagerChapterOverscroll(
    private val coroutineScope: CoroutineScope,
    private val thresholdPx: Float,
    private val isRtl: Boolean,
    private val onPrevChapter: () -> Unit,
    private val onNextChapter: () -> Unit
) : NestedScrollConnection {
    private var totalOverscroll = 0f

    override fun onPreScroll(
        available: androidx.compose.ui.geometry.Offset,
        source: NestedScrollSource
    ): androidx.compose.ui.geometry.Offset {
        Log.d(
            "ReaderContent",
            "pager onPreScroll: overscrollAccum=$totalOverscroll threshold=$thresholdPx isRtl=$isRtl available=(${available.x},${available.y}) source=$source"
        )
        return androidx.compose.ui.geometry.Offset.Zero
    }

    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset,
        available: androidx.compose.ui.geometry.Offset,
        source: NestedScrollSource
    ): androidx.compose.ui.geometry.Offset {
        // RTL (reverseLayout) 模式下 pager 内部坐标被翻转，需要对 available.x 取反
        val effectiveDelta = if (isRtl) -available.x else available.x
        totalOverscroll += effectiveDelta
        Log.d(
            "ReaderContent",
            "pager onPostScroll: consumed=(${consumed.x},${consumed.y}) available=(${available.x},${available.y}) effectiveDelta=$effectiveDelta -> accum=$totalOverscroll"
        )
        return androidx.compose.ui.geometry.Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val overscroll = totalOverscroll
        totalOverscroll = 0f
        Log.i(
            "ReaderContent",
            "pager onPostFling: accum=$overscroll threshold=$thresholdPx consumed=(${consumed.x},${consumed.y}) available=(${available.x},${available.y})"
        )
        if (overscroll > thresholdPx) {
            Log.i(
                "ReaderContent",
                ">>> TRIGGER prevChapter (pager) accum=$overscroll > threshold=$thresholdPx"
            )
            coroutineScope.launch { onPrevChapter() }
        } else if (overscroll < -thresholdPx) {
            Log.i(
                "ReaderContent",
                ">>> TRIGGER nextChapter (pager) accum=$overscroll < -$thresholdPx"
            )
            coroutineScope.launch { onNextChapter() }
        } else {
            Log.i("ReaderContent", ">>> NO trigger (pager) accum=$overscroll, below threshold")
        }
        return Velocity.Zero
    }
}

@Composable
fun ReaderTopBar(
    timeText: String,
    chapterName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = chapterName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ReaderPageIndicator(
    pageText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = pageText,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    )
}
