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
    val pairingRestarts: Map<String, List<Int>> = emptyMap(),
    val sharpness: Float = 0f,
    val sharpnessRestore: Float = 0.20f,
    val restorationMode: String = "OFF",
    val restorationLastMode: String = "RESTORE",
    val restoreLevel: String = "M",
    val restoreSoftLevel: String = "M"
) {
    fun normalized() = copy(
        autoIntervalSeconds = autoIntervalSeconds.coerceIn(2, 30),
        imageRetries = imageRetries.coerceIn(0, 3),
        imageTimeoutSeconds = imageTimeoutSeconds.coerceIn(5, 60),
        preloadPages = preloadPages.coerceIn(0, 3),
        sharpnessRestore = if (sharpnessRestore.isFinite()) sharpnessRestore.coerceIn(0.01f, 1f) else 0.20f,
        sharpness = if (sharpness.isFinite()) sharpness.coerceIn(0f, 1f) else 0f,
        restorationMode = restorationMode.takeIf { it in setOf("OFF", "RESTORE", "SOFT") } ?: "OFF",
        restorationLastMode = restorationLastMode.takeIf { it in setOf("RESTORE", "SOFT") } ?: "RESTORE",
        restoreLevel = restoreLevel.takeIf { it in RESTORE_LEVEL_NAMES } ?: "M",
        restoreSoftLevel = restoreSoftLevel.takeIf { it in RESTORE_LEVEL_NAMES } ?: "M",
        pageLayout = pageLayout.takeIf { it in setOf("SINGLE", "DOUBLE", "AUTO") } ?: "SINGLE",
        pairingRestarts = pairingRestarts.mapValues { (_, pages) -> pages.filter { it >= 0 }.distinct().sorted() }.filterValues { it.isNotEmpty() }
    )
}

/** 两种修复模式共用档位名称，各自保存选择。 */
internal val RESTORE_LEVEL_NAMES = setOf("S", "M", "L", "VL", "UL")

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
