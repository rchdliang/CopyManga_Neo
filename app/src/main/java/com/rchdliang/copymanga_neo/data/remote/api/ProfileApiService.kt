package com.rchdliang.copymanga_neo.data.remote.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.SimpleResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.lang.reflect.Type

/** 账号资料写入禁止自动重放；上传成功与资料保存成功分别检查。 */
interface ProfileApiService {
    @GET("api/v3/member/update/info")
    suspend fun editable(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<EditableProfileResponse>

    @Multipart
    @POST("api/v3/upload/image/1")
    suspend fun uploadAvatar(
        @Part image: MultipartBody.Part,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<AvatarUploadResponse>

    @FormUrlEncoded
    @POST("api/v3/member/update/info")
    suspend fun update(
        @Field("nickname") nickname: String,
        @Field("avatar") avatar: String,
        @Field("gender") gender: Int,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<SimpleResponse>
}

data class EditableProfileResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: EditableProfileResult?
)

data class EditableProfileResult(@SerializedName("info") val info: EditableProfileDto?)
data class EditableProfileDto(
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("avatar_rp") val avatarPath: String?,
    @JsonAdapter(ProfileGenderDeserializer::class)
    @SerializedName("gender") val gender: Int?
)

/** 编辑接口返回 {value, display}，兼容直接返回数值的响应，保存时保留原性别。 */
internal class ProfileGenderDeserializer : JsonDeserializer<Int> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Int {
        val value = if (json.isJsonObject) json.asJsonObject.get("value") else json
        return value?.takeIf { it.isJsonPrimitive }
            ?.asString?.toIntOrNull()
            ?: throw JsonParseException("账号性别数据格式异常，请重新加载资料")
    }
}

data class AvatarUploadResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val path: String?
)
