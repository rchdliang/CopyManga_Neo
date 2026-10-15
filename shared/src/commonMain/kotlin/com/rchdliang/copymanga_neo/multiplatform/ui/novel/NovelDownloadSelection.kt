package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.multiplatform.ui.detail.chapterDownloadChanges

/** 完整缓存覆盖队列状态；未完整缓存仍可通过勾选继续下载。 */
internal fun novelVolumeStatuses(state: NovelUiState): Map<String, ChapterDownloadStatus> =
    buildMap {
        state.downloadTask?.volumes?.forEach { put(it.volume.id, it.status) }
        state.cachedVolumes.filter { it.offlineReady }.forEach {
            put(it.volume.id, ChapterDownloadStatus.DOWNLOADED)
        }
    }

/** 与漫画使用同一套勾选语义：保留已选下载，取消或删除范围内未选中的卷。 */
internal fun novelDownloadChanges(state: NovelUiState) = chapterDownloadChanges(
    state.managedVolumeIds, state.selectedVolumeIds.orEmpty(), novelVolumeStatuses(state)
)
