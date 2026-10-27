package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import java.awt.datatransfer.DataFlavor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 注入假平台剪贴板，不读取或覆盖用户的系统剪贴板。 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class TextClipboardTest {
    @Test
    fun desktopUsesProvidedClipboardAndPreservesMultilineUnicode() = runSkikoComposeUiTest {
        var entry: ClipEntry? = null
        val platformClipboard = object : Clipboard {
            override suspend fun getClipEntry() = entry
            override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
        }
        lateinit var writer: TextClipboard
        setContent {
            CompositionLocalProvider(LocalClipboard provides platformClipboard) {
                writer = rememberTextClipboard()
            }
        }
        val text = "第一段🙂\n第二段\t原样保留"
        runOnIdle { runBlocking { writer.setText(AnnotatedString(text)) } }
        val transferable = assertNotNull(assertNotNull(entry).asAwtTransferable)
        assertEquals(text, transferable.getTransferData(DataFlavor.stringFlavor))
    }
}
