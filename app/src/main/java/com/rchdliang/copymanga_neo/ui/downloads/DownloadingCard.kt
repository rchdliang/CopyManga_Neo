package com.rchdliang.copymanga_neo.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadingCard(
    name: String,
    cover: String?,
    progressInfo: DownloadProgressInfo,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val animatedOverallProgress by animateFloatAsState(
        targetValue = progressInfo.overallProgress.coerceIn(0f, 1f),
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    val statusColor = when (progressInfo.status) {
        DownloadTaskStatus.ERROR -> MaterialTheme.colorScheme.error
        DownloadTaskStatus.PAUSED_BY_RISK -> MaterialTheme.colorScheme.error
        DownloadTaskStatus.PAUSED -> MaterialTheme.colorScheme.outline
        DownloadTaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val stateText = when (progressInfo.status) {
        DownloadTaskStatus.IDLE -> "空闲"
        DownloadTaskStatus.WAITING -> "等待中"
        DownloadTaskStatus.REQUESTING_PAGE -> "下载中"
        DownloadTaskStatus.DOWNLOADING -> "下载中"
        DownloadTaskStatus.PAUSED -> "已暂停"
        DownloadTaskStatus.PAUSED_BY_RISK -> "风控暂停"
        DownloadTaskStatus.COMPLETED -> "已完成"
        DownloadTaskStatus.ERROR -> "出错"
    }

    val chapterText = when (progressInfo.status) {
        DownloadTaskStatus.COMPLETED -> "下载完成 ${progressInfo.completedChapters}/${progressInfo.totalChapters} 章"

        else -> {
            val current = progressInfo.currentChapterIndex.takeIf { it > 0 }
            if (current != null && progressInfo.totalChapters > 0) {
                "第 $current/${progressInfo.totalChapters} 章 · 已完成 ${progressInfo.completedChapters} 章"
            } else {
                "已完成 ${progressInfo.completedChapters}/${progressInfo.totalChapters} 章"
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = cover,
                contentDescription = name,
                modifier = Modifier
                    .width(96.dp)
                    .height(132.dp)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 132.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(10.dp))

                    DownloadStatusChip(text = stateText, color = statusColor)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = chapterText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!progressInfo.errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.width(4.dp))

                        Text(
                            text = progressInfo.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = progressInfo.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (progressInfo.status != DownloadTaskStatus.COMPLETED) {
                        Text(
                            text = "${(progressInfo.overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (progressInfo.status == DownloadTaskStatus.DOWNLOADING) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = formatDownloadSpeed(progressInfo.speedBytesPerSecond),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


                if (progressInfo.status != DownloadTaskStatus.COMPLETED) {
                    Spacer(Modifier.height(8.dp))

                    if (
                        progressInfo.status == DownloadTaskStatus.PAUSED ||
                        progressInfo.status == DownloadTaskStatus.PAUSED_BY_RISK ||
                        progressInfo.status == DownloadTaskStatus.ERROR
                    ) {
                        LinearProgressIndicator(
                            progress = { progressInfo.overallProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.small),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            color = statusColor
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            progress = { animatedOverallProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.small),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (progressInfo.status) {
                    DownloadTaskStatus.ERROR -> {
                        if (onRetry != null) {
                            IconButton(onClick = onRetry) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "重试",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    DownloadTaskStatus.COMPLETED -> {}
                    DownloadTaskStatus.PAUSED,
                    DownloadTaskStatus.PAUSED_BY_RISK -> {
                        IconButton(onClick = onPauseResume) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "继续",
                                tint = if (progressInfo.status == DownloadTaskStatus.PAUSED_BY_RISK) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    else -> {
                        IconButton(onClick = onPauseResume) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "暂停",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusChip(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 KB/s"

    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0

    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", kb)
    }
}

@Preview(showBackground = true, name = "下载中", widthDp = 1000)
@Composable
private fun DownloadingCardPreview() {
    CopyManga_NeoTheme {
        val previewInfo = DownloadProgressInfo(
            comicPathWord = "test",
            name = "小手指君别碰我",
            cover = null,
            completedChapters = 2,
            totalChapters = 12,
            overallProgress = 0.38f,
            currentChapterIndex = 3,
            currentChapterName = "第3话",
            currentChapterProgress = 0.5f,
            status = DownloadTaskStatus.DOWNLOADING,
            statusText = "正在下载图片 14/32",
            downloadedImages = 14,
            totalImages = 32,
            speedBytesPerSecond = 603 * 1024
        )
        DownloadingCard(
            name = previewInfo.name,
            cover = previewInfo.cover,
            progressInfo = previewInfo,
            isPaused = false,
            onPauseResume = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "风控暂停", widthDp = 1000)
@Composable
private fun DownloadingCardPausedByRiskPreview() {
    CopyManga_NeoTheme {
        val previewInfo = DownloadProgressInfo(
            comicPathWord = "test",
            name = "小手指君别碰我",
            cover = null,
            completedChapters = 1,
            totalChapters = 12,
            overallProgress = 0.45f,
            currentChapterIndex = 2,
            currentChapterName = "第2话",
            currentChapterProgress = 0.6f,
            status = DownloadTaskStatus.PAUSED_BY_RISK,
            statusText = "触发风控，已暂停下载",
            errorMessage = "服务器返回 429\n请稍后重试或检查登录状态",
            downloadedImages = 12,
            totalImages = 30,
            speedBytesPerSecond = 0L
        )
        DownloadingCard(
            name = previewInfo.name,
            cover = previewInfo.cover,
            progressInfo = previewInfo,
            isPaused = true,
            onPauseResume = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "出错", widthDp = 1000)
@Composable
private fun DownloadingCardErrorPreview() {
    CopyManga_NeoTheme {
        val previewInfo = DownloadProgressInfo(
            comicPathWord = "test",
            name = "测试漫画",
            cover = null,
            completedChapters = 0,
            totalChapters = 5,
            overallProgress = 0f,
            currentChapterIndex = 1,
            currentChapterName = "第1话",
            currentChapterProgress = 0f,
            status = DownloadTaskStatus.ERROR,
            statusText = "章节下载失败",
            errorMessage = "网络连接失败：timeout",
            downloadedImages = 0,
            totalImages = 0,
            speedBytesPerSecond = 0L
        )
        DownloadingCard(
            name = previewInfo.name,
            cover = previewInfo.cover,
            progressInfo = previewInfo,
            isPaused = false,
            onPauseResume = {},
            onCancel = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "已完成", widthDp = 1000)
@Composable
private fun DownloadingCardCompletedPreview() {
    CopyManga_NeoTheme {
        val previewInfo = DownloadProgressInfo(
            comicPathWord = "test",
            name = "已完成的漫画",
            cover = null,
            completedChapters = 12,
            totalChapters = 12,
            overallProgress = 1f,
            currentChapterIndex = 12,
            currentChapterName = null,
            currentChapterProgress = 1f,
            status = DownloadTaskStatus.COMPLETED,
            statusText = "下载完成",
            downloadedImages = 0,
            totalImages = 0,
            speedBytesPerSecond = 0L
        )
        DownloadingCard(
            name = previewInfo.name,
            cover = previewInfo.cover,
            progressInfo = previewInfo,
            isPaused = false,
            onPauseResume = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "请求网页", widthDp = 1000)
@Composable
private fun DownloadingCardRequestingPagePreview() {
    CopyManga_NeoTheme {
        val previewInfo = DownloadProgressInfo(
            comicPathWord = "test",
            name = "正在请求的漫画",
            cover = null,
            completedChapters = 1,
            totalChapters = 12,
            overallProgress = 0.1f,
            currentChapterIndex = 2,
            currentChapterName = "第2话",
            currentChapterProgress = 0f,
            status = DownloadTaskStatus.REQUESTING_PAGE,
            statusText = "正在请求网页",
            downloadedImages = 0,
            totalImages = 0,
            speedBytesPerSecond = 0L
        )
        DownloadingCard(
            name = previewInfo.name,
            cover = previewInfo.cover,
            progressInfo = previewInfo,
            isPaused = false,
            onPauseResume = {},
            onCancel = {}
        )
    }
}
