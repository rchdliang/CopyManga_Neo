package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

private enum class ChapterManageVisualState {
    Normal,
    Downloaded,
    Downloading,
    WillDownload,
    WillDeleteLocal
}

private fun resolveChapterManageVisualState(
    status: ChapterDownloadStatus?,
    selected: Boolean
): ChapterManageVisualState {
    return when (status) {
        ChapterDownloadStatus.DOWNLOADED -> {
            if (selected) ChapterManageVisualState.Downloaded
            else ChapterManageVisualState.WillDeleteLocal
        }

        ChapterDownloadStatus.WAITING,
        ChapterDownloadStatus.DOWNLOADING -> {
            if (selected) ChapterManageVisualState.Downloading
            else ChapterManageVisualState.Normal
        }

        else -> {
            if (selected) ChapterManageVisualState.WillDownload
            else ChapterManageVisualState.Normal
        }
    }
}

@Composable
fun ChapterItem(
    chapter: ChapterInfo,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    downloadStatus: ChapterDownloadStatus? = null,
    downloadProgress: Float? = null,
    isReadCompleted: Boolean = false
) {
    ChapterTitleItem(
        chapter.name, onClick, onLongPress, modifier, isSelected,
        isSelectionMode, downloadStatus, downloadProgress, isReadCompleted
    )
}

/** 漫画章节与小说分卷共用；章名原文与阅读、下载状态分别绘制。 */
@Composable
fun ChapterTitleItem(
    name: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    downloadStatus: ChapterDownloadStatus? = null,
    downloadProgress: Float? = null,
    isReadCompleted: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    val visualState = if (isSelectionMode) {
        resolveChapterManageVisualState(downloadStatus, isSelected)
    } else {
        null
    }

    val bgColor = when {
        visualState == ChapterManageVisualState.Normal ->
            MaterialTheme.colorScheme.surface

        visualState == ChapterManageVisualState.Downloaded ->
            MaterialTheme.colorScheme.secondaryContainer

        visualState == ChapterManageVisualState.Downloading ->
            MaterialTheme.colorScheme.tertiaryContainer

        visualState == ChapterManageVisualState.WillDownload ->
            MaterialTheme.colorScheme.primaryContainer

        visualState == ChapterManageVisualState.WillDeleteLocal ->
            MaterialTheme.colorScheme.errorContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer
        downloadStatus == ChapterDownloadStatus.WAITING -> MaterialTheme.colorScheme.secondaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiaryContainer
        downloadStatus == ChapterDownloadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        visualState == ChapterManageVisualState.Normal ->
            MaterialTheme.colorScheme.onSurface

        visualState == ChapterManageVisualState.Downloaded ->
            MaterialTheme.colorScheme.onSecondaryContainer

        visualState == ChapterManageVisualState.Downloading ->
            MaterialTheme.colorScheme.onTertiaryContainer

        visualState == ChapterManageVisualState.WillDownload ->
            MaterialTheme.colorScheme.onPrimaryContainer

        visualState == ChapterManageVisualState.WillDeleteLocal ->
            MaterialTheme.colorScheme.onErrorContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.onPrimaryContainer
        downloadStatus == ChapterDownloadStatus.WAITING -> MaterialTheme.colorScheme.onSecondaryContainer
        downloadStatus == ChapterDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.onTertiaryContainer
        downloadStatus == ChapterDownloadStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val marker = when (visualState) {
        ChapterManageVisualState.Downloaded -> "\u2713"
        ChapterManageVisualState.Downloading -> "\u25CB"
        ChapterManageVisualState.WillDownload -> "\u2193"
        ChapterManageVisualState.WillDeleteLocal -> "\u00D7"
        else -> null
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .semantics { if (isReadCompleted) stateDescription = "已读完" }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = metadataText(name),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isReadCompleted) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(16.dp)
                    .height(2.dp)
                    .background(contentColor.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            )
        }

        if (marker != null) {
            Text(
                text = marker,
                color = contentColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            )
        }

        if (!isSelectionMode) {
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
}

private val previewChapter = ChapterInfo(
    index = 128,
    uuid = "preview-chapter",
    count = 32,
    ordered = 128,
    size = 2048,
    name = "第 128 话",
    comicId = "preview-comic-id",
    comicPathWord = "preview-comic",
    groupId = "preview-group",
    groupPathWord = "default",
    type = 0,
    news = null,
    datetimeCreated = "2026-08-20",
    prevChapter = "chapter-127",
    nextChapter = "chapter-129"
)

@Preview(showBackground = true, name = "组件 - 章节下载状态", widthDp = 220)
@Composable
private fun ChapterDownloadStatesPreview() {
    CopyManga_NeoTheme {
        Column(Modifier.padding(8.dp)) {
            ChapterItem(
                chapter = previewChapter,
                isReadCompleted = true,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
            ChapterItem(
                chapter = previewChapter.copy(name = "第 127 话"),
                downloadStatus = ChapterDownloadStatus.DOWNLOADING,
                downloadProgress = 0.62f,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
            ChapterItem(
                chapter = previewChapter.copy(name = "第 126 话"),
                isReadCompleted = true,
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
            ChapterItem(
                chapter = previewChapter.copy(name = "第 125 话"),
                downloadStatus = ChapterDownloadStatus.ERROR,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, name = "组件 - 已读原章名与独立下载标记", widthDp = 110)
@Composable
private fun ChapterReadStatusPreview() {
    CopyManga_NeoTheme {
        Column(Modifier.padding(8.dp)) {
            ChapterItem(
                previewChapter.copy(name = "第299话"), {}, {}, isReadCompleted = true,
                modifier = Modifier.fillMaxWidth()
            )
            ChapterItem(
                previewChapter.copy(name = "第298话"),
                {},
                {},
                isReadCompleted = true,
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, name = "组件 - 章节管理状态", widthDp = 220)
@Composable
private fun ChapterManageStatesPreview() {
    CopyManga_NeoTheme {
        Column(Modifier.padding(8.dp)) {
            ChapterItem(
                chapter = previewChapter,
                isSelectionMode = true,
                isSelected = true,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
            ChapterItem(
                chapter = previewChapter.copy(name = "第 127 话"),
                isSelectionMode = true,
                isSelected = false,
                downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                onClick = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
