package com.rchdliang.copymanga_neo.data.remote

import android.content.Context
import androidx.core.content.edit
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import com.rchdliang.copymanga_neo.domain.repository.RiskControlNotice
import com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 应用级风控闸门：记录触发时间，并取消已经在途的站点请求。 */
@Singleton
class RiskControlGate @Inject constructor(@ApplicationContext context: Context) :
    RiskControlRepository {
    private companion object {
        val RETRY_AFTER_MILLIS = TimeUnit.HOURS.toMillis(1)
    }

    private val preferences = context.getSharedPreferences("risk_control", Context.MODE_PRIVATE)
    private val lock = Any()
    private val calls = mutableSetOf<Call>()
    private var hasLaunchedActivity = false
    private val state = MutableStateFlow(preferences.getString("reason", null)?.let {
        RiskControlNotice(preferences.getLong("detected_at", 0L), it)
    })
    override val notice = state.asStateFlow()

    override fun resetForAppLaunch(isFreshActivity: Boolean) = synchronized(lock) {
        val firstLaunchInProcess = !hasLaunchedActivity
        hasLaunchedActivity = true
        if (firstLaunchInProcess || isFreshActivity) clearBlock()
    }

    override fun resumeIfExpired(): Boolean = synchronized(lock) {
        val detectedAt = state.value?.detectedAt ?: return@synchronized false
        if (System.currentTimeMillis() - detectedAt < RETRY_AFTER_MILLIS) {
            return@synchronized false
        }
        clearBlock()
        true
    }

    fun register(call: Call) = synchronized(lock) {
        state.value?.let { throw RiskControlException(it.reason) }
        calls.add(call)
    }

    fun unregister(call: Call) = synchronized(lock) { calls.remove(call) }

    fun block(reason: String, source: Call) {
        synchronized(lock) {
            if (state.value == null) {
                val value = RiskControlNotice(System.currentTimeMillis(), reason)
                preferences.edit {
                    putString("reason", value.reason)
                        .putLong("detected_at", value.detectedAt)
                }
                state.value = value
            }
            calls.filter { it !== source }.forEach { it.cancel() }
        }
    }

    override fun resumeByUser() = synchronized(lock) { clearBlock() }

    private fun clearBlock() {
        preferences.edit { remove("reason").remove("detected_at") }
        state.value = null
    }
}
