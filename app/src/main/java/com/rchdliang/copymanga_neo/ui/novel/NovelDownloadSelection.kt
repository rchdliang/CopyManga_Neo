package com.rchdliang.copymanga_neo.ui.novel

import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.ui.detail.chapterDownloadChanges

/** 完整主动下载覆盖队列状态；阅读缓存仍可勾选下载。 */
internal fun novelVolumeStatuses(state: NovelUiState): Map<String, ChapterDownloadStatus> =
    buildMap {
        state.downloadTask?.volumes?.forEach { put(it.volume.id, it.status) }
        state.cachedVolumes.filter { it.visibleInDownloads && it.offlineReady }.forEach {
            put(it.volume.id, ChapterDownloadStatus.DOWNLOADED)
        }
    }

/** 与漫画使用同一套勾选语义：保留已选下载，取消或删除范围内未选中的卷。 */
internal fun novelDownloadChanges(state: NovelUiState) = chapterDownloadChanges(
    state.managedVolumeIds, state.selectedVolumeIds.orEmpty(), novelVolumeStatuses(state)
)
