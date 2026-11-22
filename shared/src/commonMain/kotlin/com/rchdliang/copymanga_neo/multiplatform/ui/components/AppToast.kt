package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class ToastState internal constructor(
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState
) {
    private val pendingMessages = MutableStateFlow<Set<String>>(emptySet())

    fun showToast(message: String) {
        scope.launch {
            if (message in pendingMessages.getAndUpdate { it + message }) return@launch
            try {
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = true,
                    duration = if (message.length > 24) SnackbarDuration.Long else SnackbarDuration.Short
                )
            } finally {
                pendingMessages.update { it - message }
            }
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
