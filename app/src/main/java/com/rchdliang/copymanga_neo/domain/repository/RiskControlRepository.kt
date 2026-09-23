package com.rchdliang.copymanga_neo.domain.repository

import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

data class RiskControlNotice(val detectedAt: Long, val reason: String)

class RiskControlException(val reason: String) : IOException("code 210: $reason")

interface RiskControlRepository {
    val notice: StateFlow<RiskControlNotice?>

    /** 只能由用户明确点击重试恢复，不因刷新列表、切页或重启自动解除。 */
    fun resumeByUser()
}
