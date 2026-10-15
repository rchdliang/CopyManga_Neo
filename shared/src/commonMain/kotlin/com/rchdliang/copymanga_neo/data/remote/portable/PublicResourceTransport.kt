package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val RESOURCE_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36 Edg/86.0.622.38"
internal const val RESOURCE_LANGUAGE = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7"
internal fun resourceBoundaryHosts(settings: NetworkSettings? = null): Set<String> {
    val defaults = NetworkSettings()
    return (listOf(defaults.wwwHost, defaults.apiHost, defaults.officialApp.apiHost, defaults.officialApp.loginHost) +
        listOfNotNull(settings?.wwwHost, settings?.apiHost, settings?.loginHost, settings?.fixedHost,
            settings?.officialApp?.apiHost, settings?.officialApp?.loginHost) +
        settings?.routes.orEmpty().flatten() + settings?.shareHosts.orEmpty())
        .filter { it.isNotBlank() }.map { it.lowercase().trimEnd('.') }.toSet()
}

/** Public GETs only. A boundary resource may be read directly, but never redirected.
 * At most five redirect hops, deliberately tighter than the original OkHttp follow-up limit.
 */
internal class PublicResourceRedirectPolicy(address: String, private val allowHttp: Boolean, private val protectedHosts: Set<String>) {
    var target: Url = canonicalResourceUrl(Url(address))
        private set
    private val visited = mutableSetOf<String>()
    private var redirects = 0
    init {
        require(address.startsWith("https://", true) || (allowHttp && address.startsWith("http://", true))) { "资源地址必须是完整 HTTP 地址" }
        validate(target); visited += target.toString()
    }
    private fun validate(url: Url) {
        require((url.protocol == URLProtocol.HTTPS || (allowHttp && url.protocol == URLProtocol.HTTP)) &&
            url.host.isNotBlank() && url.user == null && url.password == null) { "资源地址协议或凭据无效" }
        require(!boundary(url) || url.protocol == URLProtocol.HTTPS) { "站点资源必须使用 HTTPS" }
    }
    private fun boundary(url: Url) = url.host.lowercase().trimEnd('.') in protectedHosts || url.encodedPath.decodeURLPart().startsWith("/api/")
    fun follow(location: String?): Url {
        require(!location.isNullOrBlank()) { "资源重定向缺少地址" }
        require(redirects < 5) { "资源重定向次数过多" }
        val next = canonicalResourceUrl(URLBuilder(target).apply {
            // Ktor takeFrom appends query parameters. RFC redirects replace them for a new path/query.
            // In particular, never carry signed source-URL parameters to another CDN host.
            if (!location.trimStart().startsWith("#")) { encodedParameters.clear(); trailingQuery = false }
            takeFrom(location)
        }.build())
        validate(next)
        require(target.protocol != URLProtocol.HTTPS || next.protocol == URLProtocol.HTTPS) { "资源重定向不能降级 HTTP" }
        require(!boundary(target) && !boundary(next)) { "站点资源不允许重定向，请检查网络地址" }
        require(visited.add(next.toString())) { "资源重定向循环" }
        redirects++
        target = next
        return next
    }
}

/** Ktor resolves relative paths but retains dot segments; normalize before checking boundaries/loops. */
private fun canonicalResourceUrl(url: Url): Url = URLBuilder(url).apply {
    val normalized = mutableListOf<String>()
    encodedPathSegments.forEachIndexed { index, segment ->
        when (segment.decodeURLPart()) {
            "." -> if (index == encodedPathSegments.lastIndex) normalized += ""
            ".." -> {
                if (normalized.isNotEmpty() && !(normalized.size == 1 && normalized[0].isEmpty())) normalized.removeAt(normalized.lastIndex)
                if (index == encodedPathSegments.lastIndex) normalized += ""
            }
            else -> normalized += segment
        }
    }
    encodedPathSegments = normalized
    encodedFragment = ""
}.build()

private data class ResourceHop<T>(val redirect: Boolean, val value: T? = null)

/** The supplied client must disable automatic redirects and have no auth/cookie/retry plugins. */
internal suspend fun <T : Any> HttpClient.publicResourceGet(
    address: String,
    allowHttp: Boolean = false,
    protectedHosts: Set<String> = resourceBoundaryHosts(),
    requestHeaders: Map<String, List<String>> = emptyMap(),
    timeoutMillis: Long? = null,
    beforeHop: () -> Unit = {},
    consume: suspend (HttpResponse) -> T
): T {
    timeoutMillis?.let { require(it in 1..60_000L) { "图片超时设置无效" } }
    val policy = PublicResourceRedirectPolicy(address, allowHttp, protectedHosts)
    while (true) {
        currentCoroutineContext().ensureActive()
        beforeHop()
        val result = prepareGet(policy.target) {
            header(HttpHeaders.UserAgent, RESOURCE_USER_AGENT)
            header(HttpHeaders.AcceptLanguage, RESOURCE_LANGUAGE)
            val permitted = setOf("accept", "cache-control", "if-none-match", "if-modified-since", "range", "if-range")
            requestHeaders.forEach { (name, values) ->
                if (name.lowercase() in permitted) values.forEach { header(name, it) }
            }
            timeoutMillis?.let { millis -> timeout { requestTimeoutMillis = millis; connectTimeoutMillis = millis; socketTimeoutMillis = millis } }
        }.execute { response ->
            beforeHop()
            if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                policy.follow(response.headers[HttpHeaders.Location])
                // Do not buffer redirect bodies. execute closes this response before the next hop.
                ResourceHop<T>(redirect = true)
            } else ResourceHop(redirect = false, value = consume(response))
        }
        if (!result.redirect) return requireNotNull(result.value)
    }
}
