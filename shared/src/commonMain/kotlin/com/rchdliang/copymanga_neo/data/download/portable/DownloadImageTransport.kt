package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.reading.ReadingContentHash
import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.data.remote.portable.publicResourceGet
import com.rchdliang.copymanga_neo.data.remote.portable.resourceBoundaryHosts
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

interface DownloadImageTransport {
    /** No retries. The caller rechecks current policy, pause and cancellation for every chunk. */
    suspend fun read(url: String, policy: DownloadNetworkState, onBytes: (Int) -> Unit): ByteArray
}

/** Bounded download, content checks and cancellation before any atomic file commit. */
class KtorDownloadImageTransport(
    private val clients: DownloadClientProvider,
    private val maximumImageBytes: Int = 64 * 1024 * 1024,
    private val networkState: PortableNetworkState? = null
) : DownloadImageTransport {
    init { require(maximumImageBytes > 0) }
    override suspend fun read(url: String, policy: DownloadNetworkState, onBytes: (Int) -> Unit): ByteArray {
        if (!policy.allowed) throw PlatformIOException("当前网络不允许下载")
        val snapshot = networkState?.snapshot
        val client = clients.client(policy).config { followRedirects = false; expectSuccess = false }
        try {
            return client.publicResourceGet(url, allowHttp = true, protectedHosts = resourceBoundaryHosts(snapshot?.settings), beforeHop = {
                snapshot?.let { networkState?.requireCurrent(it.generation) }
            }) { response ->
                val code = response.status.value
                if (code == 403 || code == 429) throw RiskControlException("服务器返回 $code，可能触发风控")
                if (code !in 200..299) throw PlatformIOException("图片下载失败，HTTP $code")
                val expected = response.contentLength()
                if (expected != null && expected > maximumImageBytes) throw PlatformIOException("图片超过单张安全大小限制")
                val channel = response.bodyAsChannel()
                val chunks = mutableListOf<ByteArray>()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    snapshot?.let { networkState?.requireCurrent(it.generation) }
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (total.toLong() + read > maximumImageBytes) throw PlatformIOException("图片超过单张安全大小限制")
                    chunks += buffer.copyOf(read)
                    total += read
                    onBytes(read)
                }
                if (total <= 0 || (expected != null && expected >= 0 && expected != total.toLong())) {
                    throw PlatformIOException("图片下载不完整：$total/${expected ?: -1} 字节")
                }
                val bytes = ByteArray(total)
                var offset = 0
                chunks.forEach { it.copyInto(bytes, offset); offset += it.size }
                val type = response.headers["Content-Type"].orEmpty()
                val preview = bytes.copyOf(minOf(bytes.size, 512)).decodeToString().lowercase()
                val nonImageType = type.isNotBlank() && !type.startsWith("image/") && !type.contains("octet-stream")
                val html = preview.trimStart().startsWith("<html") || preview.trimStart().startsWith("<!doctype html")
                if (nonImageType || html) {
                    if (listOf("captcha", "verify", "cloudflare", "cf-challenge", "安全验证", "人机验证", "访问过于频繁", "访问频繁", "too many requests").any { it in preview }) {
                        throw RiskControlException("图片地址返回风控页面")
                    }
                    throw PlatformIOException("图片地址返回了非图片内容：$type")
                }
                bytes
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: PlatformIOException) { throw error }
        catch (error: Exception) { throw PlatformIOException("图片传输失败：${error.message}", error) }
        finally { client.close() }
    }
}

internal fun downloadComicPrefix(comic: String) =
    "downloads/${ReadingContentHash.sha256(comic.encodeToByteArray())}/"
internal fun downloadChapterPrefix(comic: String, chapter: String) =
    "${downloadComicPrefix(comic)}${ReadingContentHash.sha256(chapter.encodeToByteArray())}/"

/** Lock-free immutable snapshots replace JVM ConcurrentHashMap on all targets. */
internal class JobRegistry {
    private val snapshot = MutableStateFlow<Map<String, Job>>(emptyMap())
    operator fun get(key: String): Job? = snapshot.value[key]
    operator fun set(key: String, value: Job) { snapshot.update { it + (key to value) } }
    val values: Collection<Job> get() = snapshot.value.values
    fun filterKeys(predicate: (String) -> Boolean): Map<String, Job> = snapshot.value.filterKeys(predicate)
    fun clear() { snapshot.value = emptyMap() }
    fun remove(key: String): Job? = snapshot.getAndUpdate { it - key }[key]
    fun remove(key: String, expected: Job): Boolean {
        while (true) {
            val old = snapshot.value
            if (old[key] !== expected) return false
            if (snapshot.compareAndSet(old, old - key)) return true
        }
    }
}
