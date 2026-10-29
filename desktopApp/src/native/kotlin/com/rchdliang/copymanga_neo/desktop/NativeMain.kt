package com.rchdliang.copymanga_neo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.rchdliang.copymanga_neo.data.local.portable.PortableAppSettings
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.multiplatform.app.*
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.nio.file.Path
import dev.nucleusframework.application.*
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher

private fun dataDirectory(): Path {
    System.getProperty("copymanga.dataDir")?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
    val home = System.getProperty("user.home")
    return if (System.getProperty("os.name").startsWith("Windows", true))
        Path.of(System.getenv("LOCALAPPDATA") ?: home, "CopyMangaNeo")
    else Path.of(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "copymanga-neo")
}

@OptIn(ExperimentalFoundationApi::class)
fun main(args: Array<String>) {
    if ("--verify-build-config" in args) {
        val expected = System.getenv("COPYMANGA_TAP_STRING").orEmpty()
        check(expected.matches(Regex("[0-9a-fA-F]{32}")) && expected == OfficialAppBuildConfig.TAP_STRING) {
            "Packaged configuration does not match the build input"
        }
        println("Packaged build configuration verified")
        return
    }
    // Desktop defaults to the legacy selection menu, which bypasses the reader's menu provider.
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    System.setProperty("java.net.useSystemProxies", "true")
    System.setProperty("nucleus.app.aumid", "com.rchdliang.copymanga_neo.desktop")
    System.setProperty("nucleus.app.name", "CopyManga Neo")
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
        try { runPrimary(instance, args) }
        catch (failure: Exception) {
            // Never release the lock while an unexpectedly failed UI may have live graph writers.
            showLaunchFailure("应用意外停止：${failure.message.orEmpty()}")
            kotlin.system.exitProcess(1)
        }
    }
}

private fun showLaunchFailure(message: String) {
    System.err.println(message)
    runCatching { showWindowsLaunchFailure(message) }
}

private fun runPrimary(instance: DesktopInstanceGuard, args: Array<String>) {
    // Read the small palette preference before the first window frame, ahead of keyring/graph startup.
    val startupThemeMode = runBlocking(Dispatchers.IO) {
        try {
            PortableAppSettings.readStartupThemeMode(platformDocumentStorage(instance.directory.resolve("settings").toString()))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AppThemeMode.SYSTEM }
    }
    nucleusApplication(args, backend = NucleusBackend.Tao, enableSingleInstance = false) {
        val appIcon = remember {
            checkNotNull(DesktopAppIcons::class.java.getResourceAsStream("/copymanga-neo.png")).use {
                BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
            }
        }
        val uiScope = rememberCoroutineScope()
        val scope = remember { CoroutineScope(uiScope.coroutineContext + SupervisorJob()) }
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
                withContext(NonCancellable) { uninstalled?.shutdown() }
                if (startupJob === job) startupJob = null
            }
        }
        val windowState = rememberWindowState(width = 1200.dp, height = 820.dp)
        val backDispatcher = remember { com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher() }
        val escapeKeys = remember { DesktopEscapeKeyHandler() }
        var fullscreenKeyPressed by remember { mutableStateOf(false) }
        var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
        var sizeBeforeFullscreen by remember { mutableStateOf(windowState.size) }
        var positionBeforeFullscreen by remember { mutableStateOf(windowState.position) }
        var floatingSize by remember { mutableStateOf(windowState.size) }
        var floatingPosition by remember { mutableStateOf(windowState.position) }
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.placement, windowState.size, windowState.position) }
                .collect { (placement, size, position) ->
                    if (placement == WindowPlacement.Floating) {
                        floatingSize = size
                        floatingPosition = position
                    }
                }
        }
        var quitHidden by remember { mutableStateOf(false) }
        DecoratedWindow(
            title = "CopyManga Neo",
            icon = appIcon,
            state = windowState,
            visible = phase.visible && !quitHidden,
            onPreviewKeyEvent = fullscreenKey@ { event ->
                if (phase.showApplication) escapeKeys.observe(event)
                if (phase.showApplication && event.key == Key.F11 &&
                    !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed) {
                    when (event.type) {
                        KeyEventType.KeyDown -> if (!fullscreenKeyPressed) {
                            fullscreenKeyPressed = true
                            if (windowState.placement == WindowPlacement.Fullscreen) {
                                windowState.size = sizeBeforeFullscreen
                                windowState.position = positionBeforeFullscreen
                                windowState.placement = placementBeforeFullscreen
                            } else {
                                placementBeforeFullscreen = windowState.placement
                                sizeBeforeFullscreen = floatingSize
                                positionBeforeFullscreen = floatingPosition
                                windowState.placement = WindowPlacement.Fullscreen
                            }
                        }
                        KeyEventType.KeyUp -> fullscreenKeyPressed = false
                    }
                    return@fullscreenKey true
                }
                false
            },
            onKeyEvent = { event ->
                phase.showApplication && escapeKeys.handle(event, backDispatcher::dispatchBack)
            },
            // Removing the app is phase one; persistence runs only after that disposal commit.
            onCloseRequest = { if (!phase.flushing) phase = desktopCloseRequest(activeTransfers()) }
        ) {
            val window = nucleusWindow
            escapeKeys.WindowNavigationEffect()
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused, phase.showApplication) {
                if (!focused || !phase.showApplication) {
                    escapeKeys.reset()
                    fullscreenKeyPressed = false
                }
            }
            val host = remember(window, windowState) {
                DesktopPlatformHost(
                    backDispatcher = backDispatcher,
                    windowPolicy = ComposeDesktopWindowPolicy(windowState),
                    nativeWindowHandle = { requireNotNull(window.unsafe.taoWindow).nativeHandle },
                    nativeUiDispatcher = uiScope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                    nativeFileDialogs = true,
                )
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
                window.setMinimized(false)
                windowState.isMinimized = false
                window.show()
                window.toFront()
            }
            DisposableEffect(instance) {
                val requests = scope.launch {
                    // 窗口最小化时 scene 的调度会暂停；实例检查必须属于应用级作用域。
                    while (isActive) {
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
                onDispose { requests.cancel() }
            }
            LaunchedEffect(phase) {
                if (!phase.flushing) return@LaunchedEffect
                val target = phase
                val recovery = quitRecovery
                quitRecovery = DesktopQuitRecovery.SAVE
                failedQuit = false
                // Keep shutdown in the application scope after the window becomes invisible.
                if (target == DesktopWindowPhase.FLUSH_QUIT) quitHidden = true
                scope.launch {
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
                        quitHidden = false
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
            }

            DisposableEffect(graph, window) {
                val taskbarProgress = graph?.let { ready -> NucleusDesktopTaskbarProgress(
                    applicationScope = scope, window = window,
                    downloads = ready.repositories.downloads, exports = ready.repositories.export,
                    visibility = snapshotFlow { phase.visible },
                    onWarning = { warning -> scope.launch { if (warning !in transferWarnings) transferWarnings += warning } }
                ) }
                val notifications = graph?.let { ready -> NucleusDesktopTransferNotifications(
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
                    notifications?.status?.collect {
                        trayStatus = it
                        // 隐藏窗口时 scene 不运行；托盘丢失也必须在应用级任务中恢复窗口。
                        if (desktopRecoverTrayLoss(phase, it) != phase) reopen()
                    }
                }
                onDispose { statusJob.cancel(); notifications?.close(); taskbarProgress?.close() }
            }
            LaunchedEffect(warnings, graph, transferWarnings.toList()) {
                for (warning in (graph?.startupWarnings.orEmpty() + warnings + transferWarnings).distinct()) {
                    if (warning !in dismissedWarnings) {
                        snackbar.showSnackbar(warning, actionLabel = "知道了", duration = SnackbarDuration.Long)
                        dismissedWarnings = dismissedWarnings + warning
                    }
                }
            }
            PortableHostTheme(graph?.repositories?.appSettings, initialThemeMode = startupThemeMode) {
                val titleStyle = LocalTitleBarStyle.current
                TitleBar(style = titleStyle.copy(colors = titleStyle.colors.copy(
                    background = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveBackground = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurface,
                    controlButtonIconColor = MaterialTheme.colorScheme.onSurface,
                ))) {
                    Row(
                        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = rememberTitleBarIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("CopyManga Neo",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val ready = graph
                    if (phase.flushing) StartupSurface() else if (phase == DesktopWindowPhase.BACKGROUND) {
                        // Deliberately dispose reading effects while retaining window, graph and saveables.
                    } else if (ready != null) appState.SaveableStateProvider("application") {
                        com.rchdliang.copymanga_neo.multiplatform.platform.DesktopUriHandlerProvider {
                            CopyMangaApp(ready.repositories, remember(ready, host) { ready.platforms(host) }, owner,
                                openDownloads = openDownloads, onDownloadsOpened = { openDownloads = false })
                        }
                    } else StartupSurface(error) { attempt++ }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    if (phase == DesktopWindowPhase.CONFIRM_CLOSE) AlertDialog(
                        onDismissRequest = { phase = DesktopWindowPhase.OPEN },
                        title = { NativeText("下载或导出仍在进行") },
                        text = { NativeText(if (trayStatus == DesktopNotificationStatus.Available)
                            "可以继续在系统托盘中运行，稍后通过托盘打开或退出应用。退出会停止传输，下载队列会保留。"
                            else "系统托盘暂不可用，可以最小化窗口继续传输。退出会停止传输，下载队列会保留。") },
                        confirmButton = { TextButton(onClick = {
                            // Read current availability again; never create an unrecoverable hidden window.
                            if (trayStatus == DesktopNotificationStatus.Available) phase = DesktopWindowPhase.FLUSH_BACKGROUND
                            else { phase = DesktopWindowPhase.OPEN; windowState.isMinimized = true }
                        }) { NativeText(if (trayStatus == DesktopNotificationStatus.Available) "后台继续" else "最小化继续") } },
                        dismissButton = { Row {
                            TextButton(onClick = { phase = DesktopWindowPhase.OPEN }) { NativeText("返回") }
                            TextButton(onClick = { phase = DesktopWindowPhase.FLUSH_QUIT }) { NativeText("停止并退出") }
                        } }
                    )
                    if (phase.showApplication && ready != null && error != null) AlertDialog(
                        onDismissRequest = { error = null; failedQuit = false },
                        title = { NativeText(if (failedQuit) "阅读记录未能保存" else "窗口保持打开") },
                        text = { NativeText(error.orEmpty() + if (failedQuit)
                            "\n可以重新读取原文件并重试保存，或明确选择不保存退出。后者可能丢失本次尚未保存的阅读进度；不会删除原有书库文件。" else "") },
                        confirmButton = { TextButton(onClick = { error = null; failedQuit = false }) { NativeText("返回应用") } },
                        dismissButton = { if (failedQuit) Column {
                            TextButton(onClick = {
                                error = null
                                quitRecovery = DesktopQuitRecovery.RETRY_READ_AND_SAVE
                                phase = DesktopWindowPhase.FLUSH_QUIT
                            }) { NativeText("重试保存并退出") }
                            TextButton(onClick = {
                                error = null
                                quitRecovery = DesktopQuitRecovery.QUIT_WITHOUT_SAVING
                                phase = DesktopWindowPhase.FLUSH_QUIT
                            }) { NativeText("不保存本次阅读进度并退出") }
                        } }
                    )
                }
            }
        }
    }
}

private val titleBarIconSizes = listOf(16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 96, 128, 256, 512)

/** 按当前窗口的物理像素选图，避免将大图标骤缩后产生细线锯齿。 */
@Composable
private fun rememberTitleBarIcon(): BitmapPainter {
    val pixels = with(LocalDensity.current) { 20.dp.roundToPx() }
    val resourceSize = titleBarIconSizes.firstOrNull { it >= pixels } ?: titleBarIconSizes.last()
    return remember(resourceSize) {
        val resource = if (resourceSize == 512) "/copymanga-neo.png"
            else "/icons/copymanga-neo-$resourceSize.png"
        checkNotNull(DesktopAppIcons::class.java.getResourceAsStream(resource)).use {
            BitmapPainter(
                image = org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap(),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/** 宿主提示始终使用当前主题的可读文字颜色。 */
@Composable
private fun NativeText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface)
}
