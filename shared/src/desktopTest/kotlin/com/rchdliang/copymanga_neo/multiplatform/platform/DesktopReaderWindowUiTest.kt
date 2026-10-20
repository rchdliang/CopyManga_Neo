package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopReaderWindowUiTest {
    @Test fun enteringReadersAndTogglingControlsNeverRequestsFullscreen() = runSkikoComposeUiTest {
        var requests = 0
        val policy = CallbackDesktopWindowPolicy(
            fullscreen = { requests++; Result.success(AutoCloseable {}) },
            keepAwake = { Result.success(AutoCloseable {}) }
        )
        val host = DesktopPlatformHost(windowPolicy = policy)
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        var reading by mutableStateOf(false)
        var controls by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (reading) {
                    host.ImmersiveEffect(ScreenOrientationSetting.SYSTEM_DEFAULT)
                    host.ReaderWindow(keepScreenOn = false, controlsVisible = controls, lightBars = false)
                }
            }
        }
        repeat(3) {
            runOnIdle { reading = true }
            waitForIdle()
            runOnIdle { controls = true }
            waitForIdle()
            runOnIdle { controls = false }
            waitForIdle()
            runOnIdle { reading = false }
            waitForIdle()
        }
        assertEquals(0, requests)
        runOnIdle { owner.destroy() }
    }
}
