package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.repository.NovelAnnotationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.novelAnnotationDataStore by preferencesDataStore("novel_reader_annotations_v1")

/** 解析失败时不覆盖原数据；写入在 DataStore 的原子事务内执行。 */
internal class NovelAnnotationCodec {
    private val gson =
        GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    private val type = object : TypeToken<List<NovelAnnotationDto>>() {}.type

    private data class RangeDto(
        @SerializedName("start_line") val startLine: Int?,
        @SerializedName("start_character") val startCharacter: Int?,
        @SerializedName("end_line") val endLine: Int?,
        @SerializedName("end_character") val endCharacter: Int?
    )

    private data class NovelAnnotationDto(
        @SerializedName("id") val id: String?,
        @SerializedName("owner") val owner: String?,
        @SerializedName("mode") val mode: String?,
        @SerializedName("path") val path: String?,
        @SerializedName("volume") val volume: String?,
        @SerializedName("volume_name") val volumeName: String?,
        @SerializedName("text") val text: String?,
        @SerializedName("range") val range: RangeDto?,
        @SerializedName("style") val style: String?,
        @SerializedName("note") val note: String?,
        @SerializedName("created_at") val createdAt: Long?,
        @SerializedName("updated_at") val updatedAt: Long?
    )

    private fun validate(value: NovelAnnotation) {
        require(
            value.id.isNotBlank() && value.owner.isNotBlank() && value.mode.isNotBlank() &&
                    value.path.isNotBlank() && value.volume.isNotBlank() && value.range.valid && value.text.isNotBlank() &&
                    value.style in setOf("PRIMARY", "SECONDARY", "TERTIARY")
        )
    }

    fun decode(json: String?): List<NovelAnnotation> {
        if (json == null) return emptyList()
        val records: List<NovelAnnotationDto> = gson.fromJson(json, type)
            ?: throw IllegalArgumentException("标注数据格式不正确")
        return records.map { dto ->
            val coordinates = requireNotNull(dto.range)
            NovelAnnotation(
                requireNotNull(dto.id),
                requireNotNull(dto.owner),
                requireNotNull(dto.mode),
                requireNotNull(dto.path),
                requireNotNull(dto.volume),
                dto.volumeName.orEmpty(),
                requireNotNull(dto.text),
                com.rchdliang.copymanga_neo.domain.model.NovelTextRange(
                    requireNotNull(coordinates.startLine),
                    requireNotNull(coordinates.startCharacter),
                    requireNotNull(coordinates.endLine),
                    requireNotNull(coordinates.endCharacter)
                ),
                dto.style ?: "PRIMARY",
                dto.note.orEmpty(),
                dto.createdAt ?: 0L,
                dto.updatedAt ?: dto.createdAt ?: 0L
            ).also(::validate)
        }
    }

    fun encode(values: List<NovelAnnotation>): String {
        values.forEach(::validate)
        return gson.toJson(values.map {
            NovelAnnotationDto(
                it.id, it.owner, it.mode, it.path, it.volume, it.volumeName, it.text,
                RangeDto(
                    it.range.startLine,
                    it.range.startCharacter,
                    it.range.endLine,
                    it.range.endCharacter
                ),
                it.style, it.note, it.createdAt, it.updatedAt
            )
        })
    }

    fun upsert(values: List<NovelAnnotation>, value: NovelAnnotation): List<NovelAnnotation> {
        validate(value)
        val same = values.firstOrNull {
            it.owner == value.owner && it.mode == value.mode &&
                    it.path == value.path && it.volume == value.volume && (it.id == value.id || it.range == value.range)
        }
        val updated =
            value.copy(id = same?.id ?: value.id, createdAt = same?.createdAt ?: value.createdAt)
        return values.filterNot {
            it.id == updated.id && it.owner == updated.owner && it.mode == updated.mode &&
                    it.path == updated.path && it.volume == updated.volume
        } + updated
    }
}

@Singleton
class NovelAnnotationStore @Inject constructor(@ApplicationContext context: Context) :
    NovelAnnotationRepository {
    private val store = context.novelAnnotationDataStore
    private val key = stringPreferencesKey("annotations")
    private val codec = NovelAnnotationCodec()
    override val annotations = store.data.map { codec.decode(it[key]) }

    override suspend fun save(annotation: NovelAnnotation): Result<Unit> = try {
        store.edit { it[key] = codec.encode(codec.upsert(codec.decode(it[key]), annotation)) }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun remove(annotation: NovelAnnotation): Result<Unit> = try {
        store.edit { preferences ->
            preferences[key] = codec.encode(codec.decode(preferences[key]).filterNot {
                it.id == annotation.id && it.owner == annotation.owner && it.mode == annotation.mode &&
                        it.path == annotation.path && it.volume == annotation.volume
            })
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
