package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface ShelfReadingRepository {
    val snapshot: StateFlow<ShelfReadingSnapshot>
    val error: StateFlow<String?>
    /** 只接受正在显示的正文上报，预加载和打开详情不算开始阅读。 */
    fun record(session: LocalReadingSession, reading: LocalReadingRecord)
    /** 只检查已有追平基线或待确认完成的作品，不为整个书架逐本请求目录。 */
    fun refresh(session: LocalReadingSession, kind: String, paths: Set<String>)
}
