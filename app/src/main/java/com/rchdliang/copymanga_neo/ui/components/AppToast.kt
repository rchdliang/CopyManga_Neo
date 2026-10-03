package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 全局 Toast 状态持有者，提供 showToast 方法以显示自定义 Compose Toast。
 * 通过 [AppToastHost] 创建并提供给整个 App 使用。
 */
class ToastState internal constructor(
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState
) {
    var message by mutableStateOf<String?>(null)
        private set

    private var hideJob: Job? = null

    /**
     * 显示一条 Toast 消息，2 秒后自动消失。
     * 可从任何回调（包括 onClick 等非 Composable 上下文）中调用。
     */
    fun showToast(message: String) {
        hideJob?.cancel()
        this.message = message
        hideJob = scope.launch {
            delay(2000L.milliseconds)
            this@ToastState.message = null
        }
    }

    /**
     * 显示带操作按钮的 Snackbar（基于 Material3 Snackbar）。
     *
     * @param message 消息文本
     * @param actionLabel 操作按钮文字（如"撤回"）
     * @param onAction 用户点击操作按钮时的回调
     */
    fun showSnackbar(
        message: String,
        actionLabel: String,
        onAction: (() -> Unit)? = null
    ) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }
}

/**
 * 全局 Toast CompositionLocal，默认提供预览与测试用的安全回退实例，避免在 Preview 中未提供 ToastHost 时抛出异常
 */
val LocalToastHost: ProvidableCompositionLocal<ToastState> =
    compositionLocalOf {
        ToastState(
            scope = CoroutineScope(EmptyCoroutineContext),
            snackbarHostState = SnackbarHostState()
        )
    }

/**
 * App 级 Toast 宿主 Composable。
 * 应在 MainActivity 的根布局中调用一次，并通过 CompositionLocalProvider 提供 [LocalToastHost]。
 *
 * @param modifier 修饰符
 * @param content 子 Composable，通常包含整个 App 的导航结构
 */
@Composable
fun AppToastHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toastState = remember { ToastState(scope, snackbarHostState) }

    val currentMessage = toastState.message
    // 缓存上一个非 null 的 message，退出动画期间保持文本不变
    val displayMessage = remember { mutableStateOf("") }
    if (currentMessage != null) {
        displayMessage.value = currentMessage
    }

    CompositionLocalProvider(LocalToastHost provides toastState) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            // Toast 悬浮层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = currentMessage != null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = displayMessage.value,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Snackbar 宿主（用于带操作按钮的消息，如章节切换撤回）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
