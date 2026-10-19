package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PublicResourceHttpException(val status: Int) :
    java.io.IOException("资源请求失败：HTTP $status")

/** 公共 GitHub 资源沿用代理设置，完全不附加漫画站点凭据、Cookie 或域名路由。 */
@Singleton
class PublicResourceClient @Inject constructor(networkSettings: NetworkSettingsStore) {
    private val client = OkHttpClient.Builder()
        .proxySelector(SettingsProxySelector({ networkSettings.settings.value }))
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()

    suspend fun fetch(url: String, maximumBytes: Int): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(
                Request.Builder().url(url).header("User-Agent", "CopyManga-Neo").build()
            )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw PublicResourceHttpException(it.code)
                            val source = it.body.source()
                            source.request(maximumBytes + 1L)
                            require(source.buffer.size <= maximumBytes) { "资源响应过大" }
                            source.readByteArray()
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
}
