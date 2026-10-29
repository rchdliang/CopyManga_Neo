package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.ui.text.font.FontFamily

/** Slider gestures should not retain a native font instance for every visited weight forever. */
internal class NovelFontFamilyCache {
    private val entries = object : LinkedHashMap<Pair<String, Int>, FontFamily>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, FontFamily>?) = size > 64
    }

    @Synchronized
    fun getOrPut(id: String, weight: Int, create: () -> FontFamily): FontFamily =
        entries.getOrPut(id to weight, create)
}
