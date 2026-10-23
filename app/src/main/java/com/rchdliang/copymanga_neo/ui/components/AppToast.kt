package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class ToastState internal constructor(
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState
) {
    /** Queue messages; Material applies the platform's recommended reading timeout. */
    fun showToast(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = if (listOf("失败", "未完成", "无法", "请先登录", "已过期").any { it in message }) SnackbarDuration.Indefinite
                    else if (message.length > 24) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    fun showSnackbar(message: String, actionLabel: String, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(message, actionLabel, withDismissAction = true,
                duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalToastHost: ProvidableCompositionLocal<ToastState> = compositionLocalOf {
    ToastState(CoroutineScope(EmptyCoroutineContext), SnackbarHostState())
}

@Composable
fun AppToastHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val host = remember { SnackbarHostState() }
    val state = remember(scope, host) { ToastState(scope, host) }
    CompositionLocalProvider(LocalToastHost provides state) {
        Box(modifier.fillMaxSize()) {
            content()
            SnackbarHost(host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 48.dp))
        }
    }
}
