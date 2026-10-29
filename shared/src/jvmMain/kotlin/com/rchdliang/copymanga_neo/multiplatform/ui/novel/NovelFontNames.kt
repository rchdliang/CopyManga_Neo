package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.nio.ByteBuffer
import java.nio.charset.Charset

internal data class NovelFontName(val name: String, val language: String, val kind: Int = 1)

private fun fontNameLanguagePriority(language: String): Int {
    val tag = language.lowercase().replace('_', '-')
    return when {
        tag.startsWith("zh-hans") || tag == "zh-cn" || tag == "zh-sg" -> 0
        tag.startsWith("zh") -> 1
        tag.startsWith("en") -> 2
        else -> 3
    }
}

/** Prefer a localized family name, never the PostScript identifier (name ID 6). */
internal fun novelFontFamilyName(names: List<NovelFontName>): NovelFontName? = names
    .filter { it.kind in setOf(1, 16) && it.name.isNotBlank() }
    .minWithOrNull(compareBy<NovelFontName> { fontNameLanguagePriority(it.language) }.thenBy { if (it.kind == 16) 0 else 1 })

internal fun novelFontDisplayName(names: List<NovelFontName>): String? {
    val family = novelFontFamilyName(names) ?: return names.filter { it.kind == 4 }
        .minByOrNull { fontNameLanguagePriority(it.language) }?.name
    val label = novelFontFamilyLabel(family.name)
    val style = names.filter { it.kind in setOf(2, 17) }
        .minWithOrNull(compareBy<NovelFontName> { if (it.language == family.language) -1 else fontNameLanguagePriority(it.language) }
            .thenBy { if (it.kind == 17) 0 else 1 })?.name ?: return label
    return if (style.lowercase() in setOf("regular", "normal", "roman", "常规", "常規", "標準", "标准", "正常")) label
        else "$label · $style"
}

internal fun novelFontFamilyLabel(name: String): String = commonFontNames[name] ?: name

private val commonFontNames = mapOf(
    "SimSun" to "宋体", "NSimSun" to "新宋体", "SimHei" to "黑体",
    "KaiTi" to "楷体", "FangSong" to "仿宋", "Microsoft YaHei" to "微软雅黑",
    "DengXian" to "等线", "STXingkai" to "华文行楷", "STKaiti" to "华文楷体",
    "STSong" to "华文宋体", "STZhongsong" to "华文中宋", "STFangsong" to "华文仿宋",
    "Noto Sans SC" to "Noto 黑体（简体中文）", "Noto Sans CJK SC" to "Noto 黑体（简体中文）",
    "Noto Serif SC" to "Noto 宋体（简体中文）", "Noto Serif CJK SC" to "Noto 宋体（简体中文）",
    "Noto Sans TC" to "Noto 黑体（繁体中文）", "Noto Sans CJK TC" to "Noto 黑体（繁体中文）",
    "Noto Serif TC" to "Noto 宋体（繁体中文）", "Noto Serif CJK TC" to "Noto 宋体（繁体中文）",
    "Noto Sans CJK JP" to "Noto 黑体（日文）", "Noto Serif CJK JP" to "Noto 宋体（日文）",
    "Noto Sans CJK KR" to "Noto 黑体（韩文）", "Noto Serif CJK KR" to "Noto 宋体（韩文）"
)

/** Read localized OpenType name records, including format-1 language tags and TTC face indices. */
internal fun novelFontNames(buffer: ByteBuffer, ttcIndex: Int): List<NovelFontName> = runCatching {
    val data = novelOpenTypeTable(buffer, ttcIndex, 0x6e616d65) ?: return emptyList() // name
    fun u16(at: Int) = data.getShort(at).toInt() and 0xffff
    val format = u16(0)
    require(format in 0..1)
    val count = u16(2)
    val storage = u16(4)
    require(6L + count * 12L <= data.limit() && storage >= 6 + count * 12)
    fun stringAt(length: Int, offset: Int, charset: Charset): String {
        val start = storage + offset
        require(start.toLong() + length <= data.limit())
        val bytes = ByteArray(length)
        data.duplicate().apply { position(start); get(bytes) }
        return String(bytes, charset).trim().also { require(it.isNotEmpty() && it.none(Char::isISOControl) && '\ufffd' !in it) }
    }
    buildList {
        for (index in 0 until count) {
            runCatching record@ {
                val at = 6 + index * 12
                val platform = u16(at)
                val encoding = u16(at + 2)
                val language = u16(at + 4)
                val kind = u16(at + 6)
                if (kind !in setOf(1, 2, 4, 16, 17)) return@record
                val charset = when {
                    platform == 0 || platform == 3 && encoding in setOf(0, 1, 10) -> Charsets.UTF_16BE
                    platform == 1 && encoding == 0 -> Charset.forName("x-MacRoman")
                    else -> return@record
                }
                val tag = when {
                    language >= 0x8000 && format == 1 -> {
                        val tags = 6 + count * 12
                        val slot = language - 0x8000
                        require(slot < u16(tags) && tags.toLong() + 2 + u16(tags) * 4L <= storage)
                        stringAt(u16(tags + 2 + slot * 4), u16(tags + 4 + slot * 4), Charsets.UTF_16BE)
                    }
                    platform == 3 && language in setOf(0x0804, 0x1004) -> "zh-Hans"
                    platform == 3 && (language and 0x3ff) == 4 -> "zh-Hant"
                    platform == 3 && (language and 0x3ff) == 9 || platform == 1 && language == 0 -> "en"
                    else -> "und"
                }
                add(NovelFontName(stringAt(u16(at + 8), u16(at + 10), charset), tag, kind))
            }
        }
    }
}.getOrDefault(emptyList())
