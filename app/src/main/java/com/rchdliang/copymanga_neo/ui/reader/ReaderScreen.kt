package com.rchdliang.copymanga_neo.ui.reader

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/**
 * 阅读器屏幕
 * 显示章节图片列表，支持左右翻页和跨章节跳转
 */
@Composable
fun ReaderScreen(
    chapterPathWord: String,
    comicPathWord: String,
    chapterName: String,
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
        viewModel.loadChapter(comicPathWord, chapterPathWord)
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

                // 边界滑动跳转逻辑
                var lastOverscrollTime by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPageOffsetFraction }
                        .collect { offset ->
                            val currentTime = System.currentTimeMillis()
                            // 检测右侧越界（去下一章）
                            if (pagerState.currentPage == images.size - 1 && offset > 0.1f) {
                                if (state.content.nextUuid == null) {
                                    if (currentTime - lastOverscrollTime > 2000) {
                                        Toast.makeText(
                                            context,
                                            "已经是最后一章了",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        lastOverscrollTime = currentTime
                                    }
                                } else {
                                    if (currentTime - lastOverscrollTime < 1000 && lastOverscrollTime != 0L) {
                                        viewModel.loadNextChapter()
                                        lastOverscrollTime = 0L
                                    } else if (currentTime - lastOverscrollTime > 2000) {
                                        Toast.makeText(
                                            context,
                                            "本章已完，再次滑动进入下一章",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        lastOverscrollTime = currentTime
                                    }
                                }
                            }
                            // 检测左侧越界（去上一章）
                            else if (pagerState.currentPage == 0 && offset < -0.1f) {
                                if (state.content.prevUuid == null) {
                                    if (currentTime - lastOverscrollTime > 2000) {
                                        Toast.makeText(
                                            context,
                                            "已经是第一章了",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        lastOverscrollTime = currentTime
                                    }
                                } else {
                                    if (currentTime - lastOverscrollTime < 1000 && lastOverscrollTime != 0L) {
                                        viewModel.loadPrevChapter()
                                        lastOverscrollTime = 0L
                                    } else if (currentTime - lastOverscrollTime > 2000) {
                                        Toast.makeText(
                                            context,
                                            "再次滑动返回上一章",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        lastOverscrollTime = currentTime
                                    }
                                }
                            }
                        }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
//                    overscrollEffect =
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
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chapterName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${pagerState.currentPage + 1} /${images.size}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
