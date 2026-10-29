package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.FontLoader
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NovelVariableFontTest {
    @Suppress("DEPRECATION")
    @Test
    fun installedVariableFontReceivesIntermediateWeightOnItsNativeAxis() {
        val variable = runBlocking { DesktopNovelFonts.list() }.firstOrNull { font ->
            FontMgr.default.matchFamilyStyle(font.id.removePrefix("desktop:"), FontStyle.NORMAL)?.use {
                it.variationAxes?.any { axis -> axis.tag == "wght" && axis.minValue <= 553 && axis.maxValue >= 554 } == true
            } == true
        }
        assumeTrue("Requires an installed variable font with a weight axis", variable != null)
        for (weight in listOf(553, 554)) {
            val family = DesktopNovelFonts.family(variable!!.id, weight) as FontListFontFamily
            val typeface = FontLoader().load(family.fonts.single())
            val axis = assertNotNull(typeface.variations?.firstOrNull { it.tag == "wght" })
            assertEquals(weight.toFloat(), axis.value, 0.01f)
        }
    }

    @Test
    fun androidWeightAxisReaderHandlesRealVariableStaticAndCollectionFonts() {
        val file = File("C:/Windows/Fonts/bahnschrift.ttf")
        assumeTrue("Windows system font fixture", file.isFile)
        val buffer = ByteBuffer.wrap(file.readBytes()).apply { position(7) }
        assertEquals(300f..700f, novelFontWeightAxis(buffer, 0))
        assertEquals(7, buffer.position())
        assertNull(novelFontWeightAxis(buffer, 1))
        for (name in listOf("segoeui.ttf", "simsun.ttc")) {
            val static = File("C:/Windows/Fonts/$name")
            if (static.isFile) assertNull(novelFontWeightAxis(ByteBuffer.wrap(static.readBytes()), 0))
        }
        assertNull(novelFontWeightAxis(ByteBuffer.allocate(3), 0))
    }
}
