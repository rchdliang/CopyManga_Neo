package com.rchdliang.copymanga_neo.multiplatform.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.multiplatform.ui.components.TopicCoverImage
import com.rchdliang.copymanga_neo.multiplatform.ui.components.comicContainerSharedBounds
import com.rchdliang.copymanga_neo.multiplatform.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.multiplatform.ui.components.rememberComicSharedTransitionClick
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 专题头部：宽屏图文并排，窄屏按封面、标题和简介顺序排列。 */
@Composable
internal fun TopicDetailHeader(topic: ComicTopic) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            if (maxWidth >= 600.dp && !topic.cover.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopicBanner(topic, Modifier.weight(1f))
                    TopicIntroduction(topic, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (!topic.cover.isNullOrBlank()) TopicBanner(topic, Modifier.fillMaxWidth())
                    TopicIntroduction(topic, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TopicBanner(topic: ComicTopic, modifier: Modifier) {
    TopicCoverImage(
        topic = topic,
        contentDescription = null,
        modifier = modifier
            .aspectRatio(1920f / 412f)
            .clip(MaterialTheme.shapes.large)
    )
}

@Composable
private fun TopicIntroduction(topic: ComicTopic, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            topic.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        topic.brief?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 专题漫画使用独立文字区，保留完整封面与详情页共享转场。 */
@Composable
internal fun TopicComicCard(
    comic: ComicSummary,
    transitionKey: String?,
    onClick: () -> Unit
) {
    val visibility = com.rchdliang.copymanga_neo.multiplatform.ui.components.comicVisibility(comic.pathWord)
    val pending =
        visibility == com.rchdliang.copymanga_neo.domain.model.ComicCoverVisibility.PENDING
    val author = comic.author?.joinToString(" / ") { it.name }?.takeIf { it.isNotBlank() }
        ?: comic.authorAlias
    val click = if (transitionKey != null) {
        rememberComicSharedTransitionClick(
            transitionKey = transitionKey,
            pathWord = comic.pathWord,
            cover = comic.cover,
            name = comic.name,
            subtitle = author,
            onClick = onClick
        )
    } else onClick
    Card(
        onClick = { if (!pending) click() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (transitionKey != null) Modifier.comicContainerSharedBounds(transitionKey)
                else Modifier
            )
    ) {
        com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicCoverImage(
            path = comic.pathWord, model = comic.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .then(
                    if (transitionKey != null) Modifier.comicCoverSharedElement(transitionKey)
                    else Modifier
                )
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (pending) "" else comic.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (pending) "" else author.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
/** 本地资源预览覆盖手机、平板及深色主题，不请求网络。 */
@Preview(name = "专题 - 手机", widthDp = 400, heightDp = 880)
@Preview(name = "专题 - 平板", widthDp = 1000, heightDp = 800)
@Composable
private fun TopicDetailPreview() {
    TopicPreviewContent(darkTheme = false)
}

@Preview(name = "专题 - 深色", widthDp = 400, heightDp = 880)
@Composable
private fun TopicDetailDarkPreview() {
    TopicPreviewContent(darkTheme = true)
}

@Composable
private fun TopicPreviewContent(darkTheme: Boolean) {
    CopyManga_NeoTheme(darkTheme = darkTheme) {
        Surface {
            BoxWithConstraints {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(
                        ((maxWidth - 16.dp) / 152.dp).toInt().coerceAtLeast(2)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TopicDetailHeader(
                            ComicTopic(
                                "preview", "夏番漫畫2026 必看熱門漫畫推薦第41期", "preview",
                                "本次专题汇集夏季新番动画的原作漫画及相关衍生作品。看番不过瘾，来看看原作漫画吧。"
                            )
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "专题漫画", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(8) { index ->
                        TopicComicCard(
                            ComicSummary(
                                if (index % 2 == 0) "正相反的你与我" else "魔法少女奈叶 EXCEEDS",
                                "preview-$index", null, listOf(Author("示例作者", null)),
                                null, null, null, null, null
                            ),
                            transitionKey = null,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}
