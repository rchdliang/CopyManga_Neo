package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ConfirmRemovalDialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UnsavedChangesDialog
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class EditingDialogsUiTest {
    @Test fun continueAndDiscardAreDistinctAndNoActionRunsOnOpen() = runSkikoComposeUiTest(size = Size(360f, 480f)) {
        var saved = 0; var discarded = 0; var continued = 0
        setContent { MaterialTheme { UnsavedChangesDialog(onContinue = { continued++ }, onDiscard = { discarded++ }, onSave = { saved++ }) } }
        assertEquals(0, saved + discarded + continued)
        onNodeWithText("继续编辑").performClick()
        assertEquals(1, continued); assertEquals(0, discarded)
        onNodeWithText("不保存").performScrollTo().performClick()
        assertEquals(1, discarded); assertEquals(0, saved)
        onNodeWithText("保存并返回").performScrollTo().performClick()
        assertEquals(1, saved)
        saveRender("unsaved-dialog-desktop")
    }
    @Test fun busyDialogCannotSaveDiscardOrContinue() = runSkikoComposeUiTest {
        setContent { MaterialTheme { UnsavedChangesDialog(busy = true, onContinue = { error("busy") }, onDiscard = { error("busy") }, onSave = { error("busy") }) } }
        onNodeWithText("继续编辑").assertIsNotEnabled()
        onNodeWithText("不保存").assertIsNotEnabled()
        onNodeWithText("保存并返回").assertIsNotEnabled()
    }
    @Test fun destructiveConfirmationDoesNotActOnCancel() = runSkikoComposeUiTest {
        var deleted = 0; var dismissed = 0
        setContent { MaterialTheme { ConfirmRemovalDialog("删除下载？", "将删除 3 部作品的离线内容。", { deleted++ }, { dismissed++ }) } }
        assertEquals(0, deleted)
        onNodeWithText("取消").performClick()
        assertEquals(1, dismissed); assertEquals(0, deleted)
        onNodeWithText("删除").performClick()
        assertEquals(1, deleted)
    }
    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onAllNodes(isRoot())[1].captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/ui-audit/$name.png"); file.parentFile.mkdirs(); ImageIO.write(image, "png", file)
    }
}
