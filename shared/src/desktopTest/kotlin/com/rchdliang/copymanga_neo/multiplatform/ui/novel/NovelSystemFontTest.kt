package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.multiplatform.platform.DesktopPlatformHost
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NovelSystemFontTest {
    @Test
    fun weightSliderAndFontNamePreviewUseTheSelectedWeightWithoutSearchOrRadioControls() {
        val host = DesktopPlatformHost()
        val mono = runBlocking { host.systemFonts() }.first {
            it.name in setOf("Consolas", "Courier New", "Menlo", "DejaVu Sans Mono", "Liberation Mono")
        }
        val platform = object : NovelReaderPlatform by host {
            override suspend fun systemFonts() = listOf(mono)
        }
        val preferences = mutableStateOf(NovelReaderPreferences())
        runSkikoComposeUiTest(size = Size(700f, 1000f)) {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalNovelReaderPlatform provides platform) {
                        Column { NovelReaderSettings(preferences.value) { preferences.value = it } }
                    }
                }
            }
            onNode(SemanticsMatcher("Weight slider") {
                it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 100f..900f
            }).performSemanticsAction(SemanticsActions.SetProgress) { it(553f) }
            runOnIdle { assertEquals(553, preferences.value.fontWeight) }
            onNodeWithText("选择系统字体").performClick()
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(mono.name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(host.systemFontFamily(mono.id, 553), layouts.single().layoutInput.style.fontFamily)
            assertEquals(FontWeight(553), layouts.single().layoutInput.style.fontWeight)
            onNodeWithText(mono.name).performClick()
            runOnIdle { assertEquals(mono.id, preferences.value.systemFont) }
        }
    }

    @Test
    fun installedFontIsUsedForTextAndPaginationAndMissingFontsFallBack() {
        val host = DesktopPlatformHost()
        val fonts = runBlocking { host.systemFonts() }
        val mono = assertNotNull(fonts.firstOrNull {
            it.name in setOf("Consolas", "Courier New", "Menlo", "DejaVu Sans Mono", "Liberation Mono")
        })
        assertNull(host.systemFontFamily("desktop:MissingFontForReaderTest", 400))
        assertNull(host.systemFontFamily("android:Roboto-Regular.ttf:0", 400))
        val body = "iiii iii ii i ".repeat(300)
        val document = NovelReaderDocument(NovelTextDocument.decode(body.encodeToByteArray(), "UTF-8"), emptyList())
        val preferences = mutableStateOf(NovelReaderPreferences())
        var pages = NovelPagination()
        var style = TextStyle.Default
        var narrowWidth = 0
        var wideWidth = 0
        runSkikoComposeUiTest(size = Size(600f, 400f)) {
            setContent {
                CompositionLocalProvider(LocalNovelReaderPlatform provides host) {
                    style = novelTextStyle(preferences.value)
                    val measurer = rememberTextMeasurer()
                    narrowWidth = measurer.measure("iiii", style).size.width
                    wideWidth = measurer.measure("WWWW", style).size.width
                    pages = rememberNovelPagination(document, preferences.value, 280, 200)
                }
            }
            waitUntil { pages.complete }
            val originalPages = pages.pages
            runOnIdle { preferences.value = preferences.value.copy(systemFont = mono.id, fontWeight = 553) }
            waitUntil { pages.complete && pages.pages !== originalPages }
            runOnIdle {
                assertEquals(narrowWidth, wideWidth, "Selected system font must actually be monospaced")
                assertEquals(FontWeight(553), style.fontWeight)
                assertTrue(pages.pages.size > originalPages.size, "Switching to wider glyphs must repaginate")
                assertEquals(body, pages.pages.flatMap { it.blocks }.joinToString("") { it.text })
                preferences.value = preferences.value.copy(systemFont = "desktop:MissingFontForReaderTest")
            }
            waitForIdle()
            runOnIdle { assertEquals(FontFamily.Default, style.fontFamily) }
        }
    }
}
