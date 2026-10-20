package com.rchdliang.copymanga_neo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.rchdliang.copymanga_neo.multiplatform.app.*
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.nio.file.Path

private fun dataDirectory(): Path {
    System.getProperty("copymanga.dataDir")?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
    val home = System.getProperty("user.home")
    return if (System.getProperty("os.name").startsWith("Windows", true))
        Path.of(System.getenv("LOCALAPPDATA") ?: home, "CopyMangaNeo")
    else Path.of(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "copymanga-neo")
}

fun main() {
    System.setProperty("java.net.useSystemProxies", "true")
    val instance = try { DesktopInstanceGuard.acquire(dataDirectory()) }
    catch (failure: Exception) {
        showLaunchFailure("无法安全打开应用资料，未启动第二个写入进程：${failure.message.orEmpty()}")
        return
    }
    instance.use {
        if (!instance.primary) {
            val signaled = try { instance.requestOpen() } catch (_: Exception) { false }
            if (!signaled) showLaunchFailure("已有实例正在启动或退出，但未能确认打开窗口。为保护资料，本次不会启动第二个实例。请稍后重试。")
            return
        }
        try { runPrimary(instance) }
        catch (failure: Exception) {
            // Never release the lock while an unexpectedly failed UI may have live graph writers.
            showLaunchFailure("应用意外停止：${failure.message.orEmpty()}")
            kotlin.system.exitProcess(1)
        }
    }
}

private fun showLaunchFailure(message: String) {
    System.err.println(message)
    if (!java.awt.GraphicsEnvironment.isHeadless()) runCatching {
        javax.swing.JOptionPane.showMessageDialog(null, message, "CopyManga Neo", javax.swing.JOptionPane.ERROR_MESSAGE)
    }
}

private fun runPrimary(instance: DesktopInstanceGuard) {
    application(exitProcessOnExit = false) {
        val appIcon = remember { BitmapPainter(useResource("copymanga-neo.png", ::loadImageBitmap)) }
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        var graph by remember { mutableStateOf<DesktopGraph?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        var phase by remember { mutableStateOf(DesktopWindowPhase.OPEN) }
        var trayStatus by remember { mutableStateOf<DesktopNotificationStatus>(DesktopNotificationStatus.Starting) }
        fun activeTransfers(): Boolean = graph?.repositories?.let { repositories ->
            desktopHasActiveTransfers(repositories.downloads.getDownloadProgress().value.values.map { it.status },
                repositories.export.state.value.running)
        } ?: false
        var openDownloads by remember { mutableStateOf(false) }
        var startupJob by remember { mutableStateOf<Job?>(null) }
        val transferWarnings = remember { mutableStateListOf<String>() }
        LaunchedEffect(attempt) {
            val job = currentCoroutineContext().job
            startupJob = job
            error = null
            var uninstalled: DesktopGraph? = null
            try {
                // Keep ownership even if withContext is canceled after create succeeds but before delivery.
                withContext(Dispatchers.IO) {
                    uninstalled = DesktopGraph.create(instance.directory, scope,
                        System.getenv("COPYMANGA_TAP_STRING").orEmpty().ifBlank { OfficialAppBuildConfig.TAP_STRING })
                }
                currentCoroutineContext().ensureActive()
                if (phase != DesktopWindowPhase.FLUSH_QUIT) {
                    graph = uninstalled
                    uninstalled = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "初始化失败" }
            finally {
                uninstalled?.shutdown()
                if (startupJob === job) startupJob = null
            }
        }
        val windowState = rememberWindowState(width = 1200.dp, height = 820.dp)
        Window(
            title = "CopyManga Neo",
            icon = appIcon,
            state = windowState,
            visible = phase.visible,
            // Removing the app is phase one; persistence runs only after that disposal commit.
            onCloseRequest = { if (!phase.flushing) phase = desktopCloseRequest(activeTransfers()) }
        ) {
            val escapeKeys = remember { DesktopEscapeKeyHandler() }
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused, phase.showApplication) {
                if (!focused || !phase.showApplication) escapeKeys.reset()
            }
            val host = remember(window, windowState) {
                DesktopPlatformHost(window, ComposeDesktopWindowPolicy(windowState))
            }
            val warnings by host.capabilityWarnings.collectAsState()
            var dismissedWarnings by remember { mutableStateOf(setOf<String>()) }
            val snackbar = remember { SnackbarHostState() }
            val appState = rememberSaveableStateHolder()
            var reopenAfterFlush by remember { mutableStateOf(false) }
            var failedQuit by remember { mutableStateOf(false) }
            var quitRecovery by remember { mutableStateOf(DesktopQuitRecovery.SAVE) }
            fun reopen(downloads: Boolean = false) {
                // Do not resurrect UI collectors once final shutdown has started.
                if (phase == DesktopWindowPhase.FLUSH_QUIT) return
                if (downloads) openDownloads = true
                if (phase == DesktopWindowPhase.FLUSH_BACKGROUND) {
                    // Finish the flush before reintroducing reader collectors.
                    reopenAfterFlush = true
                    return
                }
                phase = DesktopWindowPhase.OPEN
                windowState.isMinimized = false
                window.isVisible = true
                window.toFront()
            }
            LaunchedEffect(instance) {
                while (isActive) {
                    // Keep the lock through final graph drain. A late secondary must time out,
                    // rather than get a false acknowledgement from an already-quitting window.
                    if (phase != DesktopWindowPhase.FLUSH_QUIT) {
                        try { instance.pollOpenRequests { reopen() } }
                        catch (failure: Exception) {
                            val warning = "无法处理再次打开应用的请求：${failure.message.orEmpty()}"
                            if (warning !in transferWarnings) transferWarnings += warning
                        }
                    }
                    delay(150)
                }
            }
            LaunchedEffect(phase) {
                if (!phase.flushing) return@LaunchedEffect
                val target = phase
                val recovery = quitRecovery
                quitRecovery = DesktopQuitRecovery.SAVE
                failedQuit = false
                // This effect starts after applyChanges disposes the application branch. Keep
                // route VMs/saveables until persistence succeeds, including on a background close.
                if (target == DesktopWindowPhase.FLUSH_QUIT) startupJob?.cancelAndJoin()
                val result = desktopFlushWindowTransition(target,
                    flushReading = { graph?.repositories?.localReading?.flush() ?: Result.success(Unit) },
                    trayStatus = { trayStatus },
                    recovery = recovery,
                    retryRead = { graph?.repositories?.localReading?.retryRead() ?: Result.success(Unit) },
                    quit = {
                        owner.viewModelStore.clear()
                        graph?.shutdown()
                        exitApplication()
                        scope.cancel()
                    })
                if (result.isFailure) {
                    reopenAfterFlush = false
                    failedQuit = target == DesktopWindowPhase.FLUSH_QUIT
                    error = "阅读记录尚未保存，窗口保持打开：${result.exceptionOrNull()?.message}"
                    phase = DesktopWindowPhase.OPEN
                } else if (target == DesktopWindowPhase.FLUSH_BACKGROUND) {
                    if (reopenAfterFlush) {
                        reopenAfterFlush = false
                        phase = DesktopWindowPhase.OPEN
                        reopen()
                    } else {
                        phase = result.getOrThrow()
                        if (phase == DesktopWindowPhase.OPEN) windowState.isMinimized = true
                    }
                }
            }

            LaunchedEffect(trayStatus) {
                if (desktopRecoverTrayLoss(phase, trayStatus) != phase) reopen()
            }
            DisposableEffect(graph) {
                val notifications = graph?.let { ready -> DesktopTransferNotifications(
                    applicationScope = scope, downloads = ready.repositories.downloads, exports = ready.repositories.export,
                    onOpenDownloads = { scope.launch { reopen(downloads = true) } },
                    onOpenApplication = { scope.launch { reopen() } },
                    onQuit = { scope.launch {
                        if (!phase.flushing) {
                            reopen()
                            phase = desktopCloseRequest(activeTransfers())
                        }
                    } },
                    onWarning = { warning -> scope.launch { if (warning !in transferWarnings) transferWarnings += warning } }
                ) }
                val statusJob = scope.launch {
                    notifications?.status?.collect { trayStatus = it }
                }
                onDispose { statusJob.cancel(); notifications?.close() }
            }
            LaunchedEffect(warnings, graph, transferWarnings.toList()) {
                for (warning in (graph?.startupWarnings.orEmpty() + warnings + transferWarnings).distinct()) {
                    if (warning !in dismissedWarnings) {
                        snackbar.showSnackbar(warning, actionLabel = "知道了", duration = SnackbarDuration.Long)
                        dismissedWarnings = dismissedWarnings + warning
                    }
                }
            }
            PortableHostTheme(graph?.repositories?.appSettings) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPreviewKeyEvent { event ->
                    if (phase.showApplication && event.key == Key.Escape) escapeKeys.observe(when (event.type) {
                        KeyEventType.KeyDown -> DesktopEscapeEvent.DOWN
                        KeyEventType.KeyUp -> DesktopEscapeEvent.UP
                        else -> DesktopEscapeEvent.OTHER
                    })
                    false // Children always get first refusal; this only records physical press ownership.
                }.onKeyEvent { event ->
                    if (!phase.showApplication || event.key != Key.Escape) false
                    else escapeKeys.handle(
                        event = when (event.type) {
                            KeyEventType.KeyDown -> DesktopEscapeEvent.DOWN
                            KeyEventType.KeyUp -> DesktopEscapeEvent.UP
                            else -> DesktopEscapeEvent.OTHER
                        },
                        modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed,
                        dispatchBack = host.backDispatcher::dispatchBack)
                }) {
                    val ready = graph
                    if (phase.flushing) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("正在保存阅读记录…")
                    } else if (phase == DesktopWindowPhase.BACKGROUND) {
                        // Deliberately dispose reading effects while retaining window, graph and saveables.
                    } else if (ready != null) appState.SaveableStateProvider("application") {
                        CopyMangaApp(ready.repositories, remember(ready, host) { ready.platforms(host) }, owner,
                            openDownloads = openDownloads, onDownloadsOpened = { openDownloads = false })
                    } else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("正在读取本地资料…") }
                        else { Text(error.orEmpty()); Spacer(Modifier.height(16.dp)); Button(onClick = { attempt++ }) { Text("重试") } }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    if (phase == DesktopWindowPhase.CONFIRM_CLOSE) AlertDialog(
                        onDismissRequest = { phase = DesktopWindowPhase.OPEN },
                        title = { Text("下载或导出仍在进行") },
                        text = { Text(if (trayStatus == DesktopNotificationStatus.Available)
                            "可以继续在系统托盘中运行，稍后通过托盘打开或退出应用。退出会停止传输，下载队列会保留。"
                            else "系统托盘暂不可用，可以最小化窗口继续传输。退出会停止传输，下载队列会保留。") },
                        confirmButton = { TextButton(onClick = {
                            // Read current availability again; never create an unrecoverable hidden window.
                            if (trayStatus == DesktopNotificationStatus.Available) phase = DesktopWindowPhase.FLUSH_BACKGROUND
                            else { phase = DesktopWindowPhase.OPEN; windowState.isMinimized = true }
                        }) { Text(if (trayStatus == DesktopNotificationStatus.Available) "后台继续" else "最小化继续") } },
                        dismissButton = { Row {
                            TextButton(onClick = { phase = DesktopWindowPhase.OPEN }) { Text("返回") }
                            TextButton(onClick = { phase = DesktopWindowPhase.FLUSH_QUIT }) { Text("停止并退出") }
                        } }
                    )
                    if (phase.showApplication && ready != null && error != null) AlertDialog(
                        onDismissRequest = { error = null; failedQuit = false },
                        title = { Text(if (failedQuit) "阅读记录未能保存" else "窗口保持打开") },
                        text = { Text(error.orEmpty() + if (failedQuit)
                            "\n可以重新读取原文件并重试保存，或明确选择不保存退出。后者可能丢失本次尚未保存的阅读进度；不会删除原有书库文件。" else "") },
                        confirmButton = { TextButton(onClick = { error = null; failedQuit = false }) { Text("返回应用") } },
                        dismissButton = { if (failedQuit) Column {
                            TextButton(onClick = {
                                error = null
                                quitRecovery = DesktopQuitRecovery.RETRY_READ_AND_SAVE
                                phase = DesktopWindowPhase.FLUSH_QUIT
                            }) { Text("重试保存并退出") }
                            TextButton(onClick = {
                                error = null
                                quitRecovery = DesktopQuitRecovery.QUIT_WITHOUT_SAVING
                                phase = DesktopWindowPhase.FLUSH_QUIT
                            }) { Text("不保存本次阅读进度并退出") }
                        } }
                    )
                }
            }
        }
    }
}
