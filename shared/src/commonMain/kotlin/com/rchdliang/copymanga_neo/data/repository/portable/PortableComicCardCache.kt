package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStore
import com.rchdliang.copymanga_neo.domain.model.ComicCardMetadata
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** 仅保存公开展示字段，沿用各平台的原子文档存储。损坏文件不覆盖。 */
class PortableComicCardCache(storage: DocumentStorage) {
    private val store = DocumentStore(storage, "comic-card-metadata",
        MapSerializer(String.serializer(), ComicCardMetadata.serializer()), emptyMap(),
        validate = { values -> require(values.values.all { it.pathWord.isNotBlank() }) })
    suspend fun read(): Map<String, ComicCardMetadata> {
        store.ensureLoaded()
        return store.state.value
    }
    suspend fun save(key: String, value: ComicCardMetadata) {
        store.update { it + (key to value) }
    }
}
