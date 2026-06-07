package com.rchdliang.copymanga_neo.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadingCard(
    name: String,
    cover: String?,
    progressInfo: DownloadProgressInfo,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedOverallProgress by animateFloatAsState(
        targetValue = progressInfo.overallProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = cover,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 80.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                val statusText = when (progressInfo.status) {
                    DownloadTaskStatus.DOWNLOADING -> {
                        val currentName = progressInfo.currentChapterName ?: ""
                        "已完成 ${progressInfo.completedChapters}/${progressInfo.totalChapters} 章" +
                                if (currentName.isNotBlank()) "（正在下载：$currentName）" else ""
                    }

                    DownloadTaskStatus.COMPLETED -> "下载完成 ${progressInfo.completedChapters}/${progressInfo.totalChapters} 章"
                    DownloadTaskStatus.ERROR -> "已完成 ${progressInfo.completedChapters}/${progressInfo.totalChapters} 章（部分出错）"
                    else -> "等待中..."
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "取消下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LinearWavyProgressIndicator(
            progress = { animatedOverallProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            color = when (progressInfo.status) {
                DownloadTaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                DownloadTaskStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${(progressInfo.overallProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
