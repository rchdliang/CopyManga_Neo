package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.awaitApplication
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Opt in on an interactive Windows desktop; exercises real native fullscreen and DPI scaling. */
class DesktopFullscreenRestoreUiTest {
    @Test fun restoredNativeWindowAndComposeViewportMatchOriginal() {
        assumeTrue(System.getenv("COPYMANGA_TEST_NATIVE_WINDOW") == "1")
        assumeTrue(System.getProperty("os.name").startsWith("Windows") && !GraphicsEnvironment.isHeadless())
        val failure = AtomicReference<Throwable?>()
        val completedCycles = AtomicInteger()
        runBlocking {
            withTimeout(30_000) {
                awaitApplication {
                    val state = rememberWindowState(width = 800.dp, height = 600.dp)
                    Window(onCloseRequest = ::exitApplication, state = state, title = "Fullscreen restoration test") {
                        DesktopFullscreenRestoreEffect(window, state.placement)
                        val measured = remember { AtomicReference(IntSize.Zero) }
                        Box(Modifier.fillMaxSize().onSizeChanged { measured.set(it) })
                        LaunchedEffect(Unit) {
                            try {
                                delay(500)
                                val original = nativeBounds(window.windowHandle)
                                val originalViewport = measured.get()
                                assertNotEquals(IntSize.Zero, originalViewport)
                                repeat(3) {
                                    state.placement = WindowPlacement.Fullscreen
                                    delay(500)
                                    assertEquals(WindowPlacement.Fullscreen, window.placement)
                                    assertNotEquals(original, nativeBounds(window.windowHandle))
                                    state.placement = WindowPlacement.Floating
                                    // AWT resize events and Compose measurement settle on separate
                                    // EDT turns. Observe the result without driving an extra frame.
                                    var attempts = 0
                                    while ((window.placement != WindowPlacement.Floating ||
                                            nativeBounds(window.windowHandle) != original ||
                                            measured.get() != originalViewport) && attempts++ < 100) {
                                        delay(20)
                                    }
                                    val restored = nativeBounds(window.windowHandle)
                                    assertEquals(WindowPlacement.Floating, window.placement)
                                    assertEquals(original, restored, "Native bounds after fullscreen cycle $it")
                                    assertEquals(originalViewport, measured.get(), "Compose viewport after fullscreen cycle $it")
                                    completedCycles.incrementAndGet()
                                }
                            } catch (error: Throwable) {
                                // Window composition exceptions otherwise only reach the EDT handler.
                                failure.set(error)
                            } finally {
                                exitApplication()
                            }
                        }
                    }
                }
            }
        }
        failure.get()?.let { throw it }
        assertEquals(3, completedCycles.get())
    }

    private fun nativeBounds(handle: Long): List<Int> = Memory(16).use { rect ->
        check(user32.GetWindowRect(Pointer(handle), rect))
        List(4) { rect.getInt(it * 4L) }
    }

    private interface GeometryUser32 : StdCallLibrary {
        fun GetWindowRect(window: Pointer, rect: Pointer): Boolean
    }

    private val user32 by lazy { Native.load("user32", GeometryUser32::class.java) }
}
