package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.fonts.FontVariationAxis

/** Checking cmap alone misses fonts that map letters to deliberately empty clock glyphs. */
internal fun androidNovelFontHasText(font: Font): Boolean = runCatching {
    val glyphs = novelFontLetterGlyphs(font.buffer, font.ttcIndex) ?: return true
    val paint = Paint().apply { textSize = 32f }
    val bounds = RectF()
    // Clock fonts can retain a handful of accent/letter remnants; one visible glyph is insufficient.
    novelFontHasReadableLetters(glyphs) { glyph ->
        font.getGlyphBounds(glyph, paint, bounds)
        !bounds.isEmpty
    }
}.getOrDefault(true)

internal fun androidNovelTypeface(font: Font, weight: Int): Typeface {
    val value = weight.coerceIn(100, 900)
    val range = novelFontWeightAxis(font.buffer, font.ttcIndex)
    val face = if (range == null) font else {
        val coordinate = value.toFloat().coerceIn(range)
        val axes = font.axes.orEmpty().filter { it.tag != "wght" } + FontVariationAxis("wght", coordinate)
        Font.Builder(font).setFontVariationSettings(axes.toTypedArray()).setWeight(coordinate.toInt()).build()
    }
    val typeface = Typeface.CustomFallbackBuilder(FontFamily.Builder(face).build())
        // The font's axis range must not dictate the weight of unrelated fallback fonts.
        .setStyle(FontStyle(value, face.style.slant)).build()
    return if (range == null) Typeface.create(typeface, value, face.style.slant == FontStyle.FONT_SLANT_ITALIC)
        else typeface
}
