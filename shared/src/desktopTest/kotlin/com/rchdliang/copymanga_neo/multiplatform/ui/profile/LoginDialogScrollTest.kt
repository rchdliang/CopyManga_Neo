package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
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
