package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import com.rchdliang.copymanga_neo.domain.model.ComicCoverVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/**
 * 通用漫画卡片组件，支持标题、副标题、可选状态文本、长按多选
 *
 * @param name 标题
 * @param cover 封面 URL
 * @param subtitle 副标题（如作者、章节数）
 * @param statusText 可选的状态文本（如阅读进度/最新章节），为 null 时不显示
 * @param statusColor 状态文本颜色，默认使用主题 onSurfaceVariant
 * @param isSelected 是否处于选中状态（右上角显示对勾）
 * @param sharedTransitionKey 当前卡片实例的唯一共享转场 key
 * @param comicPathWord 漫画路由 pathWord，用于详情页匹配导航快照
 * @param onClick 点击回调
 * @param onLongClick 长按回调，为 null 时不处理长按
 */
@Composable
fun ComicCard(
    name: String,
    cover: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isSelected: Boolean = false,
    sharedTransitionKey: String? = null,
    comicPathWord: String? = sharedTransitionKey,
    onLongClick: (() -> Unit)? = null,
    enableExpansion: Boolean = false,
    floating: Boolean = false,
    allowPendingContent: Boolean = false,
    novelPreview: com.rchdliang.copymanga_neo.domain.model.NovelBook? = null
) {
    val visibility = comicVisibility(comicPathWord)
    val presentation = LocalComicPresentation.current
    val instance = remember { Any().toString() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val latestPresentation by androidx.compose.runtime.rememberUpdatedState(presentation)
    androidx.compose.runtime.DisposableEffect(instance) {
        onDispose {
            if (!floating && latestPresentation?.expandedId == instance) latestPresentation?.dismiss?.invoke()
        }
    }
    val blocked = visibility == ComicCoverVisibility.BLURRED
    val transitionOnClick = if (sharedTransitionKey != null) {
        rememberComicSharedTransitionClick(
            transitionKey = sharedTransitionKey,
            pathWord = comicPathWord ?: sharedTransitionKey,
            cover = cover,
            name = name,
            subtitle = subtitle,
            onClick = onClick
        )
    } else {
        onClick
    }
    val containerTransitionModifier = if (sharedTransitionKey != null) {
        Modifier.comicContainerSharedBounds(sharedTransitionKey)
    } else {
        Modifier
    }
    val coverTransitionModifier = if (sharedTransitionKey != null) {
        Modifier.comicCoverSharedElement(sharedTransitionKey)
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .onGloballyPositioned {
                val next = Rect(
                    it.positionInRoot(),
                    androidx.compose.ui.geometry.Size(
                        it.size.width.toFloat(),
                        it.size.height.toFloat()
                    )
                )
                if (!floating && bounds != Rect.Zero && next != bounds && presentation?.expandedId == instance) {
                    presentation.dismiss()
                }
                bounds = next
            }
            .graphicsLayer { if (!floating && presentation?.expandedId == instance) alpha = 0f }
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = allowPendingContent || visibility != ComicCoverVisibility.PENDING,
                onClick = transitionOnClick,
                onLongClick = if (enableExpansion && onLongClick == null && presentation != null && comicPathWord != null) {
                    {
                        presentation.expand(
                            ComicExpansion(
                                instance, comicPathWord, name, cover,
                                subtitle, statusText, bounds, transitionOnClick, novelPreview
                            )
                        )
                    }
                } else onLongClick
            )
    ) {
        if (visibility == ComicCoverVisibility.PENDING && !allowPendingContent) {
            ComicPending(comicPathWord, Modifier.fillMaxSize())
            return@Box
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
            modifier = containerTransitionModifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.large)
        ) {}

        ComicCoverImage(
            path = comicPathWord,
            model = ImageRequest
                .Builder(LocalPlatformContext.current)
                .crossfade(sharedTransitionKey == null)
                .data(cover)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = coverTransitionModifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large)
        )

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = name,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.takeUnless { blocked }?.let { sub ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                statusText?.takeUnless { blocked }?.let { status ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "组件 - 漫画卡片", widthDp = 180)
@Composable
private fun ComicCardPreview() {
    CopyManga_NeoTheme {
        ComicCard(
            name = "星海漫游指南",
            cover = null,
            subtitle = "示例作者",
            statusText = "有新内容：第 42 话",
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true, name = "组件 - 漫画卡片已选中", widthDp = 180)
@Composable
private fun SelectedComicCardPreview() {
    CopyManga_NeoTheme {
        ComicCard(
            name = "雨夜侦探社",
            cover = null,
            subtitle = "已下载 18/24 章",
            isSelected = true,
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}
