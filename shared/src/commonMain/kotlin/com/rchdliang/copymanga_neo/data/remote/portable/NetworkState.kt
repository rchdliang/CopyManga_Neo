package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

/** Host application persists this snapshot atomically and encrypts tokens at rest. Never log it. */
@kotlinx.serialization.Serializable
data class PortableSession(
    val token: String = "",
    val owner: String = "guest",
    val username: String? = null,
    val avatar: String? = null,
)

/** Verified profile names and reading ownership must describe the same authenticated identity. */
internal fun PortableSession.withMemberMetadata(username: String?, avatar: String?): PortableSession =
    copy(username = username, avatar = avatar,
        owner = readingProgressOwner(token.isNotBlank(), username) ?: owner)

data class NetworkSnapshot(
    val generation: Long = 0,
    val settings: NetworkSettings = NetworkSettings(),
    val webSession: PortableSession = PortableSession(),
    val appSession: PortableSession = PortableSession(),
) {
    fun session(mode: RequestMode = settings.requestMode): PortableSession =
        if (mode == RequestMode.OFFICIAL_APP) appSession else webSession
}

/** A single global mode; every settings or identity change invalidates outstanding requests. */
class PortableNetworkState(initial: NetworkSnapshot = NetworkSnapshot()) {
    private val mutable = MutableStateFlow(initial)
    val snapshots: StateFlow<NetworkSnapshot> = mutable.asStateFlow()
    val snapshot: NetworkSnapshot get() = mutable.value
    private val jobs = MutableStateFlow<Map<Job,Long>>(emptyMap())
    fun update(transform: (NetworkSnapshot) -> NetworkSnapshot) {
        while(true) {
            val old=mutable.value
            val next=transform(old).copy(generation=old.generation+1)
            if(mutable.compareAndSet(old,next)) {
                jobs.value.filterValues { it < next.generation }.keys.forEach { it.cancel() }
                return
            }
        }
    }
    fun updateIfCurrent(generation:Long,transform:(NetworkSnapshot)->NetworkSnapshot):Boolean {
        val old=mutable.value
        if(old.generation!=generation)return false
        val next=transform(old).copy(generation=old.generation+1)
        if(!mutable.compareAndSet(old,next))return false
        jobs.value.filterValues { it < next.generation }.keys.forEach { it.cancel() }
        return true
    }
    fun updateMetadata(generation: Long, username: String?, avatar: String?) {
        val previous = snapshot
        check(previous.generation == generation) { "登录状态已更改" }
        val mode = previous.settings.requestMode
        val session = previous.session(mode).withMemberMetadata(username, avatar)
        fun replace(old: NetworkSnapshot) = if (mode == RequestMode.OFFICIAL_APP)
            old.copy(appSession = session) else old.copy(webSession = session)
        if (session.owner != previous.session(mode).owner) {
            // Unlike an avatar refresh, canonical account correction changes reading ownership.
            // Fence old-owner readers and requests, without moving or deleting their saved data.
            check(updateIfCurrent(generation, ::replace)) { "登录状态已更改" }
        } else mutable.update { old ->
            check(old.generation == generation) { "登录状态已更改" }
            replace(old)
        }
    }
    /** Share-link discovery does not change any transport or account identity. */
    internal fun updateShareHosts(generation: Long, hosts: List<String>) {
        mutable.update { old ->
            check(old.generation == generation) { "网络或登录状态已更改，请刷新重试" }
            old.copy(settings = old.settings.copy(shareHosts = hosts))
        }
    }
    fun requireCurrent(generation: Long) {
        check(snapshot.generation == generation) { "网络或登录状态已更改，请刷新重试" }
    }
    internal fun register(job: Job, generation: Long) {
        requireCurrent(generation)
        jobs.update { it + (job to generation) }
        if (snapshot.generation != generation) {
            jobs.update { it - job }
            job.cancel()
            requireCurrent(generation)
        }
    }
    internal fun unregister(job: Job) { jobs.update { it - job } }
    /** Wait for protocol requests cancelled by a committed generation change to leave transport. */
    suspend fun awaitRetiredRequests(generation:Long) {
        val caller=currentCoroutineContext()[Job]
        while(true) {
            val retired=jobs.value.filter { (job,version)->version<generation&&job!==caller }.keys
            if(retired.isEmpty())return
            retired.joinAll()
        }
    }
}

/** Persist/reload notice via [onNoticeChanged] if the host requires cross-process retention. */
class PortableRiskControlGate(
    initial: RiskControlNotice? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onNoticeChanged: (RiskControlNotice?) -> Unit = {},
) : RiskControlRepository {
    private val mutable = MutableStateFlow(initial)
    override val notice = mutable.asStateFlow()
    private val jobs = MutableStateFlow<Set<Job>>(emptySet())
    private var launched = false
    override fun resetForAppLaunch(isFreshActivity: Boolean) {
        if (!launched || isFreshActivity) resumeByUser()
        launched = true
    }
    override fun resumeIfExpired(): Boolean {
        val current = mutable.value ?: return false
        if (nowMillis() - current.detectedAt < 3_600_000L) return false
        resumeByUser(); return true
    }
    override fun resumeByUser() { mutable.value = null; onNoticeChanged(null) }
    internal fun register(job: Job) {
        mutable.value?.let { throw RiskControlException(it.reason) }
        jobs.update { it + job }
        mutable.value?.let { jobs.update { set -> set - job }; throw RiskControlException(it.reason) }
    }
    internal fun unregister(job: Job) { jobs.update { it - job } }
    fun block(reason: String, source: Job? = null) {
        val value = RiskControlNotice(nowMillis(), reason)
        if (mutable.compareAndSet(null, value)) onNoticeChanged(value)
        jobs.value.filter { it != source }.forEach { it.cancel() }
    }
}

/** App identity must be stable and supplied by the host; no secret or hardware ID is embedded. */
interface PortableOfficialIdentity {
    fun resolve(settings: OfficialAppSettings): OfficialAppSettings
    val tapString: String
    val loginDate: String
    /** Local date formatted yyyy.MM.dd, matching the official protocol. */
    fun dateHeader(): String
}

/** Engine-specific proxy policy. Manual proxy MUST fail rather than silently connect directly. */
fun interface PortableProxyPolicy {
    fun verify(settings: NetworkSettings)
    companion object {
        val EngineDefault = PortableProxyPolicy { settings ->
            require(settings.proxyMode == ProxyMode.SYSTEM) {
                "此平台尚未配置所选代理模式；请提供支持该模式的 HTTP 引擎"
            }
        }
    }
}
