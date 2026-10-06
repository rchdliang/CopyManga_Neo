package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.SimpleResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST

/** 已抓包确认的 App 会员写入；固定账号，使用禁止自动重放的客户端。 */
interface MemberWriteApiService {
    @FormUrlEncoded
    @POST("api/v3/member/collect/comic")
    suspend fun addFavorite(
        @Field("comic_id") comicId: String,
        @Header("Authorization") authorization: String,
        @Field("is_collect") isCollect: Int = 1,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<SimpleResponse>

    /** App 使用重复的 ids 字段，不能转换成索引数组或 Map。 */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/v3/member/collect/comics", hasBody = true)
    suspend fun removeFavorites(
        @Field("ids") collectIds: List<Int>,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<SimpleResponse>

    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/v3/member/browse/comics", hasBody = true)
    suspend fun clearBrowseHistory(
        @Header("Authorization") authorization: String,
        @Field("free_type") freeType: Int = 1,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<SimpleResponse>

    /** 官方退出请求体为空，只在请求头携带旧账号 Token。 */
    @POST("api/v3/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): Response<SimpleResponse>
}
