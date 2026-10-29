package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily

data class NovelSystemFont(val id: String, val name: String)

/**
 * OS services used by the complete shared novel reader. Application hosts supply real adapters;
 * reading state, pagination, selection, annotations and search remain in common code.
 */
interface NovelReaderPlatform {
    /** Restore the previous OS window/idle/IME policy when this effect leaves composition. */
    @Composable
    fun ReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean, lightBars: Boolean)

    /** Android overrides this with statusBarsIgnoringVisibility to keep floating controls stable. */
    @Composable
    fun statusBarControlInsets(): WindowInsets = WindowInsets.statusBars

    fun plainTextFromHtml(html: String): String
    fun formatLineHeight(value: Float): String

    val supportsSystemFonts: Boolean get() = false
    suspend fun systemFonts(): List<NovelSystemFont> = emptyList()
    /** Unknown or unavailable identifiers must fall back without opening arbitrary files. */
    fun systemFontFamily(id: String, weight: Int): FontFamily? = null
}

val LocalNovelReaderPlatform = staticCompositionLocalOf<NovelReaderPlatform?> { null }

@Composable
internal fun novelReaderPlatform(): NovelReaderPlatform? {
    val platform = LocalNovelReaderPlatform.current
    check(platform != null || LocalInspectionMode.current) {
        "Provide NovelReaderPlatform at the application root before rendering shared novel screens."
    }
    return platform
}
