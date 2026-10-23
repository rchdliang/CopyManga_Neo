package com.rchdliang.copymanga_neo.domain.model

/** 阅读体验开关独立于每部漫画的方向和适配设置；自动阅读不会自动启动。 */
@kotlinx.serialization.Serializable
data class ReaderToolsSettings(
    val inlineComments: Boolean = true,
    val continuousChapters: Boolean = true,
    val volumeKeys: Boolean = false,
    val autoIntervalSeconds: Int = 8,
    val imageRetries: Int = 2,
    val imageTimeoutSeconds: Int = 15,
    val preloadPages: Int = 2,
    val pageLayout: String = "SINGLE",
    val pageAnimation: Boolean = true,
    val pairingRestarts: Map<String, List<Int>> = emptyMap()
) {
    fun normalized() = copy(
        autoIntervalSeconds = autoIntervalSeconds.coerceIn(2, 30),
        imageRetries = imageRetries.coerceIn(0, 3),
        imageTimeoutSeconds = imageTimeoutSeconds.coerceIn(5, 60),
        preloadPages = preloadPages.coerceIn(0, 3),
        pageLayout = pageLayout.takeIf { it in setOf("SINGLE", "DOUBLE", "AUTO") } ?: "SINGLE",
        pairingRestarts = pairingRestarts.mapValues { (_, pages) -> pages.filter { it >= 0 }.distinct().sorted() }.filterValues { it.isNotEmpty() }
    )
}

@kotlinx.serialization.Serializable
data class ReaderBookmark(
    val comicPathWord: String,
    val chapterUuid: String,
    val chapterName: String,
    val group: String,
    val page: Int,
    val createdAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    val fraction: Double = 0.0
)
