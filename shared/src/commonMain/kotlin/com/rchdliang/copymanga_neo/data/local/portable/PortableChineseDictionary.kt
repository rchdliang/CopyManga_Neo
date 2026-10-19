package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.data.remote.portable.GitHubMetadata
import com.rchdliang.copymanga_neo.data.text.ChineseDictionaryStore
import com.rchdliang.copymanga_neo.domain.repository.ChineseDictionaryRepository
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** 原子文档保存最新完整词典，兼容读取先前固定版本的本地数据。 */
internal fun portableChineseDictionary(
    documents: DocumentStorage,
    client: CopyMangaClient
): ChineseDictionaryRepository {
    val serializer = MapSerializer(String.serializer(), String.serializer())
    val metadata = GitHubMetadata(client)
    return ChineseDictionaryStore(
        read = {
            (documents.read(ChineseDictionaryStore.DOCUMENT_KEY) ?: documents.read(
                ChineseDictionaryStore.LEGACY_DOCUMENT_KEY
            ))?.let { raw ->
                require(raw.encodeToByteArray().size <= ChineseDictionaryStore.MAX_DOCUMENT_BYTES) { "词典文件过大" }
                storageJson.decodeFromString(serializer, raw)
            }
        },
        write = { files ->
            documents.write(
                ChineseDictionaryStore.DOCUMENT_KEY,
                storageJson.encodeToString(serializer, files)
            )
        },
        fetch = { url -> client.resource(url, ChineseDictionaryStore.MAX_FILE_BYTES) },
        latest = metadata::latestDictionary
    )
}
