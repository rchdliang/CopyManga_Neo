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

/**
 * 全局 Toast 状态持有者，提供 showToast 方法以显示自定义 Compose Toast。
 * 通过 [AppToastHost] 创建并提供给整个 App 使用。
 */
class ToastState internal constructor(private val scope: CoroutineScope) {
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
            delay(2000L)
            this@ToastState.message = null
        }
    }
}

/**
 * 全局 Toast CompositionLocal，默认抛出异常以提示未提供 ToastHost
 */
val LocalToastHost: ProvidableCompositionLocal<ToastState> =
    compositionLocalOf { error("LocalToastHost 未提供，请在 App 根部放置 AppToastHost") }

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
    val toastState = remember { ToastState(scope) }

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
                    visible = toastState.message != null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = toastState.message.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
