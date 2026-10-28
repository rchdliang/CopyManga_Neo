package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.remote.portable.util.ProtocolCrypto
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlin.time.Clock

class PortableTransportException(message: String) : com.rchdliang.copymanga_neo.domain.platform.PlatformIOException(message)
data class NetworkResponse(val first:Int,val second:String,val headers:Map<String,List<String>>)
data class ResourceResponse(val status:Int,val headers:Map<String,List<String>>,val bytes:ByteArray,val requestMillis:Long,val responseMillis:Long,val generation:Long)
class ApiException(val status: Int, message: String) : Exception(message)
class RawResponseBody(private val text: String) {
    fun string(): String = text
    fun close() = Unit
    inline fun <T> use(block: (RawResponseBody) -> T): T = block(this)
}
class PortableResponse<T>(private val status: Int, private val value: T?, private val raw: String, val headers: Map<String,List<String>> = emptyMap()) {
    val isSuccessful get() = status in 200..299
    fun code() = status
    fun message() = "HTTP $status"
    fun body(): T? = value
    fun errorBody(): RawResponseBody? = if (isSuccessful) null else RawResponseBody(raw)
}

data class ApiRequest(
    val path: String,
    val method: String = "GET",
    val query: List<Pair<String, String>> = emptyList(),
    val form: List<Pair<String, String>>? = null,
    val headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
    val www: Boolean = false,
    val mode: RequestMode? = null,
    val appIdentity: AppCredentialIdentity? = null,
    val avatarJpeg: ByteArray? = null,
    val invalidateCurrentSessionOn401: Boolean = false,
)

/** API redirects are rejected and business writes are never retried. Public resource GETs have bounded redirects.
 * Supply a clean engine client without HttpRequestRetry, cookies, auth or default credentials.
 * Browser engines remain subject to CORS and forbidden request headers; no proxy bypass exists. */
class CopyMangaClient(
    engineClient: HttpClient,
    val state: PortableNetworkState,
    val risk: PortableRiskControlGate = PortableRiskControlGate(),
    private val identity: PortableOfficialIdentity? = null,
    private val proxy: PortableProxyPolicy = PortableProxyPolicy.EngineDefault,
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val diagnostics: ((NetworkDiagnostic) -> Unit)? = null,
    private var sessionInvalidated: (suspend (RequestMode,Long) -> Unit)? = null,
    private val engineProvider: ((NetworkSettings) -> HttpClient)? = null,
    private val boundGeneration:Long? = null,
    private val webApiUserAgent: String = CopyMangaNetworkConfig.WEB_API_USER_AGENT,
) {
    private fun configure(engine:HttpClient)=engine.config {
        followRedirects=false;expectSuccess=false
        install(ContentEncoding){gzip();deflate()}
        install(HttpTimeout)
    }
    private val http = configure(engineClient)
    private data class SelectedEngine(val generation:Long,val source:HttpClient,val client:HttpClient)
    private data class BoundedResponse(val status:HttpStatusCode,val headers:Headers,val url:Url,val bytes:ByteArray)
    private val engineMutex=Mutex()
    private var activeEngine:SelectedEngine?=null
    private suspend fun engine(snapshot:NetworkSnapshot):HttpClient {
        check(boundGeneration==null||boundGeneration==snapshot.generation){"下载客户端已过期，请重新检查网络策略"}
        if(engineProvider==null)return http
        return engineMutex.withLock {
            state.requireCurrent(snapshot.generation)
            activeEngine?.takeIf{it.generation==snapshot.generation}?.let{return@withLock it.client}
            val source=engineProvider.invoke(snapshot.settings)
            val selected=configure(source)
            if(state.snapshot.generation!=snapshot.generation){selected.close();source.close();state.requireCurrent(snapshot.generation)}
            activeEngine?.let{it.client.close();it.source.close()}
            activeEngine=SelectedEngine(snapshot.generation,source,selected)
            selected
        }
    }
    private val routeCounter = MutableStateFlow(0)
    private data class WebCookie(val name:String,val value:String,val path:String,val expiresAt:Long?)
    private val webCookies = MutableStateFlow<Map<String,List<WebCookie>>>(emptyMap())

    suspend fun <T> call(request: ApiRequest, serializer: KSerializer<T>): PortableResponse<T> {
        val response = execute(request)
        val value = if (response.first in 200..299) json.decodeFromString(serializer, response.second) else null
        return PortableResponse(response.first, value, response.second, response.headers)
    }
    suspend fun raw(request: ApiRequest): PortableResponse<RawResponseBody> {
        val response = execute(request)
        return PortableResponse(response.first, if(response.first in 200..299) RawResponseBody(response.second) else null, response.second, response.headers)
    }
    suspend fun results(request: ApiRequest): JsonObject {
        val (status, text) = execute(request)
        check(status in 200..299) { "HTTP $status" }
        val root = json.parseToJsonElement(text).jsonObject
        return root["results"] as? JsonObject ?: error("官方接口未返回有效内容")
    }
    suspend fun execute(request: ApiRequest): NetworkResponse = coroutineScope {
        val snapshot = state.snapshot
        val settings = snapshot.settings
        val mode = request.mode ?: settings.requestMode
        require(mode in state.supportedRequestModes) { "当前平台仅支持网页模式" }
        val app = mode == RequestMode.OFFICIAL_APP && "X-Copy-Comment" !in request.headers && !request.www
        val job = currentCoroutineContext().job
        state.register(job, snapshot.generation)
        try {
            risk.register(job)
            try {
                proxy.verify(settings)
                val selectedHttp=engine(snapshot)
                require(request.path.startsWith("/") && !request.path.contains("..") && !request.path.contains("#")) { "无效请求路径" }
                if(request.www && mode == RequestMode.OFFICIAL_APP && (request.path.startsWith("/comic/") || request.path.startsWith("/comicdetail/"))) error("当前为 App 模式，请通过 API 加载章节")
                val login = request.path == "/api/v3/login"
                val profile = request.appIdentity?.applyTo(settings.officialApp) ?: settings.officialApp
                val host = when {
                    request.www -> settings.wwwHost
                    app -> if(login) profile.loginHost else profile.apiHost
                    login && settings.loginHost.isNotBlank() -> settings.loginHost
                    settings.selection == NetworkSelection.FIXED -> settings.fixedHost
                    settings.selection == NetworkSelection.ROUTE -> settings.routes.getOrNull(settings.routeIndex)?.takeIf { it.isNotEmpty() }?.let { route -> route[(routeCounter.getAndUpdate { it + 1 } and Int.MAX_VALUE) % route.size] } ?: settings.apiHost
                    else -> settings.apiHost
                }
                require(host.isNotBlank() && host.none { it in "/:@?#\\" || it.isWhitespace() }) { "无效站点域名" }
                val headers = request.headers.toMutableMap()
                val transportHeaders=CopyMangaNetworkConfig.transportHeaders(request.www,request.path).let {
                    if (request.www) it else it + ("User-Agent" to webApiUserAgent)
                }
                headers.keys.filter { key -> transportHeaders.keys.any { it.equals(key,true) } }.forEach(headers::remove)
                headers.putAll(transportHeaders)
                val noAuth = headers.remove("X-No-Auth") != null
                headers.remove("X-Copy-Comment")
                headers.remove("Cookie")
                if(request.www) {
                    headers.remove("Authorization")
                    val cookies = webCookies.value["${snapshot.generation}:$host"]?.filter { cookie ->
                        (cookie.expiresAt==null||cookie.expiresAt>nowMillis()) && (request.path==cookie.path||request.path.startsWith(cookie.path.let{if(it.endsWith('/'))it else "$it/"}))
                    }.orEmpty().filterNot { it.name == "webp" }.map { "${it.name}=${it.value}" }
                    if (mode == RequestMode.WEB) headers["Cookie"] = (cookies + "webp=1").joinToString("; ")
                } else {
                    headers["version"] = settings.version
                    headers["Origin"] = "https://${settings.wwwHost}"
                    if(!noAuth && headers["Authorization"] == null) snapshot.session(mode).token.takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Token $it" }
                }
                headers["Referer"]?.let { if(it.startsWith(CopyMangaNetworkConfig.WWW_ORIGIN+"/")) headers["Referer"]="https://${settings.wwwHost}"+it.removePrefix(CopyMangaNetworkConfig.WWW_ORIGIN) }
                if(!request.www && mode==RequestMode.WEB && (request.path.startsWith("/api/v3/member/")||request.path.contains("/chapter2/")) && headers["Authorization"].isNullOrBlank()) error("请先登录")
                var path = request.path
                val query = request.query.toMutableList()
                var form = request.form?.map { (key,value) -> key to if ((login || path == "/api/v3/register") && key == "version") settings.version else value }
                if(app) {
                    val provider = identity ?: error("当前平台缺少 App 模式身份配置")
                    val appProfile = provider.resolve(profile)
                    val tap = provider.tapString.takeIf { it.isNotEmpty() } ?: error("当前安装包缺少 App 模式所需配置")
                    require(listOf(appProfile.pseudoId, appProfile.umString, appProfile.device, appProfile.deviceInfo, appProfile.buildTime).all { it.isNotBlank() }) { "App 身份字段不完整" }
                    val timestamp = (nowMillis()/1000).toString()
                    query.removeAll { it.first in setOf("_t","_update","platform") }
                    query += "platform" to "3"
                    if(path == "/api/v3/h5/homeIndex") path = "/api/v3/h5/homeIndex2"
                    if(path == "/api/v3/search/comic" && query.none { it.first == "q_type" }) query += "q_type" to ""
                    if(Regex("/api/v3/comic2/[^/]+$").matches(path) || path.contains("/chapter2/") || path.endsWith("/chapters") || Regex("/api/v3/book/[^/]+(?:/volume/[^/]+)?$").matches(path)) {
                        query.removeAll { it.first in setOf("in_mainland","request_id") };query += "in_mainland" to "false";query += "request_id" to ""
                    }
                    listOf("Origin","X-Requested-With","Sec-Fetch-Site","Sec-Fetch-Mode","Sec-Fetch-Dest","dnts","Referer","Accept-Encoding").forEach(headers::remove)
                    headers.putAll(mapOf("User-Agent" to "COPY/${appProfile.version}","source" to "copyApp","version" to appProfile.version,"versioncode" to appProfile.versionCode,"platform" to "3","region" to "0","webp" to "1","Accept" to "application/json","Accept-Language" to "","Content-Type" to "application/x-www-form-urlencoded","referer" to "com.copymanga.app-${appProfile.version}","device" to appProfile.device,"deviceinfo" to appProfile.deviceInfo,"pseudoid" to appProfile.pseudoId,"umstring" to appProfile.umString,"dt" to provider.dateHeader(),"X-auth-timestamp" to timestamp,"X-auth-signature" to ProtocolCrypto.hex(ProtocolCrypto.hmacSha256(tap.encodeToByteArray(),timestamp.encodeToByteArray()))))
                    val authorization = headers["Authorization"]
                    if(authorization == null) headers["Authorization"] = "Token"
                    form = form?.filter { it.first != "_update" }
                    if(form != null && (login || path == "/api/v3/register" || requiresMemberForm(request.method,path))) {
                        val register = path == "/api/v3/register"
                        val common = mapOf("platform" to "3","authorization" to if(login||register)"Token " else requireNotNull(authorization){"请先登录后操作"},"version" to appProfile.version,"source" to "copyApp","region" to "0","webp" to "1","userAgent" to "COPY/${appProfile.version}","isGoogle" to "false","pseudoID" to appProfile.pseudoId,"dt" to provider.loginDate,"tapString" to tap,"umString" to appProfile.umString,"buildTime" to appProfile.buildTime,"versionCode" to appProfile.versionCode,"androidId" to appProfile.device,"referer" to "com.copymanga.app-${appProfile.version}","deviceInfo" to appProfile.deviceInfo)
                        form = form.filter { it.first !in common } + common.toList()
                        if(login||register) headers["Authorization"] = "Token"
                    }
                }
                state.requireCurrent(snapshot.generation)
                val started=nowMillis()
                fun diagnostic(phase:String,status:Int?=null,code:Int?=null,bytes:Int?=null,failure:String?=null) {
                    runCatching { diagnostics?.invoke(NetworkDiagnostic(phase,request.method,host,redactedEndpoint(path),mode,snapshot.generation,status,code,nowMillis()-started,bytes,failure,request.method!="GET"||path.contains("/chapter2download/"))) }
                }
                diagnostic("request")
                if(request.avatarJpeg!=null) headers.remove("Content-Type")
                val response=try { selectedHttp.prepareRequest {
                    url { protocol=URLProtocol.HTTPS;this.host=host;encodedPath=path;query.forEach { (key,value) -> parameters.append(key,value) } }
                    method=HttpMethod.parse(request.method)
                    headers.forEach { (key,value) -> header(key,value) }
                    form?.let { fields ->
                        val parameters = Parameters.build { fields.forEach { (key, value) -> append(key, value) } }
                        if (app && request.method == "POST" && path in setOf("/api/v3/member/bookcomment", "/api/v3/member/comment")) {
                            setBody(io.ktor.http.content.TextContent(
                                NovelCommentFormPolicy.encodeAppTopLevelReply(request.method, path, parameters.formUrlEncode()),
                                ContentType.Application.FormUrlEncoded
                            ))
                        } else setBody(FormDataContent(parameters))
                    }
                    request.avatarJpeg?.let { bytes -> setBody(MultiPartFormDataContent(formData { append("file",bytes,Headers.build { append(HttpHeaders.ContentType,"image/jpeg");append(HttpHeaders.ContentDisposition,"filename=\"avatar.jpg\"") }) })) }
                }.execute { streaming -> bounded(streaming,8*1024*1024) } } catch(e:CancellationException) { throw e } catch(_:Exception) {
                    diagnostic("failure",failure="transport")
                    throw PortableTransportException("网络请求失败")
                }
                state.requireCurrent(snapshot.generation)
                require(response.url.host == host && response.url.protocol == URLProtocol.HTTPS) { "站点重定向到其他域名，请更新网络设置" }
                if(response.status.value in 300..399) throw ApiException(response.status.value,"站点请求重定向已停止；未重放业务请求")
                val text=try { response.bytes.decodeToString(throwOnInvalidSequence=true) } catch(e:CancellationException) { throw e } catch(e:Exception) {
                    diagnostic("failure",failure="response_read")
                    throw PortableTransportException("响应读取失败")
                }
                state.requireCurrent(snapshot.generation)
                if(request.www) {
                    webCookies.update { current ->
                        if(state.snapshot.generation!=snapshot.generation)return@update current
                        val old=current["${snapshot.generation}:$host"].orEmpty().toMutableList()
                        response.headers.getAll(HttpHeaders.SetCookie).orEmpty().forEach { value ->
                            runCatching { parseServerSetCookieHeader(value) }.getOrNull()?.let { cookie ->
                                val domain=cookie.domain?.trimStart('.')
                                if(domain==null||host==domain||host.endsWith(".$domain")) {
                                    val cookiePath=cookie.path?.takeIf{it.startsWith('/')}?:request.path.substringBeforeLast('/',"").ifEmpty{"/"}
                                    val expiry=cookie.maxAge?.let{nowMillis()+it.toLong()*1000}?:cookie.expires?.timestamp
                                    old.removeAll{it.name==cookie.name&&it.path==cookiePath}
                                    if(expiry==null||expiry>nowMillis())old+=WebCookie(cookie.name,cookie.value,cookiePath,expiry)
                                }
                            }
                        }
                        current.filterKeys { it.startsWith("${snapshot.generation}:") } + ("${snapshot.generation}:$host" to old)
                    }
                }
                val root=runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                val code=(root?.get("code") as? JsonPrimitive)?.intOrNull
                val message=(root?.get("message") as? JsonPrimitive)?.contentOrNull
                diagnostic("response",response.status.value,code,text.encodeToByteArray().size)
                if(!request.www && (!noAuth||request.invalidateCurrentSessionOn401) && (response.status.value==401 || code==401) && snapshot.session(mode).token.isNotBlank() && headers["Authorization"]=="Token ${snapshot.session(mode).token}" && state.snapshot.generation==snapshot.generation) {
                    state.unregister(job)
                    invalidateSession(mode,snapshot.generation,snapshot.session(mode).token)
                }
                val business=ApiBusinessErrors.isValidation(path,message)||CommentPolicy.isClosedResponse(path,response.status.value,code,message)
                if(!business&&(response.status.value==210||code==210)) {
                    val reason=message?.takeIf { it.isNotBlank() } ?: "官方暂时限制了当前网络的访问"
                    state.requireCurrent(snapshot.generation);risk.block(reason,job);throw RiskControlException(reason)
                }
                if(CommentPolicy.isClosedResponse(path,response.status.value,code,message)) throw com.rchdliang.copymanga_neo.domain.repository.CommentClosedException(message?:"该漫画不允许评论")
                if(code!=null&&code!=200) throw ApiException(code,message?:"官方接口错误 $code")
                if(app&&response.status.value in 200..299&&!permitsEmptyResult(request.method,path)&&root?.get("results")==JsonNull) error("App 接口返回空结果，请检查登录会话和设备配置；未自动回退网页路径")
                NetworkResponse(response.status.value,text,response.headers.entries().associate { it.key to it.value })
            } finally { risk.unregister(job) }
        } finally { state.unregister(job) }
    }

    /** Resource fetch uses no API headers, authentication or cookies, even on shared CDNs. */
    suspend fun resource(address:String,maximumBytes:Int,allowHttp:Boolean=false):ByteArray {
        val response=resourceResponse(address,maximumBytes,allowHttp)
        require(response.status in 200..299){"资源 HTTP ${response.status}"}
        return response.bytes
    }
    /** GET resource transport retaining actual status and cache headers for image loaders. */
    suspend fun resourceResponse(address:String,maximumBytes:Int,allowHttp:Boolean=false,requestHeaders:Map<String,List<String>> = emptyMap(),timeoutMillis:Long? = null):ResourceResponse = coroutineScope {
        require(maximumBytes>0){"资源大小限制必须为正数"}
        val target=Url(address)
        require((target.protocol==URLProtocol.HTTPS||(allowHttp&&target.protocol==URLProtocol.HTTP)) && target.user.isNullOrEmpty() && target.password.isNullOrEmpty()) { "资源地址协议或凭据无效" }
        val snapshot=state.snapshot;proxy.verify(snapshot.settings);val job=currentCoroutineContext().job
        val selectedHttp=engine(snapshot)
        val site=target.host in setOf(snapshot.settings.wwwHost,CopyMangaNetworkConfig.WWW_HOST) ||
            (target.host in setOf(snapshot.settings.apiHost,snapshot.settings.officialApp.apiHost,CopyMangaNetworkConfig.API_HOST)&&target.encodedPath.startsWith("/api/"))
        require(!site||target.protocol==URLProtocol.HTTPS){"站点资源必须使用 HTTPS"}
        val requestedAt=nowMillis()
        fun diagnostic(phase:String,status:Int?=null,bytes:Int?=null,failure:String?=null) {
            runCatching{diagnostics?.invoke(NetworkDiagnostic(phase,"GET",target.host,redactedEndpoint(target.encodedPath),snapshot.settings.requestMode,snapshot.generation,status,elapsedMillis=nowMillis()-requestedAt,bytes=bytes,failureKind=failure))}
        }
        state.register(job,snapshot.generation)
        try {
            if(site)risk.register(job)
            try {
                diagnostic("request")
                val response=selectedHttp.publicResourceGet(address,allowHttp,resourceBoundaryHosts(snapshot.settings),
                    requestHeaders,timeoutMillis,beforeHop={state.requireCurrent(snapshot.generation);proxy.verify(snapshot.settings)}) { streaming -> bounded(streaming,maximumBytes) }
                val bytes=response.bytes
                state.requireCurrent(snapshot.generation)
                diagnostic("response",response.status.value,bytes.size)
                if(site) {
                    val envelope=runCatching{json.parseToJsonElement(bytes.decodeToString()) as? JsonObject}.getOrNull()
                    if(response.status.value==210||(envelope?.get("code") as? JsonPrimitive)?.intOrNull==210) {
                        val reason=(envelope?.get("message") as? JsonPrimitive)?.contentOrNull?:"官方暂时限制了当前网络的访问"
                        risk.block(reason,job);throw RiskControlException(reason)
                    }
                }
                ResourceResponse(response.status.value,response.headers.entries().associate{it.key to it.value},bytes,requestedAt,nowMillis(),snapshot.generation)
            }finally{if(site)risk.unregister(job)}
        } catch(e:CancellationException) { diagnostic("failure",failure="cancelled");throw e
        } catch(e:Exception) { diagnostic("failure",failure="resource_transport_or_policy");throw e
        } finally { state.unregister(job) }
    }
    suspend fun invalidateSession(mode:RequestMode,generation:Long,token:String) {
        if(state.snapshot.generation!=generation||state.snapshot.session(mode).token!=token)return
        sessionInvalidated?.invoke(mode,generation)
        state.updateIfCurrent(generation){old->if(mode==RequestMode.OFFICIAL_APP)old.copy(appSession=PortableSession()) else old.copy(webSession=PortableSession())}
    }
    /** Host/user repository removes persisted credentials before an expired session is cleared. */
    fun setSessionInvalidationListener(listener:suspend (RequestMode,Long)->Unit) { sessionInvalidated=listener }
    /** Reuse protocol/session/risk policy with an engine bound to an approved download network. */
    fun withEngineClient(engine:HttpClient,proxyPolicy:PortableProxyPolicy=proxy,generation:Long=state.snapshot.generation):CopyMangaClient {
        state.requireCurrent(generation)
        check(boundGeneration==null||boundGeneration==generation){"下载客户端已过期，请重新检查网络策略"}
        return CopyMangaClient(engine,state,risk,identity,proxyPolicy,json,nowMillis,diagnostics,sessionInvalidated,boundGeneration=generation,webApiUserAgent=webApiUserAgent)
    }
    fun credentialIdentity(): AppCredentialIdentity {
        val resolved=(identity?:error("当前平台缺少 App 模式身份配置")).resolve(state.snapshot.settings.officialApp)
        return AppCredentialIdentity(resolved.version,resolved.versionCode,resolved.pseudoId,resolved.umString,resolved.device,resolved.deviceInfo,resolved.buildTime)
    }
    fun close() { http.close();activeEngine?.let{it.client.close();it.source.close()};activeEngine=null }
    private suspend fun bounded(response:HttpResponse,maximum:Int)=BoundedResponse(
        response.status,response.headers,response.request.url,readBounded(response,maximum)
    )
    private suspend fun readBounded(response: HttpResponse, maximum: Int): ByteArray {
        val channel=response.bodyAsChannel()
        response.contentLength()?.let { if(it>maximum){channel.cancel();error("响应超过大小限制")} }
        val chunks=mutableListOf<ByteArray>();var size=0
        while(true) { val buffer=ByteArray(minOf(8192L,maximum.toLong()-size+1).toInt());val read=channel.readAvailable(buffer);if(read<0)break;if(read==0)continue;size+=read;if(size>maximum){channel.cancel();error("响应超过大小限制")};chunks+=buffer.copyOf(read) }
        val result=ByteArray(size);var offset=0;chunks.forEach { it.copyInto(result,offset);offset+=it.size };return result
    }
    private fun requiresMemberForm(method:String,path:String)=
        (method=="POST"&&path in setOf("/api/v3/member/collect/comic","/api/v3/member/collect/book","/api/v3/member/comment","/api/v3/member/bookcomment","/api/v3/member/roast","/api/v3/member/comment/report","/api/v3/member/bookcomment/report","/api/v3/member/roast/report","/api/v3/member/update/info"))||
        (method=="DELETE"&&path in setOf("/api/v3/member/collect/comics","/api/v3/member/collect/books","/api/v3/member/browse/comics","/api/v3/member/comment","/api/v3/member/roast"))
    private fun permitsEmptyResult(method:String,path:String)=
        (method=="POST"&&path in setOf("/api/v3/logout","/api/v3/member/collect/comic","/api/v3/member/collect/book","/api/v3/member/comment","/api/v3/member/bookcomment","/api/v3/member/roast","/api/v3/member/comment/report","/api/v3/member/bookcomment/report","/api/v3/member/roast/report","/api/v3/member/update/info"))||(method=="DELETE"&&path in setOf("/api/v3/member/browse/comics","/api/v3/member/comment","/api/v3/member/roast"))
}
