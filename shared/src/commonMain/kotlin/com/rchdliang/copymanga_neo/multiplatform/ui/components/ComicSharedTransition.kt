package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 页面内小说转场复用导航的共享布局，但使用独立的可见性与选择状态。 */
@Composable
internal fun currentBookSharedTransitionScope(): SharedTransitionScope? =
    LocalSharedTransitionScope.current

@Composable
internal fun currentBookTransitionVisibility(): AnimatedVisibilityScope? =
    LocalAnimatedVisibilityScope.current

@Composable
internal fun currentBookTransitionSelection(): ComicSharedTransitionState? =
    LocalComicSharedTransitionState.current
private val LocalAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
private val LocalComicSharedTransitionState =
    staticCompositionLocalOf<ComicSharedTransitionState?> { null }

private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

data class ComicTransitionData(
    val transitionKey: String,
    val pathWord: String,
    val cover: String?,
    val name: String,
    val subtitle: String?
)

@Stable
class ComicSharedTransitionState {
    var selectedComic by mutableStateOf<ComicTransitionData?>(null)
        private set

    fun select(data: ComicTransitionData) {
        selectedComic = data
    }
}

@Composable
fun rememberComicSharedTransitionState(): ComicSharedTransitionState =
    remember { ComicSharedTransitionState() }

/**
 * 为当前 NavHost 目的地提供共享元素作用域。
 */
@Composable
fun ComicSharedTransitionProvider(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    transitionState: ComicSharedTransitionState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedTransitionScope,
        LocalAnimatedVisibilityScope provides animatedVisibilityScope,
        LocalComicSharedTransitionState provides transitionState,
        content = content
    )
}

/**
 * 将漫画卡片的背景层与对应详情页背景连接为 Material 容器变换。
 * Preview 或不在共享转场作用域中时自动退化为普通 Modifier。
 */
@Composable
fun Modifier.comicContainerSharedBounds(pathWord: String, hideOnExit: Boolean = false): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return this

    // 详情退场时立即移除背景的共享边界；封面仍使用独立的 sharedElement。
    if (hideOnExit && animatedVisibilityScope.transition.targetState == EnterExitState.PostExit) {
        return graphicsLayer { alpha = 0f }
    }

    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState("comic-container:$pathWord"),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 200, easing = emphasizedDecelerate)
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 100, easing = emphasizedAccelerate)
            ),
            renderInOverlayDuringTransition = false
        )
    }
}

/** 将卡片封面移动并缩放到详情页封面位置。 */
@Composable
fun Modifier.comicCoverSharedElement(pathWord: String): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return this

    return with(sharedTransitionScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState("comic-cover:$pathWord"),
            animatedVisibilityScope = animatedVisibilityScope,
            zIndexInOverlay = 1f
        )
    }
}

/** 在执行导航前保存目标封面，使详情数据尚未返回时也能完成共享元素匹配。 */
@Composable
fun rememberComicSharedTransitionClick(
    transitionKey: String,
    pathWord: String,
    cover: String?,
    name: String,
    subtitle: String?,
    onClick: () -> Unit
): () -> Unit {
    val transitionState = LocalComicSharedTransitionState.current
    val currentOnClick by rememberUpdatedState(onClick)

    return remember(transitionState, transitionKey, pathWord, cover, name, subtitle) {
        {
            transitionState?.select(
                ComicTransitionData(
                    transitionKey = transitionKey,
                    pathWord = pathWord,
                    cover = cover,
                    name = name,
                    subtitle = subtitle
                )
            )
            currentOnClick()
        }
    }
}

@Composable
fun currentComicTransitionData(pathWord: String): ComicTransitionData? =
    LocalComicSharedTransitionState.current
        ?.selectedComic
        ?.takeIf { it.pathWord == pathWord }

@Composable
fun isComicSharedTransitionActive(pathWord: String): Boolean =
    LocalSharedTransitionScope.current?.isTransitionActive == true &&
            currentComicTransitionData(pathWord) != null
