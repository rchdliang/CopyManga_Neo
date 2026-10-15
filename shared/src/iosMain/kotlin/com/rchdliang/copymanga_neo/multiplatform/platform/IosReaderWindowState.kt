package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSThread
import platform.UIKit.*

/** Swift's UIViewController overrides UIKit category methods which Kotlin cannot override. */
class IosReaderWindowState(private val onChanged: () -> Unit) {
    private data class Policy(val hidden: Boolean, val light: Boolean, val orientation: ScreenOrientationSetting?)
    private val policies = linkedMapOf<Any, Policy>()
    private val current get() = policies.values.lastOrNull()
    private val warnings = MutableStateFlow<List<String>>(emptyList())
    val capabilityWarnings: StateFlow<List<String>> = warnings
    val hasAppearancePolicy: Boolean get() = current != null
    val hiddenBars: Boolean get() = current?.hidden ?: false
    /** True means a light background with dark system-bar glyphs, matching Android. */
    val lightBars: Boolean get() = current?.light ?: false
    /** Zero restores the Swift host's original orientation policy. */
    val orientationMask: Long get() = when (policies.values.lastOrNull { it.orientation != null }?.orientation) {
        ScreenOrientationSetting.PORTRAIT -> UIInterfaceOrientationMaskPortrait.toLong()
        ScreenOrientationSetting.LANDSCAPE -> UIInterfaceOrientationMaskLandscape.toLong()
        ScreenOrientationSetting.AUTO -> UIInterfaceOrientationMaskAll.toLong()
        ScreenOrientationSetting.SYSTEM_DEFAULT, null -> 0L
    }
    val windowPolicy = IosReaderWindowPolicy { hidden, light, orientation ->
        runCatching {
            check(NSThread.isMainThread) { "阅读窗口策略必须在主线程应用" }
            val key = Any(); policies[key] = Policy(hidden, light, orientation)
            try { onChanged() } catch (error: Exception) { policies.remove(key); throw error }
            AutoCloseable { if (policies.remove(key) != null) onChanged() }
        }
    }
    fun reportFailure(message: String) { if (message !in warnings.value) warnings.value += message }
}
