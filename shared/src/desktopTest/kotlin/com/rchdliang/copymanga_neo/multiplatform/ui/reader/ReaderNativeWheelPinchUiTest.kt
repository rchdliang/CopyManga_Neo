package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.platform.desktopCtrlWheelPinchStepsPerDoubling
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/** 重放 Nucleus Windows 实际产生的 Ctrl＋双指事件，不能仅用普通 Scroll 事件代替。 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class ReaderNativeWheelPinchUiTest {
    @Test
    fun windowsControlWheelPinchUsesThreeTimesScale() = verifyPinch(continuous = false, ctrl = true, native = true)
    @Test
    fun windowsControlWheelPinchDoesNotScrollContinuousReader() =
        verifyPinch(continuous = true, ctrl = true, native = true)

    @Test
    fun ordinaryTouchPinchKeepsItsOriginalSensitivity() = verifyPinch(continuous = false, ctrl = false, native = true)
    @Test
    fun nonNativeHostDoesNotRescaleTouchPinch() = verifyPinch(continuous = false, ctrl = true, native = false)

    private fun verifyPinch(continuous: Boolean, ctrl: Boolean, native: Boolean) {
        val zoom = ReaderZoomState()
        lateinit var list: LazyListState
        val maximum = if (continuous) READER_CONTINUOUS_MAX_SCALE else READER_MAX_SCALE
        val adapter = desktopCtrlWheelPinchStepsPerDoubling("Windows 11", native)
        val scene = ImageComposeScene(900, 600) {
            list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalReaderCtrlWheelPinchSteps provides adapter) {
                Box(
                    Modifier.fillMaxSize().onSizeChanged(zoom::updateViewport)
                        .readerImageZoomable(zoom, scope, continuous, maximum)
                ) {
                    if (continuous) LazyColumn(state = list) {
                        items(30) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                    }
                }
            }
        }
        var time = 0L
        fun frames() {
            repeat(4) { time += 16_000_000L; scene.render(time).close() }
        }

        val focal = Offset(500f, 250f)
        fun nativeTick(notches: Float) {
            // TaoWheelPinchZoom.stepFromWheelDelta 与 Windows 宿主的 120px 初始半径。
            val step = 2f.pow(notches / 12f)
            fun send(type: PointerEventType, radius: Float) {
                val pressed = type != PointerEventType.Release
                scene.sendPointerEvent(
                    type,
                    pointers = listOf(
                        ComposeScenePointer(PointerId(0xA001L), focal - Offset(radius, 0f), pressed, PointerType.Touch),
                        ComposeScenePointer(PointerId(0xA002L), focal + Offset(radius, 0f), pressed, PointerType.Touch)
                    ),
                    keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = ctrl),
                    timeMillis = time / 1_000_000L
                )
                frames()
            }
            send(PointerEventType.Press, 120f)
            send(PointerEventType.Move, 120f * step)
            send(PointerEventType.Release, 120f * step)
        }
        try {
            frames()
            nativeTick(1f)
            assertEquals(if (ctrl && native) 3f else 2f.pow(1f / 12f), zoom.scale, .002f)
            val anchor = (focal - zoom.viewportCenter() - zoom.offset) / zoom.scale
            assertEquals(50f, anchor.x, .01f)
            assertEquals(-50f, anchor.y, .01f)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
            if (ctrl && native) {
                nativeTick(-1f)
                assertEquals(1f, zoom.scale, .002f)
                nativeTick(.5f)
                assertEquals(kotlin.math.sqrt(3f), zoom.scale, .002f)
                nativeTick(2f)
                assertEquals(maximum, zoom.scale, .002f)
            }
        } finally {
            scene.close()
        }
    }
}
