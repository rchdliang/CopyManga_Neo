package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginDialogScrollTest {
    @Test fun typingPasswordKeepsShortDialogViewportStable() = checkViewport(400f)
    @Test fun typingPasswordInVeryShortDialog() = checkViewport(300f)
    @Test fun typingPasswordInTinyDialog() = checkViewport(250f)

    @Test fun retryUsesEditedPassword() = runSkikoComposeUiTest {
        val attempts = mutableListOf<Pair<String, String>>()
        setContent {
            MaterialTheme {
                LoginDialog(false, null, null, { null }, true, true,
                    { user, password, _, _ -> attempts += user to password }, {})
            }
        }
        onNode(hasSetTextAction() and hasText("账号")).performTextInput("dummy-account")
        val password = onNode(hasSetTextAction() and hasText("密码"))
        password.performTextInput("wrong-password")
        onNodeWithText("登录", substring = false).performClick()
        password.performTextReplacement("correct-password")
        onNodeWithText("登录", substring = false).performClick()
        assertEquals(listOf("dummy-account" to "wrong-password", "dummy-account" to "correct-password"), attempts)
    }

    @Test fun shrinkingDialogKeepsFocusedPasswordVisibleAndEditable() = runSkikoComposeUiTest {
        var submittedPassword: String? = null
        var dialogHeight by mutableStateOf(700.dp)
        setContent {
            MaterialTheme {
                LoginDialog(false, null, "dummy-account", { null }, false, false,
                    { _, password, _, _ -> submittedPassword = password }, {},
                    modifier = Modifier.heightIn(max = dialogHeight))
            }
        }
        val password = onNode(hasSetTextAction() and hasText("密码"))
        password.performTextInput("initial-password")
        runOnIdle { dialogHeight = 250.dp }
        waitForIdle()
        assertFullyVisible(password)
        password.assertIsFocused()
        password.performTextReplacement("edited-password")
        password.assertIsFocused()
        onNodeWithContentDescription("显示密码").performTouchInput { click() }
        password.assertTextContains("edited-password")
        onNodeWithContentDescription("隐藏密码").performTouchInput { click() }
        // The desktop icon button can take focus. Return to the field before IME submission.
        password.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        password.assertIsFocused()
        password.performImeAction()
        assertEquals("edited-password", submittedPassword)
        onNodeWithText("自动登录").performScrollTo().assertIsDisplayed()
        onNodeWithText("登录", substring = false).assertIsDisplayed()
    }

    @Test fun togglingPreferencesThenRefocusingPasswordSettlesInShortWindow() = runSkikoComposeUiTest {
        var dialogHeight by mutableStateOf(300.dp)
        setContent {
            MaterialTheme {
                LoginDialog(false, null, "dummy-account", { null }, false, false,
                    { _, _, _, _ -> }, {}, modifier = Modifier.heightIn(max = dialogHeight))
            }
        }
        val password = onNode(hasSetTextAction() and hasText("密码"))
        password.performTextInput("dummy-password")
        repeat(3) {
            for (label in listOf("记住密码", "自动登录")) {
                onNodeWithText(label).performScrollTo()
                waitForIdle()
                val before = onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
                mainClock.autoAdvance = false
                onNodeWithText(label).performTouchInput { click() }
                repeat(30) {
                    mainClock.advanceTimeByFrame()
                    assertEquals(before, onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top, 1f,
                        "Preference continued scrolling after toggle")
                }
                mainClock.autoAdvance = true
            }
            password.performScrollTo().performTouchInput { click() }
            for (height in listOf(290, 275, 260, 250, 265, 280, 300)) {
                runOnIdle { dialogHeight = height.dp }
                mainClock.advanceTimeByFrame()
            }
            waitForIdle()
            val before = password.fetchSemanticsNode().boundsInRoot.top
            mainClock.autoAdvance = false
            repeat(60) {
                mainClock.advanceTimeByFrame()
                assertEquals(before, password.fetchSemanticsNode().boundsInRoot.top, 1f,
                    "Password continued scrolling after viewport settled")
            }
            mainClock.autoAdvance = true
            assertFullyVisible(password)
        }
    }

    @Test fun largeFontPasswordAndLongInputRemainUsable() = runSkikoComposeUiTest(size = Size(600f, 500f)) {
        var submitted: String? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    LoginDialog(false, null, "dummy-account", { null }, false, false,
                        { _, password, _, _ -> submitted = password }, {})
                }
            }
        }
        val password = onNode(hasSetTextAction() and hasText("密码"))
        password.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val text = "dummy-long-password-".repeat(10)
        password.performTextInput(text)
        waitForIdle()
        assertFullyVisible(password)
        password.assertIsFocused().performImeAction()
        assertEquals(text, submitted)
        for (label in listOf("记住密码", "自动登录")) {
            onNodeWithText(label).performScrollTo().performTouchInput { click() }
            waitForIdle()
            onNodeWithText(label).assertIsDisplayed()
            val top = onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
            mainClock.autoAdvance = false
            repeat(30) {
                mainClock.advanceTimeByFrame()
                assertEquals(top, onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top, 1f)
            }
            mainClock.autoAdvance = true
        }
    }

    private fun assertFullyVisible(field: SemanticsNodeInteraction) {
        val full = field.getUnclippedBoundsInRoot()
        val clipped = field.getBoundsInRoot()
        assertEquals((full.bottom - full.top).value, (clipped.bottom - clipped.top).value,
            1f, "Focused password must be fully visible")
    }

    private fun checkViewport(height: Float) = runSkikoComposeUiTest(size = Size(480f, height)) {
        setContent {
            MaterialTheme {
                LoginDialog(false, null, null, { null }, false, false, { _, _, _, _ -> }, {})
            }
        }
        val account = onNode(hasSetTextAction() and hasText("账号"))
        val password = onNode(hasSetTextAction() and hasText("密码"))
        account.performScrollTo().performClick().performTextInput("dummy-account")
        password.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        waitForIdle()
        assertFullyVisible(password)
        val initial = password.fetchSemanticsNode().boundsInRoot.top
        mainClock.autoAdvance = false
        for (character in "dummy-password") {
            password.performTextInput(character.toString())
            repeat(12) {
                mainClock.advanceTimeByFrame()
                assertEquals(initial, password.fetchSemanticsNode().boundsInRoot.top, 1f,
                    "Password field jumped during a typing animation frame")
            }
            password.assertIsFocused()
            assertEquals(initial, password.fetchSemanticsNode().boundsInRoot.top, 1f,
                "Password field moved on character input")
        }
        password.performTextReplacement("dummy")
        mainClock.advanceTimeBy(300)
        password.assertIsFocused()
        assertEquals(initial, password.fetchSemanticsNode().boundsInRoot.top, 1f,
            "Password field moved after deleting text")
    }
}
