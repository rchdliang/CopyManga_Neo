package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 阅读器屏幕
 * 显示章节图片列表
 */
@Composable
fun ReaderScreen(
    chapterPathWord: String,
    comicPathWord: String,
    chapterName: String,
    onNavigateBack: () -> Unit
) {
    // TODO: 实现 ViewModel 加载章节图片
    // 基础版本：显示占位内容
    var isLoading by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf(emptyList<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chapterPathWord) {
        // TODO: 从 ViewModel 加载图片
        // 模拟加载
        isLoading = false
        images = emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            errorMessage != null -> {
                Text(
                    text = "加载失败：$errorMessage",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            images.isEmpty() -> {
                // 基础版本：显示提示信息
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "章节：$chapterName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "图片加载功能待实现",
                        fontSize = 14.sp
                    )
                }
            }

            else -> {
                // 显示图片列表
                ImageList(
                    images = images,
                    chapterName = chapterName
                )
            }
        }
    }
}

@Composable
private fun ImageList(
    images: List<String>,
    chapterName: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = chapterName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        items(images) { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "章节图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
