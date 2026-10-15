package com.rchdliang.copymanga_neo.domain.model

/** 原始正文的 UTF-16 坐标，结束位置不包含在选区内。 */
@kotlinx.serialization.Serializable
data class NovelTextRange(
    val startLine: Int, val startCharacter: Int,
    val endLine: Int, val endCharacter: Int
) {
    val valid: Boolean
        get() = startLine >= 0 && startCharacter >= 0 &&
                endLine >= startLine && endCharacter >= 0 &&
                (endLine > startLine || endCharacter > startCharacter)
}

@kotlinx.serialization.Serializable
data class NovelTextSelection(val text: String, val range: NovelTextRange)

/** 标注按账号、请求模式、作品及分卷隔离，正文刷新后保留原文以核对位置。 */
@kotlinx.serialization.Serializable
data class NovelAnnotation(
    val id: String, val owner: String, val mode: String, val path: String,
    val volume: String, val volumeName: String,
    val text: String, val range: NovelTextRange,
    val style: String = "PRIMARY", val note: String = "",
    val createdAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = createdAt
)
