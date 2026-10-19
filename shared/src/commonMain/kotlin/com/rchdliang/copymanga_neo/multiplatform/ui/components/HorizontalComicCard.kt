package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ComicCoverVisibility
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.multiplatform.ui.components.toCompactLocale

/** 左侧完整保留原卡片，右侧占两个封面宽度。 */
@Composable
fun HorizontalComicCard(
    comic: ComicSummary, transitionKey: String, onClick: () -> Unit, statusText: String? = null
) {
    val visibility = comicVisibility(comic.pathWord)
    val click = rememberComicSharedTransitionClick(
        transitionKey, comic.pathWord, comic.cover,
        comic.name, comic.author?.firstOrNull()?.name, onClick
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        val coverWidth = maxWidth / 3
        val height = coverWidth * 4 / 3
        if (visibility == ComicCoverVisibility.PENDING) {
            ComicPending(
                comic.pathWord, Modifier
                    .fillMaxWidth()
                    .height(height)
            )
        } else Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(onClick = click)
        ) {
            ComicCard(
                comic.name, comic.cover, click, Modifier.width(coverWidth),
                subtitle = comic.author?.firstOrNull()?.name,
                statusText = statusText,
                comicPathWord = comic.pathWord, sharedTransitionKey = transitionKey
            )
            ComicCardInformation(
                comic.pathWord, Modifier
                    .weight(1f)
                    .fillMaxHeight(), comic
            )
        }
    }
}

/** 不重复显示字段标题，窄屏时元信息自然换行。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComicCardInformation(
    path: String,
    modifier: Modifier = Modifier,
    summary: ComicSummary? = null
) {
    val context = LocalComicPresentation.current
    val info = context?.state?.metadata?.get(path)
    val error = context?.state?.failures?.get(path)
    BookCardInformation(
        modifier = modifier, themes = (info?.themes ?: summary?.theme).orEmpty().map { it.name },
        brief = info?.brief, popular = summary?.popular ?: info?.popular,
        updated = summary?.lastUpdate ?: info?.lastUpdate,
        loading = info == null && error == null, error = error.takeIf { info == null },
        onRetry = { context?.retry?.invoke(path) }
    )
}

/** 漫画和轻小说共用预览排版，数据与请求保持各自类型和会话。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BookCardInformation(
    modifier: Modifier = Modifier,
    themes: List<String>,
    brief: String?,
    popular: Int?,
    updated: String?,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), maxLines = 2
        ) {
            themes.forEach { theme ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        metadataText(theme), Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (loading || error != null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.CenterStart
            ) {
                if (error != null) TextButton(onClick = onRetry) {
                    Text("重试", color = MaterialTheme.colorScheme.primary)
                } else LoadingIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
            }
        } else Text(metadataText(brief?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
            ?: "暂无简介"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            popular?.let { CardMetadata(Icons.Outlined.LocalFireDepartment, it.toCompactLocale()) }
            updated?.takeIf { it.isNotBlank() }
                ?.let { CardMetadata(Icons.Outlined.Schedule, "${it.take(10)} 更新") }
        }
    }
}

@Composable
private fun CardMetadata(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, maxLines = 1
        )
    }
}

@Preview(name = "横向漫画卡片", showBackground = true, widthDp = 360)
@Composable
private fun HorizontalComicCardPreview() {
    CopyManga_NeoTheme {
        HorizontalComicCard(
            ComicSummary(
                "星海漫游指南", "preview", null, null, 1286000,
                null, null, null, null, "2026-10-18"
            ), "preview", {})
    }
}
