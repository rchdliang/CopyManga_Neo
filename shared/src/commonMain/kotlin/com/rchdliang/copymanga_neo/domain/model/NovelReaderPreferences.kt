package com.rchdliang.copymanga_neo.domain.model

/** 小说阅读设置独立于漫画；分页和滚动共用文字排版参数。 */
@kotlinx.serialization.Serializable
data class NovelReaderPreferences(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.65f,
    val paragraphSpacing: Float = 8f,
    val margin: Float = 16f,
    val font: String = "SYSTEM",
    val mode: String = "SCROLL",
    val columns: String = "AUTO",
    val theme: String = "SYSTEM",
    val keepScreenOn: Boolean = true,
    val systemFont: String? = null,
    // Retained for reading settings saved before the weight slider was introduced.
    val bold: Boolean = false,
    val fontWeight: Int = if (bold) 700 else 400
) {
    fun normalized() = copy(
        fontSize = fontSize.takeIf { it.isFinite() }?.coerceIn(12f, 36f) ?: 18f,
        lineHeight = lineHeight.takeIf { it.isFinite() }?.coerceIn(1.1f, 2.4f) ?: 1.65f,
        paragraphSpacing = paragraphSpacing.takeIf { it.isFinite() }?.coerceIn(0f, 24f) ?: 8f,
        margin = margin.takeIf { it.isFinite() }?.coerceIn(8f, 48f) ?: 16f,
        font = font.takeIf { it in setOf("SYSTEM", "SERIF", "SANS") } ?: "SYSTEM",
        fontWeight = fontWeight.coerceIn(100, 900),
        systemFont = systemFont?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 && it.none(Char::isISOControl) },
        mode = mode.takeIf { it in setOf("SCROLL", "PAGED") } ?: "SCROLL",
        columns = columns.takeIf { it in setOf("AUTO", "SINGLE", "DOUBLE") } ?: "AUTO",
        theme = theme.takeIf { it in setOf("SYSTEM", "LIGHT", "DARK", "PAPER") } ?: "SYSTEM"
    )
}
