package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 通用漫画卡片组件，支持标题、副标题、可选状态文本、长按多选
 *
 * @param name 标题
 * @param cover 封面 URL
 * @param subtitle 副标题（如作者、章节数）
 * @param statusText 可选的状态文本（如阅读进度/最新章节），为 null 时不显示
 * @param statusColor 状态文本颜色，默认使用主题 onSurfaceVariant
 * @param isSelected 是否处于选中状态（右上角显示对勾）
 * @param onClick 点击回调
 * @param onLongClick 长按回调，为 null 时不处理长按
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(
    name: String,
    cover: String?,
    subtitle: String? = null,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.large)
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = ImageRequest
                    .Builder(LocalContext.current)
                    .crossfade(true)
                    .data(cover)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
                    subtitle?.let { sub ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    statusText?.let { status ->
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
}
