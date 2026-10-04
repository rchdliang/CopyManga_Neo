package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.OfficialChapterResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.Path

/** 此 GET 会扣下载额度，必须使用禁止自动重试和重定向的客户端。 */
interface OfficialDownloadApiService {
    @GET("api/v3/comic/{pathWord}/chapter2download/{chapterUuid}")
    suspend fun chapter(
        @Path("pathWord") pathWord: String,
        @Path("chapterUuid") chapterUuid: String,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<OfficialChapterResponse>
}
