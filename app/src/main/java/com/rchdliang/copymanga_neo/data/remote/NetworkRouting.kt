package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** 请求角色在改写前确定，外部图片不会因共享客户端而携带账号信息。 */
internal data class NetworkRequestContext(
    val generation: Long,
    val api: Boolean,
    val www: Boolean,
    val targetHost: String,
    val settings: NetworkSettings? = null
)

internal fun Request.isCopyApi(): Boolean = tag(NetworkRequestContext::class.java)?.api
    ?: (url.host == CopyMangaNetworkConfig.API_HOST && url.encodedPath.startsWith("/api/"))

internal fun Request.isCopyWww(): Boolean = tag(NetworkRequestContext::class.java)?.www
    ?: (url.host == CopyMangaNetworkConfig.WWW_HOST)

/** 线路内轮询；请求只发送一次，不自动重试登录、评论或其他业务写入。 */
class NetworkRoutingInterceptor @Inject constructor(private val store: NetworkSettingsStore) : Interceptor {
    private val counter = AtomicInteger()
    override fun intercept(chain: Interceptor.Chain): Response {
        val (generation, snapshot) = store.snapshot()
        val original = chain.request()
        val settings =
            original.tag(RequestMode::class.java)?.let { snapshot.copy(requestMode = it) }
                ?: snapshot
        val api = original.url.host == CopyMangaNetworkConfig.API_HOST && original.url.encodedPath.startsWith("/api/")
        val www = original.url.host == CopyMangaNetworkConfig.WWW_HOST && !api
        val builder = original.newBuilder()
        if (api || www) {
            val login = api && original.url.encodedPath == "/api/v3/login"
            val appMode =
                settings.requestMode == RequestMode.OFFICIAL_APP &&
                        original.header("X-Copy-Comment") == null
            if (www && appMode && (original.url.encodedPath.startsWith("/comicdetail/") ||
                        original.url.encodedPath.startsWith("/comic/"))
            ) {
                throw IOException("当前为 App 模式，请刷新页面以通过 API 加载章节")
            }
            val target = if (api && appMode) {
                if (login) settings.officialApp.loginHost else settings.officialApp.apiHost
            } else if (www) settings.wwwHost else if (login && settings.loginHost.isNotBlank()) settings.loginHost
                else selectedApiHost(settings, counter.getAndIncrement())
            builder.url(original.url.newBuilder().host(target).build())
            if (api) {
                builder.header("version", settings.version).header("Origin", "https://${settings.wwwHost}")
            }
            original.header("Referer")?.let { referer ->
                if (referer.startsWith(CopyMangaNetworkConfig.WWW_ORIGIN + "/")) {
                    builder.header("Referer", "https://${settings.wwwHost}" + referer.removePrefix(CopyMangaNetworkConfig.WWW_ORIGIN))
                }
            }
            // 登录和注册表单的 version 必须与当前网页请求头一致。
            if (login || (api && original.url.encodedPath == "/api/v3/register")) {
                (original.body as? FormBody)?.let { form ->
                    val replacement = FormBody.Builder()
                    for (i in 0 until form.size) replacement.add(form.name(i), if (form.name(i) == "version") settings.version else form.value(i))
                    builder.method(original.method, replacement.build())
                }
            }
        } else {
            builder.removeHeader("Authorization").removeHeader("Cookie")
        }
        val routed = builder.build()
        return chain.proceed(routed.newBuilder().tag(NetworkRequestContext::class.java,
            NetworkRequestContext(generation, api, www, routed.url.host, settings)
        ).build()
        )
    }
}

internal fun selectedApiHost(settings: NetworkSettings, index: Int = 0): String = when (settings.selection) {
    NetworkSelection.DEFAULT -> settings.apiHost
    NetworkSelection.FIXED -> settings.fixedHost
    NetworkSelection.ROUTE -> settings.routes.getOrNull(settings.routeIndex)?.let { route ->
        route.getOrNull(Math.floorMod(index, route.size))
    } ?: settings.apiHost
}

/** 切换设置后，旧响应不能继续触发认证清理或风控状态更新。 */
class NetworkGenerationInterceptor @Inject constructor(private val store: NetworkSettingsStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val generation = chain.request().tag(NetworkRequestContext::class.java)?.generation
        if (generation != null && generation != store.generation) throw IOException("网络设置已更改，请刷新重试")
        val response = chain.proceed(chain.request())
        if (generation != null && generation != store.generation) {
            response.close()
            throw IOException("网络设置已更改，请刷新重试")
        }
        return response
    }
}

/** 系统模式委托系统选择器；手动模式失败时不会偷偷回退直连。 */
internal class SettingsProxySelector(
    private val settings: () -> NetworkSettings,
    private val system: ProxySelector? = getDefault()
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val config = settings()
        return when (config.proxyMode) {
            ProxyMode.DIRECT -> listOf(Proxy.NO_PROXY)
            ProxyMode.MANUAL -> listOf(NetworkSettingsCodec.proxy(config.proxyAddress))
            ProxyMode.SYSTEM -> system?.select(uri)?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
        }
    }
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        if (settings().proxyMode == ProxyMode.SYSTEM) system?.connectFailed(uri, sa, ioe)
    }
}

/** 重定向及连接复用也必须遵守配置边界，不能泄露凭据或沿用旧代理。 */
class NetworkBoundaryInterceptor @Inject constructor(private val store: NetworkSettingsStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = request.tag(NetworkRequestContext::class.java)
        if (context != null) {
            if (context.generation != store.generation) throw IOException("网络设置已更改，请刷新重试")
            if ((context.api || context.www) && (request.url.host != context.targetHost || !request.url.isHttps)) {
                throw IOException("站点重定向到其他域名，请在网络设置中更新地址")
            }
            val allowed = SettingsProxySelector({ store.settings.value }).select(request.url.toUri())
            if (chain.connection()?.route()?.proxy !in allowed) {
                throw IOException("代理设置已更改，请刷新重试")
            }
        }
        return chain.proceed(request)
    }
}
