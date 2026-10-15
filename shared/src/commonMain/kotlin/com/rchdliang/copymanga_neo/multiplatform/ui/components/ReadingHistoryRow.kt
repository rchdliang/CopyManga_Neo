package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage

/** 云端浏览记录、本地历史及书签共用同一横向封面行。 */
@Composable
fun ReadingHistoryRow(
    title: String,
    cover: String?,
    authors: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    trailingContent: @Composable () -> Unit = {},
    supportingContent: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = containerColor, modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(
                    Modifier.size(72.dp, 96.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    if (cover.isNullOrBlank()) {
                        Text(
                            "暂无封面", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else PreviewAwareAsyncImage(
                        model = cover, contentDescription = title,
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(Icons.Outlined.BrokenImage),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    authors.ifBlank { "作者信息暂缺" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supportingContent()
            }
            trailingContent()
        }
    }
}
