package com.rchdliang.copymanga_neo.ui.detail

import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus

/** 提示与执行共用本次多选已访问组的范围，跨组汇总且不影响未访问组。 */
internal data class ChapterDownloadChanges(
    val download: Set<String>,
    val delete: Set<String>,
    val cancel: Set<String>
)

internal fun chapterDownloadChanges(
    managedUuids: Set<String>,
    selectedUuids: Set<String>,
    statuses: Map<String, ChapterDownloadStatus>
): ChapterDownloadChanges {
    val selected = selectedUuids intersect managedUuids
    val downloaded =
        managedUuids.filter { statuses[it] == ChapterDownloadStatus.DOWNLOADED }.toSet()
    val active = managedUuids.filter {
        statuses[it] == ChapterDownloadStatus.WAITING ||
                statuses[it] == ChapterDownloadStatus.DOWNLOADING
    }.toSet()
    return ChapterDownloadChanges(
        selected - downloaded - active,
        downloaded - selected,
        active - selected
    )
}

/** 修改当前组勾选时保留其他组的选择，切换回来仍可继续编辑。 */
internal fun replaceVisibleSelection(
    selected: Set<String>, visible: Set<String>, replacement: Set<String>
): Set<String> = (selected - visible) + (replacement intersect visible)
