package com.rchdliang.copymanga_neo.domain.model

/** 原图的临时副本；格式由文件内容确定，不依赖 URL 后缀。 */
@kotlinx.serialization.Serializable
data class ReaderExportImage(val filePath: String, val mimeType: String, val displayName: String)
