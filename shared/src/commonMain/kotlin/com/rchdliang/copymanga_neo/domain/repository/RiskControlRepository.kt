package com.rchdliang.copymanga_neo.domain.repository

import kotlinx.coroutines.flow.StateFlow
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException

data class RiskControlNotice(val detectedAt: Long, val reason: String)

class RiskControlException(val reason: String) : PlatformIOException("code 210: $reason")

interface RiskControlRepository {
    val notice: StateFlow<RiskControlNotice?>

    /** 新进程或重新打开主界面时解除旧暂停，由后续请求重新判断是否仍受限制。 */
    fun resetForAppLaunch(isFreshActivity: Boolean)

    /** 回到前台时，距离最近一次风控满一小时才解除暂停。 */
    fun resumeIfExpired(): Boolean

    /** 用户主动重试。 */
    fun resumeByUser()
}
