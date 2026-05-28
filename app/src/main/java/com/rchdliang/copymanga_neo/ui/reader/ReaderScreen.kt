package com.rchdliang.copymanga_neo.ui.reader

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs


/**
 * 阅读器屏幕
 * 显示章节图片列表，支持左右翻页和跨章节跳转
 */
@Composable
fun ReaderScreen(
    chapterPathWord: String,
    comicPathWord: String,
    chapterName: String,
    groupPathWord: String = "default",
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // 隐藏状态栏逻辑：进入阅读器时隐藏，退出时恢复
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        // 隐藏状态栏
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        // 设置行为：滑动边缘临时显示状态栏，过一会儿自动隐藏
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // 退出阅读器时恢复状态栏
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    var showOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(chapterPathWord) {
        viewModel.loadChapter(comicPathWord, chapterPathWord, groupPathWord)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showOverlay = !showOverlay }
    ) {
        when (val state = uiState) {
            is ReaderUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            is ReaderUiState.Error -> {
                Text(
                    text = "加载失败：${state.message}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            is ReaderUiState.RiskControl -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "风控",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "触发风控",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.reason,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
            is ReaderUiState.Success -> {
                val images = state.content.imageUrls
                val pagerState = rememberPagerState(pageCount = { images.size })
                val density = LocalDensity.current
                val threshold = remember { with(density) { 60.dp.toPx() } }
                var lastOverscrollTime by remember { mutableLongStateOf(0L) }
                val coroutineScope = rememberCoroutineScope()
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pagerState) {
                            awaitEachGesture {
                                // 等待用户按下手指
                                awaitFirstDown(requireUnconsumed = false)
                                var overscrollAmount = 0f

                                do {
                                    // 使用 Initial 阶段，在 Pager 消费事件前获取最原始的滑动数据
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    // 获取当前手势在 X 轴上的移动量
                                    val delta = event.calculatePan().x

                                    // 判断 Pager 是否完全停留在第一页或最后一页
                                    val atStartEdge =
                                        pagerState.currentPage == 0 && abs(pagerState.currentPageOffsetFraction) < 0.001f
                                    val atEndEdge =
                                        pagerState.currentPage == pagerState.pageCount - 1 && abs(
                                            pagerState.currentPageOffsetFraction
                                        ) < 0.001f

                                    if (atStartEdge && (delta > 0 || overscrollAmount > 0)) {
                                        // 在第一页向右拉伸（delta > 0），或者处于拉伸状态下向左回缩
                                        overscrollAmount += delta
                                        // 防止回缩过度导致数值变成负数
                                        overscrollAmount = overscrollAmount.coerceAtLeast(0f)
                                    } else if (atEndEdge && (delta < 0 || overscrollAmount < 0)) {
                                        // 在最后一页向左拉伸（delta < 0），或者处于拉伸状态下向右回缩
                                        overscrollAmount += delta
                                        // 防止回缩过度导致数值变成正数
                                        overscrollAmount = overscrollAmount.coerceAtMost(0f)
                                    } else {
                                        overscrollAmount = 0f
                                    }
                                } while (event.changes.any { it.pressed }) // 只要还有手指按下，就继续循环

                                // 手指全部抬起，判断累积的拉伸距离是否达到阈值
                                if (overscrollAmount > threshold) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "切换到上一章", Toast.LENGTH_SHORT)
                                            .show()
                                        viewModel.loadPrevChapter()
                                    }
                                } else if (overscrollAmount < -threshold) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "切换到下一章", Toast.LENGTH_SHORT)
                                            .show()
                                        viewModel.loadNextChapter()
                                    }
                                }
                            }
                        },
                    beyondViewportPageCount = 2,
                ) { page ->
                    AsyncImage(
                        model = images[page],
                        contentDescription = "第 ${page + 1} 页",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // 顶部 Overlay
                if (showOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .statusBarsPadding() // 状态栏隐藏时，此 padding 自动为 0；滑出状态栏时，自动留出空间
                            .padding(8.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
//                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = java.text.SimpleDateFormat(
                                    "HH:mm",
                                    LocalLocale.current.platformLocale
                                )
                                    .format(java.util.Date()),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${pagerState.currentPage + 1}/${images.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = chapterName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
