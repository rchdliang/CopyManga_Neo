package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import android.graphics.fonts.SystemFonts
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.text.Collator
import java.util.Locale

/** Only resolve fonts returned by the OS, never paths supplied by imported preferences. */
internal object AndroidNovelFonts {
    private val families = NovelFontFamilyCache()
    private val fonts by lazy {
        SystemFonts.getAvailableFonts().filter { it.file != null }
            .sortedBy { abs(it.style.weight - 400) + it.style.slant * 1000 }
            .distinctBy { it.file!!.name to it.ttcIndex }
            .filter(::androidNovelFontHasText)
            .associateBy { "android:${it.file!!.name}:${it.ttcIndex}" }
    }
    private val displayFonts by lazy {
        val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        fonts.mapNotNull { (id, font) ->
            novelFontDisplayName(novelFontNames(font.buffer, font.ttcIndex))?.let { NovelSystemFont(id, it) }
        }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    suspend fun list(): List<NovelSystemFont> = withContext(Dispatchers.IO) { displayFonts }

    fun family(id: String, weight: Int): FontFamily? = runCatching {
        val font = fonts[id] ?: return null
        val value = weight.coerceIn(100, 900)
        families.getOrPut(id, value) {
            FontFamily(androidNovelTypeface(font, value))
        }
    }.getOrNull()
}
