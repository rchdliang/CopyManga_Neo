package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ProfileEditorUiTest {
    private val profile = EditableProfile("测试昵称", null, "avatars/current.jpg", 1)

    @Test fun genderOnlyChangeEnablesSaveAndFailureKeepsDraft() = runSkikoComposeUiTest {
        var error by mutableStateOf<String?>(null)
        val submissions = mutableListOf<Pair<String, Int?>>()
        setContent {
            MaterialTheme {
                ProfileEditorContent(profile, false, false, false, null, false, error, {}, {}, {},
                    { name, gender -> submissions += name to gender; error = "保存失败，请重试" }, {},
                    Modifier.width(480.dp).heightIn(max = 650.dp))
            }
        }
        onNodeWithText("保存修改").assertIsNotEnabled()
        onNodeWithText("女").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("保存修改").performClick()
        assertEquals(listOf<Pair<String, Int?>>("测试昵称" to 2), submissions)
        onNodeWithText("女").performScrollTo().assertIsSelected()
        onNode(hasSetTextAction() and hasText("昵称")).performTextReplacement("新的昵称")
        onNodeWithText("保存修改").performClick()
        assertEquals<Pair<String, Int?>>("新的昵称" to 2, submissions.last())
        onNode(hasSetTextAction() and hasText("昵称")).assertTextContains("新的昵称")
    }

    @Test fun avatarCanBeRestoredAndNicknameOnlySaveLeavesGenderUntouched() = runSkikoComposeUiTest {
        var avatarChanged by mutableStateOf(false)
        var imageBusy by mutableStateOf(false)
        var result: Pair<String, Int?>? = null
        setContent {
            MaterialTheme {
                ProfileEditorContent(profile, false, false, imageBusy, null, avatarChanged, null,
                    { avatarChanged = true }, { avatarChanged = false }, {},
                    { name, gender -> result = name to gender }, {}, Modifier.width(480.dp).heightIn(max = 650.dp))
            }
        }
        onNodeWithContentDescription("更换头像").performClick()
        onNodeWithText("保存修改").assertIsEnabled()
        runOnIdle { imageBusy = true }
        onNodeWithText("保存修改").assertIsNotEnabled()
        runOnIdle { imageBusy = false }
        onNodeWithText("还原头像").performClick()
        onNodeWithText("保存修改").assertIsNotEnabled()
        val field = onNode(hasSetTextAction() and hasText("昵称"))
        field.performTextReplacement("")
        onNodeWithText("保存修改").assertIsNotEnabled()
        field.performTextReplacement("  新昵称  ")
        onNodeWithText("保存修改").performClick()
        assertEquals<Pair<String, Int?>?>("新昵称" to null, result)
    }

    @Test fun wideLightLayout() = checkLayout(560, 720, false)
    @Test fun shortDarkLayoutKeepsFieldsAndSaveReachable() = checkLayout(360, 340, true)

    private fun checkLayout(width: Int, height: Int, dark: Boolean) = runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        ProfileEditorContent(profile, false, false, false, null, false, null, {}, {}, {}, { _, _ -> }, {},
                            Modifier.fillMaxWidth().heightIn(max = (height - 32).dp))
                    }
                }
            }
        }
        saveRender(if (dark) "compact-dark" else "wide-light")
        if (height < 480) {
            val field = onNode(hasSetTextAction() and hasText("昵称")).assertIsDisplayed()
            val full = field.getUnclippedBoundsInRoot()
            val clipped = field.getBoundsInRoot()
            assertTrue(kotlin.math.abs((full.bottom - full.top).value - (clipped.bottom - clipped.top).value) < 1f,
                "Compact layout should expose the whole nickname field before scrolling")
        }
        onNode(hasSetTextAction() and hasText("昵称")).performScrollTo().assertIsDisplayed()
        onNodeWithText("女").performScrollTo().performClick().assertIsSelected()
        val save = onNodeWithText("保存修改").assertIsDisplayed().assertIsEnabled()
        val bounds = save.getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom.value <= height)
        onNodeWithContentDescription("关闭编辑资料").assertIsDisplayed()
    }

    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) rendered.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/profile-editor/$name.png")
        output.parentFile.mkdirs()
        ImageIO.write(rendered, "png", output)
    }
}
