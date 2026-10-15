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
    LaunchedEffect(warnings, graph) {
        for (warning in (graph?.startupWarnings.orEmpty() + warnings).distinct()) if (warning !in shown) {
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
            else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (error == null) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("正在读取本地资料…") }
                else { Text(error.orEmpty()); Spacer(Modifier.height(16.dp)); Button(onClick = { attempt++ }) { Text("重试") } }
            }
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
