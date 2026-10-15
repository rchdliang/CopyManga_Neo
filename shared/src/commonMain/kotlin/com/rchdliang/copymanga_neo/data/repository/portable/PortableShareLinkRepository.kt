package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository
import com.rchdliang.copymanga_neo.data.local.portable.PortableNetworkValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class PortableShareLinkRepository(
    private val client: CopyMangaClient,
    private val persistShareHosts: suspend (List<String>, Long) -> Unit
):ShareLinkRepository {
    private val mutex=Mutex()
    override suspend fun baseUrl():Result<String> = try { Result.success(mutex.withLock {
        val s=client.state.snapshot
        s.settings.shareHosts.firstOrNull()?.let{return@withLock "https://$it"}
        val hosts=try {
            val result=client.results(ApiRequest("/api/v3/system/network21",query=listOf("platform" to "1"),headers=mapOf("X-No-Auth" to "1"),www=true))
            (result["share"] as? JsonArray).orEmpty().take(20).mapNotNull { value -> runCatching {
                PortableNetworkValidation.host(value.jsonPrimitive.content)
            }.getOrNull() }.distinct()
        } catch(e:CancellationException){throw e}catch(e:Exception){emptyList()}
        client.state.requireCurrent(s.generation)
        if (hosts.isNotEmpty()) persistShareHosts(hosts, s.generation)
        val current = client.state.snapshot.settings
        "https://${current.shareHosts.firstOrNull() ?: current.wwwHost}"
    }) }catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
}
