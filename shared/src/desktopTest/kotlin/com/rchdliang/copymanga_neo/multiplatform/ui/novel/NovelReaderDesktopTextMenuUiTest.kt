package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.multiplatform.platform.DesktopPlatformHost
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class NovelReaderDesktopTextMenuUiTest {
    @Test fun mouseClickSearchDeliversSelectedText() = clickReaderAction("搜索")

    @Test fun mouseClickAnnotateDeliversSelectedText() = clickReaderAction("标注")

    @Test fun outsideClickDismissesMenuAndSelection() = clickReaderAction("outside")

    @Test fun touchLongPressSearchDeliversSelectedText() = clickReaderAction("搜索", touch = true)

    private fun clickReaderAction(label: String, touch: Boolean = false) {
        val oldFlag = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        try {
            runSkikoComposeUiTest(size = Size(600f, 400f)) {
                val menu = NovelReaderTextMenuState()
                val body = "Select this sentence for a reader action"
                val document = NovelTextDocument.decode(body.encodeToByteArray(), "UTF-8")
                val host = DesktopPlatformHost()
                var result: String? = null
                lateinit var selection: SelectionState
                setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalUiPlatform provides host) {
                            NovelReaderTextMenuHost(
                                menu, Unit, document,
                                onAnnotate = { result = "标注:${it.text}" },
                                onSearch = { result = "搜索:$it" },
                                modifier = Modifier.testTag("host")
                            ) {
                                selection = rememberSelectionState()
                                ObserveNovelReaderSelection(selection) {}
                                Box(Modifier.fillMaxSize().padding(top = 120.dp)) {
                                    SelectionContainer(state = selection) {
                                        Text(novelSourceText(body, 0), Modifier.testTag("text"))
                                    }
                                }
                            }
                        }
                    }
                }
                if (touch) onNodeWithTag("text").performTouchInput { longClick(Offset(40f, 10f)) }
                else onNodeWithTag("text").performMouseInput {
                    moveTo(Offset(8f, 10f)); press(); moveBy(Offset(100f, 0f)); release()
                }
                waitForIdle()
                val selected = selection.selectedTexts.joinToString("") { it.text }
                assertTrue(selected.isNotBlank())
                if (!touch) onNodeWithTag("text").performMouseInput { rightClick(Offset(40f, 10f)) }
                waitForIdle()
                if (label == "outside") {
                    onNodeWithTag("host").performMouseInput { click(Offset(500f, 350f)) }
                    waitForIdle()
                    assertFalse(menu.active)
                    assertTrue(selection.selectedTexts.isEmpty())
                    assertEquals(null, result)
                } else {
                    val action = onNodeWithText(label).assertIsEnabled()
                    if (touch) action.performTouchInput { click() } else action.performMouseInput { click() }
                    waitForIdle()
                    assertEquals("$label:$selected", result)
                }
            }
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = oldFlag
        }
    }

    @Test fun rightClickUsesReaderDropdownProvider() {
        val oldFlag = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        try {
            runSkikoComposeUiTest(size = Size(400f, 200f)) {
                val menu = NovelReaderTextMenuState()
                lateinit var selection: SelectionState
                setContent {
                    selection = rememberSelectionState()
                    CompositionLocalProvider(LocalTextContextMenuDropdownProvider provides menu) {
                        SelectionContainer(state = selection) {
                            Box(Modifier.fillMaxSize()) {
                                Text("Select this sentence", Modifier.testTag("text"))
                            }
                        }
                    }
                }
                onNodeWithTag("text").performMouseInput {
                    moveTo(Offset(8f, 10f))
                    press()
                    moveBy(Offset(70f, 0f))
                    release()
                }
                waitForIdle()
                assertTrue(selection.selectedTexts.isNotEmpty())
                onNodeWithTag("text").performMouseInput { rightClick(Offset(40f, 10f)) }
                waitForIdle()
                assertTrue(selection.selectedTexts.isNotEmpty())
                assertNotNull(menu.request)
            }
        } finally {
            ComposeFoundationFlags.isNewContextMenuEnabled = oldFlag
        }
    }
}
