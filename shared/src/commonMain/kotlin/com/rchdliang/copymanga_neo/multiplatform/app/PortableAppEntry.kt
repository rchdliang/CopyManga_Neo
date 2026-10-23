package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/** One startup/error/retry surface for native and browser hosts, with the same complete screen graph. */
@Composable
fun PortableAppEntry(
    create: suspend (CoroutineScope) -> PortableAppGraph,
    platforms: (PortableAppGraph) -> AppPlatforms,
    capabilityWarnings: StateFlow<List<String>>,
    onHostClose: () -> Unit = {},
    lifecycle: @Composable (PortableAppGraph?, CoroutineScope, (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    var graph by remember { mutableStateOf<PortableAppGraph?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var disposed by remember { mutableStateOf(false) }
    var startupJob by remember { mutableStateOf<Job?>(null) }
    val warnings by capabilityWarnings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var shown by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(attempt) {
        val job = currentCoroutineContext().job
        startupJob = job
        error = null
        var uninstalled: PortableAppGraph? = null
        try {
            // Wasm 的本地存储是同步 API；先让宿主绘制加载指示器，再读取配置。
            withFrameNanos { }
            withFrameNanos { }
            uninstalled = create(scope)
            currentCoroutineContext().ensureActive()
            if (!disposed) {
                graph = uninstalled
                uninstalled = null
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "初始化失败" }
        finally {
            try { uninstalled?.shutdown() }
            finally { if (startupJob === job) startupJob = null }
        }
    }
    val graphWarnings by (graph?.warnings ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<String>()) }).collectAsState()
    LaunchedEffect(warnings, graphWarnings) {
        for (warning in (graphWarnings + warnings).distinct()) if (warning !in shown) {
            snackbar.showSnackbar(warning, actionLabel = "知道了", duration = SnackbarDuration.Long)
            shown = shown + warning
        }
    }
    lifecycle(graph, scope) { error = it }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            val pendingStartup = startupJob
            pendingStartup?.cancel()
            owner.viewModelStore.clear()
            // Compose disposal is not suspendable; retain a short independent scope to flush pending records.
            CoroutineScope(Dispatchers.Main + NonCancellable).launch {
                try {
                    // Startup may still be releasing resources that depend on the native host.
                    pendingStartup?.join()
                    graph?.repositories?.localReading?.flush()
                } finally {
                    try { graph?.shutdown() }
                    finally { scope.cancel(); onHostClose() }
                }
            }
        }
    }
    PortableHostTheme(graph?.repositories?.appSettings) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val ready = graph
            if (ready != null) CopyMangaApp(ready.repositories, remember(ready) { platforms(ready) }, owner)
            else StartupSurface(error) { attempt++ }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            if (ready != null && error != null) AlertDialog(
                onDismissRequest = { error = null }, title = { Text("资料尚未保存") }, text = { Text(error.orEmpty()) },
                confirmButton = { TextButton(onClick = { scope.launch {
                    ready.repositories.localReading.flush().onSuccess { error = null }.onFailure { error = it.message ?: "保存失败" }
                } }) { Text("重试保存") } }, dismissButton = { TextButton(onClick = { error = null }) { Text("返回应用") } }
            )
        }
    }
}
