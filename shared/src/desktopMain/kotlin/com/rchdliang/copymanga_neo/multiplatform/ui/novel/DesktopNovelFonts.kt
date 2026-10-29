package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.text.Collator
import java.util.Locale

/** Uses the same system font manager as rendering, including the Native Image desktop. */
internal object DesktopNovelFonts {
    private val families = NovelFontFamilyCache()
    private val fonts by lazy {
        val manager = FontMgr.default
        (0 until manager.familiesCount).map(manager::getFamilyName)
            .filter(String::isNotBlank).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            .associateBy { "desktop:$it" }
    }

    private val displayFonts by lazy {
        val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        fonts.map { (id, name) ->
            val localized = runCatching {
                FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL)?.use { face ->
                    novelFontFamilyName(face.familyNames.map { NovelFontName(it.name, it.language) })?.name
                }
            }.getOrNull()
            NovelSystemFont(id, novelFontFamilyLabel(localized ?: name))
        }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    suspend fun list(): List<NovelSystemFont> = withContext(Dispatchers.IO) { displayFonts }

    @OptIn(ExperimentalTextApi::class)
    fun family(id: String, weight: Int): FontFamily? = runCatching {
        val value = weight.coerceIn(100, 900)
        fonts[id]?.let { name ->
            families.getOrPut(id, value) {
                FontFamily(SystemFont(
                    identity = name, weight = FontWeight(value),
                    variationSettings = FontVariation.Settings(FontVariation.weight(value))
                ))
            }
        }
    }.getOrNull()
}
