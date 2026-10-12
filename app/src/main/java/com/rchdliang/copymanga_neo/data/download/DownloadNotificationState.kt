package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus

/** 通知和服务共享的队列快照；活动 I/O 退出前不提前释放前台服务。 */
internal data class DownloadNotificationState(
    val items: List<DownloadProgressInfo>,
    val activePathWord: String?,
    val pendingCommands: Int = 0
) {
    val unfinished = items.filter { it.status != DownloadTaskStatus.COMPLETED }
    val isRunning = activePathWord != null || pendingCommands > 0 || unfinished.any {
        it.status == DownloadTaskStatus.WAITING || it.status == DownloadTaskStatus.WAITING_FOR_WIFI || it.status == DownloadTaskStatus.IDLE ||
                it.status == DownloadTaskStatus.REQUESTING_PAGE || it.status == DownloadTaskStatus.DOWNLOADING
    }
    val primary = unfinished.firstOrNull { it.taskKey == activePathWord }
        ?: unfinished.firstOrNull { it.status == DownloadTaskStatus.DOWNLOADING }
        ?: unfinished.firstOrNull()
        ?: items.lastOrNull { it.status == DownloadTaskStatus.COMPLETED }
    val progress = ((primary?.overallProgress ?: 0f).coerceIn(0f, 1f) * 1000).toInt()
}
