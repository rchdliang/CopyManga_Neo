package com.rchdliang.copymanga_neo.data.remote.portable

import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import kotlinx.coroutines.withTimeout
import okio.Buffer

/** Coil image requests use the same generation, risk and proxy policy as application requests. */
class CopyMangaImageNetworkClient(
    private val client:CopyMangaClient,
    private val timeoutSeconds:()->Int = {15},
) : NetworkClient {
    override suspend fun <T> executeRequest(request:NetworkRequest,block:suspend (NetworkResponse)->T):T {
        require(request.method.equals("GET",ignoreCase=true)&&request.body==null){"图片加载仅允许 GET 请求"}
        // Coil 3.6.3 still invokes NetworkClient on cache misses when network reads are
        // disabled; newRequest encodes that policy as only-if-cached. This adapter has
        // no HTTP cache, so it must fail locally instead of forwarding that directive.
        if (request.headers.asMap().any { (name, values) ->
                name.equals("Cache-Control", ignoreCase = true) && values.any { value ->
                    value.split(',').any { it.trim().equals("only-if-cached", ignoreCase = true) }
                }
            }) throw okio.IOException("图片未缓存，待页面激活后加载")
        val timeoutMillis=timeoutSeconds().coerceIn(5,60)*1000L
        val response=withTimeout(timeoutMillis) {
            client.resourceResponse(request.url,64*1024*1024,allowHttp=true,requestHeaders=request.headers.asMap(),timeoutMillis=timeoutMillis)
        }
        client.state.requireCurrent(response.generation)
        val headers=NetworkHeaders.Builder().apply {
            response.headers.forEach{(name,values)->values.forEach{add(name,it)}}
        }.build()
        val body=NetworkResponseBody(Buffer().write(response.bytes))
        try {
            val result=block(NetworkResponse(code=response.status,requestMillis=response.requestMillis,responseMillis=response.responseMillis,headers=headers,body=body))
            client.state.requireCurrent(response.generation)
            return result
        }finally{body.close()}
    }
}

fun copyMangaImageFetcherFactory(client:CopyMangaClient,timeoutSeconds:()->Int = {15}):NetworkFetcher.Factory =
    NetworkFetcher.Factory(networkClient={CopyMangaImageNetworkClient(client,timeoutSeconds)})
