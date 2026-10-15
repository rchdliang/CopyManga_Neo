package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.rchdliang.copymanga_neo.multiplatform.platform.IosPlatformHost
import com.rchdliang.copymanga_neo.multiplatform.platform.IosReaderWindowState
import com.rchdliang.copymanga_neo.multiplatform.ui.components.NativeBackBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController

/** The environment override is for debug launches only; archived builds use an explicit plist value. */
private fun iosTapString(debugBuild: Boolean): String {
    fun configured(value: Any?): String? = (value as? String)?.takeIf {
        it.isNotBlank() && it != "\$(COPYMANGA_TAP_STRING)"
    }
    if (debugBuild) configured(NSProcessInfo.processInfo.environment["COPYMANGA_TAP_STRING"])?.let { return it }
    return configured(NSBundle.mainBundle.objectForInfoDictionaryKey("CopyMangaTapString")).orEmpty()
}

/** Entry point used by the native UIKit launcher. Configuration is supplied by the host build/run. */
fun MainViewController(windowState: IosReaderWindowState, backBridge: NativeBackBridge, presenter: () -> UIViewController): UIViewController {
    val debugBuild = NSBundle.mainBundle.objectForInfoDictionaryKey("CopyMangaDebugBuild") == "YES"
    val host = IosPlatformHost(presenter = presenter, windowPolicy = windowState.windowPolicy)
    backBridge.connect(host.backDispatcher)
    val warningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val warnings = combine(host.capabilityWarnings, windowState.capabilityWarnings) { hostWarnings, windowWarnings ->
        (hostWarnings + windowWarnings).distinct()
    }.stateIn(warningScope, SharingStarted.Eagerly, emptyList())
    return ComposeUIViewController {
        PortableAppEntry(
            create = { scope -> IosGraph.create(IosGraph.applicationDirectory(), scope, host, tapString = iosTapString(debugBuild), debugBuild = debugBuild).core },
            platforms = { AppPlatforms(host, host, host, host, host, it.imageLoader, host) },
            capabilityWarnings = warnings,
            onHostClose = { backBridge.disconnect(); host.close(); warningScope.cancel() },
            lifecycle = { graph, scope, report ->
                DisposableEffect(graph) {
                    val center = NSNotificationCenter.defaultCenter
                    val observer = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                        backBridge.cancelGesture()
                        scope.launch { graph?.repositories?.localReading?.flush()?.onFailure { report(it.message ?: "阅读记录保存失败") } }
                    }
                    onDispose { center.removeObserver(observer) }
                }
            }
        )
    }
}
