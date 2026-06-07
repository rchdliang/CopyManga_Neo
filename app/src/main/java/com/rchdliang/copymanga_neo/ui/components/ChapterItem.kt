package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChapterItem(
    chapter: ChapterDto,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    downloadStatus: ChapterDownloadStatus? = null,
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    val bgColor = when {
        isSelectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer
        downloadStatus == ChapterDownloadStatus.WAITING -> MaterialTheme.colorScheme.secondaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiaryContainer
        downloadStatus == ChapterDownloadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        isSelectionMode && isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.onPrimaryContainer
        downloadStatus == ChapterDownloadStatus.WAITING -> MaterialTheme.colorScheme.onSecondaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.onTertiaryContainer
        downloadStatus == ChapterDownloadStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isSelectionMode && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(1.dp)
                )
            }
        }

        when (downloadStatus) {
            ChapterDownloadStatus.DOWNLOADING -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(14.dp)
                ) {
                    CircularWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(14.dp),
                        trackColor = Color.Transparent
                    )
                }
            }

            ChapterDownloadStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "下载失败",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(12.dp)
                )
            }

            ChapterDownloadStatus.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已下载",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(12.dp)
                )
            }

            else -> {}
        }
    }
}
