package com.rchdliang.copymanga_neo.data.export

import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import com.rchdliang.copymanga_neo.domain.model.ComicExportStatus

/** 通知与应用内提示共用结果文案，包括已成功的输出和清理失败提示。 */
internal fun ComicExportState.resultMessage(): String = buildString {
    append(
        when (status) {
            ComicExportStatus.CANCELLED -> "导出已取消"
            ComicExportStatus.INTERRUPTED -> "导出已中断"
            else -> if (outputs.isEmpty() && failures.isNotEmpty()) "导出失败" else "导出完成"
        }
    )
    append("：成功 ${outputs.size} 项")
    if (failures.isNotEmpty()) append("，失败 ${failures.size} 项")
    if (status == ComicExportStatus.INTERRUPTED && current.isNotBlank()) append("。$current")
    else if (outputs.isEmpty() && failures.isNotEmpty()) append("。${failures.first().message}")
    if (cleanupWarning != null) append("。部分未完成文件无法清理，请到保存位置检查")
}
