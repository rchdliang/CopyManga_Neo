package com.rchdliang.copymanga_neo.multiplatform.platform

import java.awt.Window

/** A lease restores the previous policy when the last reader effect is disposed. */
interface DesktopWindowPolicy {
    fun acquireFullscreen(): Result<AutoCloseable>
    fun acquireKeepAwake(): Result<AutoCloseable>
}

/** Compose WindowState hosts can provide callbacks without exposing their navigation to shared UI. */
class CallbackDesktopWindowPolicy(
    private val fullscreen: () -> Result<AutoCloseable>,
    private val keepAwake: () -> Result<AutoCloseable> = {
        DesktopAwakeInhibitor.acquire()
    }
) : DesktopWindowPolicy {
    override fun acquireFullscreen() = fullscreen()
    override fun acquireKeepAwake() = keepAwake()
}

/** AWT full screen is real and scoped. Idle inhibition requires a host-native lease provider. */
class AwtDesktopWindowPolicy(
    private val window: Window,
    private val keepAwake: (() -> Result<AutoCloseable>)? = null
) : DesktopWindowPolicy {
    private var fullScreenUsers = 0
    private var previousFullscreen: Window? = null

    override fun acquireFullscreen(): Result<AutoCloseable> = runCatching {
        val device = window.graphicsConfiguration.device
        check(device.isFullScreenSupported) { "当前桌面不支持全屏窗口" }
        if (fullScreenUsers == 0) {
            previousFullscreen = device.fullScreenWindow
            device.fullScreenWindow = window
        }
        fullScreenUsers++
        var closed = false
        AutoCloseable {
            if (!closed) {
                closed = true
                if (--fullScreenUsers == 0 && device.fullScreenWindow === window) {
                    device.fullScreenWindow = previousFullscreen
                    previousFullscreen = null
                }
            }
        }
    }

    override fun acquireKeepAwake(): Result<AutoCloseable> = keepAwake?.invoke()
        ?: DesktopAwakeInhibitor.acquire()
}
