package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import kotlin.test.*

/** 覆盖窗口先观察、子控件处理、窗口兜底的真实 Compose 按键分发顺序。 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopEscapeKeyHandlerSceneTest {
    @Test fun escapeWorksWithoutFocusAndAfterFocusedContentIsRemoved() {
        val keys = DesktopEscapeKeyHandler()
        val back = UiBackDispatcher()
        var backs = 0
        back.register { backs++; true }
        var showFocusedChild by mutableStateOf(false)
        var clearFocus: () -> Unit = {}
        val scene = ImageComposeScene(320, 480) {
            val focusManager = LocalFocusManager.current
            SideEffect { clearFocus = { focusManager.clearFocus(force = true) } }
            Box(Modifier.fillMaxSize()) {
                if (showFocusedChild) {
                    val focus = remember { FocusRequester() }
                    Box(Modifier.fillMaxSize().focusRequester(focus).focusable())
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }
        }
        try {
            scene.settle()
            scene.escape(keys, back)
            assertEquals(1, backs, "没有焦点的页面也应响应 Esc")
            showFocusedChild = true
            scene.settle()
            scene.escape(keys, back)
            assertEquals(2, backs)
            clearFocus()
            scene.escape(keys, back)
            assertEquals(3, backs, "主动清除焦点不应禁用返回")
            showFocusedChild = false
            scene.settle()
            scene.escape(keys, back)
            assertEquals(4, backs, "上一页面的焦点节点移除后仍应返回")
        } finally {
            scene.close()
        }
    }

    @Test fun popupClosesBeforeNestedPageAndPageUsesTheSameHandlerAsGesture() {
        val keys = DesktopEscapeKeyHandler()
        val back = UiBackDispatcher()
        var parentBacks = 0
        var nestedBacks = 0
        var nested = true
        back.register { parentBacks++; true }
        back.register(enabled = { nested }) { nestedBacks++; nested = false; true }
        var popup by mutableStateOf(true)
        val scene = ImageComposeScene(320, 480) {
            keys.WindowNavigationEffect()
            Box(Modifier.fillMaxSize()) {
                if (popup) Popup(
                    onDismissRequest = { popup = false },
                    properties = PopupProperties(focusable = true)
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
        try {
            scene.settle()
            scene.escape(keys, back)
            scene.settle()
            assertFalse(popup)
            assertEquals(0, nestedBacks, "关闭弹窗的 Esc 不应穿透到子页面")
            assertEquals(0, parentBacks)
            scene.escape(keys, back)
            assertEquals(1, nestedBacks)
            assertEquals(0, parentBacks)
            nested = true
            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(0.8f, true))
            back.commitGesture()
            assertEquals(2, nestedBacks, "Esc 和返回手势应选择同一个嵌套处理器")
            scene.escape(keys, back)
            assertEquals(1, parentBacks)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.escape(keys: DesktopEscapeKeyHandler, back: UiBackDispatcher) {
        for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
            val event = KeyEvent(Key.Escape, type)
            keys.observe(event)
            if (!sendKeyEvent(event)) keys.handle(event, back::dispatchBack)
        }
    }

    private fun ImageComposeScene.settle() {
        repeat(8) { render(it * 16_000_000L).close() }
    }
}
