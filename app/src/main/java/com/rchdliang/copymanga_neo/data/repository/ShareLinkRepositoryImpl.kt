package com.rchdliang.copymanga_neo.data.repository

import android.util.Log
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.NetworkSettingsCodec
import com.rchdliang.copymanga_neo.data.remote.api.SystemApiService
import com.rchdliang.copymanga_neo.di.WwwRetrofit
import com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 分享配置始终通过网页线路获取，和 App API 会话保持独立。 */
@Singleton
class ShareLinkRepositoryImpl @Inject constructor(
    private val store: NetworkSettingsStore,
    @WwwRetrofit retrofit: Retrofit
) : ShareLinkRepository {
    private val api = retrofit.create(SystemApiService::class.java)
    private val mutex = Mutex()

    override suspend fun baseUrl(): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val (generation, settings) = store.snapshot()
                settings.shareHosts.firstOrNull()
                    ?.let { return@withLock Result.success("https://$it") }
                val hosts = try {
                    val response = api.getShareNetworkConfig()
                    if (!response.isSuccessful) throw IOException("分享配置请求失败：HTTP ${response.code()}")
                    NetworkSettingsCodec.parseShareHosts(response.body()?.toString().orEmpty())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ShareLink", "分享域名暂不可用，使用当前 WWW 域名", e)
                    emptyList()
                }
                store.ifCurrent(generation) {
                    if (hosts.isNotEmpty()) store.save(
                        store.settings.value.copy(shareHosts = hosts), rememberPrevious = false
                    )
                }
                val current = store.settings.value
                Result.success("https://${current.shareHosts.firstOrNull() ?: current.wwwHost}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
