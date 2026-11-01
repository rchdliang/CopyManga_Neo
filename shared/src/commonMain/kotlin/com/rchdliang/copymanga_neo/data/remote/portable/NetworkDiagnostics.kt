package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/** 只记录资源标识和允许的诊断参数，不保存凭据、正文或带签名的完整 URL。 */
data class NetworkDiagnostic(
    val phase:String,
    val method:String,
    val host:String,
    val endpoint:String,
    val mode:RequestMode,
    val generation:Long,
    val status:Int? = null,
    val businessCode:Int? = null,
    val elapsedMillis:Long? = null,
    val bytes:Int? = null,
    val failureKind:String? = null,
    val outcomeUnknown:Boolean = false,
    val requestId: String? = null,
    val operationId: String? = null,
    val source: String? = null,
    val trigger: String? = null,
    val auth: String? = null,
    val query: Map<String, String> = emptyMap(),
    val forceRefresh: Boolean? = null,
)

private val diagnosticRun = Random.nextLong().toString(16)
private val diagnosticSequence = MutableStateFlow(0L)
internal fun nextDiagnosticId(): String = "$diagnosticRun-${diagnosticSequence.getAndUpdate { it + 1 }}"

/** 随协程传播调用用途；后台任务显式携带子上下文，保持同一轮操作的关联。 */
class NetworkTrace(
    val source: String,
    val trigger: String,
    val operationId: String = nextDiagnosticId(),
    val forceRefresh: Boolean? = null,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<NetworkTrace>
    fun child(source: String, forceRefresh: Boolean? = this.forceRefresh) =
        NetworkTrace(source, trigger, operationId, forceRefresh)
}

suspend fun <T> withNetworkTrace(
    source: String, trigger: String = "repository_call", forceRefresh: Boolean? = null,
    block: suspend () -> T,
): T {
    val parent = currentCoroutineContext()[NetworkTrace]
    val trace = parent?.child(source, forceRefresh ?: parent.forceRefresh)
        ?: NetworkTrace(source, trigger, forceRefresh = forceRefresh)
    return withContext(trace) { block() }
}

/** 漫画、小说及目录路径保留原标识；其他路径继续隐藏不明字段。 */
internal fun diagnosticEndpoint(path: String): String {
    val clean = path.substringBefore('?').substringBefore('#')
    val publicContent = Regex("^/(?:api/v3/(?:comic2/[^/]+(?:/query)?|comic/[^/]+/(?:group/[^/]+/chapters|chapter2(?:download)?/[^/]+)|book/[^/]+(?:/(?:query|volumes|volume/[^/]+))?)|comicdetail/[^/]+/chapters|comic/[^/]+(?:/chapter/[^/]+)?)$")
    return if (publicContent.matches(clean)) clean else redactedEndpoint(clean)
}

internal fun diagnosticQuery(query: List<Pair<String, String>>): Map<String, String> = query.filter { (key, value) ->
    when (key) {
        "offset", "limit", "platform", "type", "free_type" -> value.toIntOrNull() != null
        "_update", "in_mainland" -> value in setOf("true", "false", "0", "1")
        "chapter_id", "comic_id", "book_id", "volume_id", "group", "ordering", "view" ->
            value.length in 1..256 && value.all { it.isLetterOrDigit() || it in "_-" }
        else -> false
    }
}.toMap()

internal fun diagnosticAuth(headers: Map<String, String>): String {
    val authorization = headers.entries.firstOrNull { it.key.equals("Authorization", true) }?.value.orEmpty().trim()
    val cookie = headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value.orEmpty()
    return if ((authorization.isNotEmpty() && !authorization.equals("Token", true)) ||
        cookie.split(';').any { it.trim().isNotEmpty() && it.trim() != "webp=1" }) "authenticated" else "guest"
}

internal fun diagnosticSource(path: String): String = when {
    path.contains("/chapter2download/") -> "download.chapter"
    path.contains("/chapter2/") -> "chapter.api"
    path.endsWith("/chapters") -> "catalog.api"
    path.endsWith("/roasts") -> "comments.chapter"
    path.endsWith("/comments") || path.endsWith("/bookcomments") -> "comments.list"
    path.endsWith("/member/browse/comics") -> "history.list"
    path.startsWith("/api/v3/comic2/") -> if (path.endsWith("/query")) "comic.status" else "comic.detail"
    else -> "api"
}
internal fun redactedEndpoint(path:String):String {
    val known=setOf("api","v3","h5","homeIndex","homeIndex2","comic","comic2","comics","book","books","search","ranks","update","newest","topics","topic","contents","theme","count","filter","tags","group","chapters","chapter2","chapter2download","comicdetail","chapter","member","collect","browse","info","login","logout","register","query","recs","bookcomments","bookcomment","comment","comments","roast","roasts","config","report","upload","image","system","network21","volumes","volume")
    return path.split('/').joinToString("/"){if(it.isEmpty()||it in known)it else "{id}"}
}
