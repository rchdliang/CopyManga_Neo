package com.rchdliang.copymanga_neo.multiplatform.ui.components

import com.rchdliang.copymanga_neo.multiplatform.ui.components.RouteImageScope
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeImageModel
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewImage

data class BookDetailAuthor(val name: String, val path: String = "")

/** 漫画与小说详情共用封面尺寸，宽屏使用更大的封面。 */
@Composable
fun bookDetailCoverWidth(): Dp {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return if (width >= 840.dp) 180.dp else 144.dp
}

/** 详情信息区；共享转场与标题测量由调用方传入，业务操作留在各自页面。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookDetailHeader(
    title: String,
    cover: String?,
    authors: List<BookDetailAuthor>,
    status: String,
    updated: String,
    modifier: Modifier = Modifier,
    coverWidth: Dp = bookDetailCoverWidth(),
    coverModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    popularity: String? = null,
    onAuthor: ((String) -> Unit)? = null,
    comicPathWord: String? = null,
    originalWorks: List<com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork> = emptyList(),
    onOriginalWork: ((com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork) -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val imageModifier = coverModifier
            .width(coverWidth)
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium)
        if (comicPathWord != null) {
            ComicCoverImage(
                comicPathWord, cover, title, imageModifier,
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.BrokenImage)
            )
        } else if (LocalInspectionMode.current && !cover.isNullOrBlank()) {
            PreviewImage(
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = imageModifier
            )
        } else if (cover.isNullOrBlank()) {
            BookCoverUnavailable("暂无封面", imageModifier)
        } else {
            RouteImageScope {
                SubcomposeAsyncImage(
                    imageLoader = requireAppImageLoader(),
                    model = routeImageModel(cover), contentDescription = title,
                    contentScale = ContentScale.Crop, modifier = imageModifier
                ) {
                    when (painter.state.collectAsState().value) {
                        is AsyncImagePainter.State.Loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        is AsyncImagePainter.State.Error -> BookCoverUnavailable(
                            "封面加载失败",
                            Modifier.fillMaxSize()
                        )

                        is AsyncImagePainter.State.Success -> Image(
                            painter = painter, contentDescription = title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )

                        else -> Unit
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                modifier = titleModifier,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            BookDetailInfoRow(Icons.Default.Person, "作者") {
                val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                Text(
                    buildAnnotatedString {
                        if (authors.isEmpty()) append("作者信息暂缺")
                        authors.forEachIndexed { index, author ->
                            if (index > 0) append(", ")
                            if (onAuthor != null && author.path.isNotBlank()) {
                                withLink(LinkAnnotation.Clickable(author.path, linkStyle) {
                                    onAuthor(author.path)
                                }) { append(author.name) }
                            } else append(author.name)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val originalWorkNames = originalWorks.filter { it.name.isNotBlank() }.distinct()
            if (originalWorkNames.isNotEmpty()) {
                BookDetailInfoRow(Icons.AutoMirrored.Filled.MenuBook, "原作") {
                    Text(
                        buildAnnotatedString {
                            append("原作：")
                            val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                            originalWorkNames.forEachIndexed { index, work ->
                                if (index > 0) append("、")
                                if (onOriginalWork != null && !work.pathWord.isNullOrBlank()) {
                                    withLink(LinkAnnotation.Clickable(work.pathWord, linkStyle) {
                                        onOriginalWork(work)
                                    }) { append(work.name.trim()) }
                                } else append(work.name.trim())
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BookDetailInfoRow(Icons.Default.Pending, "状态") {
                Text(
                    status.ifBlank { "状态信息暂缺" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BookDetailInfoRow(Icons.Default.Update, "最后更新") {
                Text(
                    updated.ifBlank { "更新信息暂缺" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            popularity?.let {
                BookDetailInfoRow(Icons.Default.LocalFireDepartment, "热度") {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCoverUnavailable(label: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookDetailInfoRow(icon: ImageVector, label: String, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 简介默认单行；展开状态按作品保存，尺寸变化沿用 Compose 动画。 */
@Composable
fun BookDetailBrief(bookKey: String, brief: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(bookKey) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (expanded) "简介已展开" else "简介已收起" }
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "收起简介" else "展开简介"
            ) { expanded = !expanded }
            .animateContentSize()
            .padding(vertical = 6.dp)
    ) {
        Text(
            brief.trim().ifBlank { "简介暂缺" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
