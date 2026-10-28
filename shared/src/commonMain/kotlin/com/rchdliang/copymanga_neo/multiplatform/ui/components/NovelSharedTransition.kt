package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.multiplatform.ui.search.SearchBookRow

private val LocalNovelScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalNovelVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
private val LocalNovelSelection = staticCompositionLocalOf<ComicSharedTransitionState?> { null }

fun novelTransitionPath(path: String): String = "novel:$path"

/** 首页、小说列表和详情共用布局；独立作用域不改变漫画原有导航动画。 */
@Composable
fun <T> NovelAnimatedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit
) {
    val inheritedScope = LocalNovelScope.current ?: currentBookSharedTransitionScope()
    val selection = LocalNovelSelection.current ?: currentBookTransitionSelection()
    ?: rememberComicSharedTransitionState()
    if (inheritedScope != null) {
        NovelAnimatedContentInScope(
            inheritedScope,
            selection,
            targetState,
            modifier,
            contentKey,
            content
        )
    } else {
        SharedTransitionLayout(modifier) {
            NovelAnimatedContentInScope(this, selection, targetState, Modifier, contentKey, content)
        }
    }
}

@Composable
private fun <T> NovelAnimatedContentInScope(
    scope: SharedTransitionScope,
    selection: ComicSharedTransitionState,
    targetState: T,
    modifier: Modifier,
    contentKey: (T) -> Any?,
    content: @Composable (T) -> Unit
) {
    val parentVisibility = LocalNovelVisibility.current ?: currentBookTransitionVisibility()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "novel-content"
    ) { scene ->
        // 首页进入小说时以内层详情承接外层动画；列表内部切换则使用自身动画。
        val visibility = parentVisibility?.takeIf {
            it.transition.currentState != EnterExitState.Visible ||
                    it.transition.targetState != EnterExitState.Visible
        } ?: this
        CompositionLocalProvider(
            LocalNovelScope provides scope,
            LocalNovelVisibility provides visibility,
            LocalNovelSelection provides selection
        ) { content(scene) }
    }
}

/** 仅小说卡片与详情接入漫画已有的共享元素实现，其他内容继续使用原导航作用域。 */
@Composable
fun NovelSharedTransitionProvider(content: @Composable () -> Unit) {
    val scope = LocalNovelScope.current
    val visibility = LocalNovelVisibility.current
    val selection = LocalNovelSelection.current
    if (scope != null && visibility != null && selection != null) {
        ComicSharedTransitionProvider(scope, visibility, selection, content)
    } else {
        content()
    }
}

/** 来源区分卡片实例，类型前缀避免相同 pathword 与漫画封面碰撞。 */
@Composable
fun NovelTransitionCard(
    book: NovelBook,
    source: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    NovelSharedTransitionProvider {
        if (compact) ComicCard(
            name = displayBookTitle(book.path, book.name),
            cover = book.cover,
            subtitle = book.authors.joinToString(" / ") { it.name }.ifBlank { "作者信息暂缺" },
            statusText = statusText,
            statusColor = statusColor,
            sharedTransitionKey = if (selectionMode) null else "novel:$source:${book.path}",
            comicPathWord = novelTransitionPath(book.path),
            enableExpansion = !selectionMode && onLongClick == null,
            novelPreview = book,
            onClick = onClick,
            isSelected = isSelected,
            onLongClick = onLongClick,
            modifier = modifier
        )
        else SearchBookRow(
            name = displayBookTitle(book.path, book.name), cover = book.cover,
            authors = book.authors.joinToString(" / ") { it.name }.ifBlank { "作者信息暂缺" },
            transitionKey = "novel:$source:${book.path}", pathWord = novelTransitionPath(book.path),
            onClick = onClick
        )
    }
}
