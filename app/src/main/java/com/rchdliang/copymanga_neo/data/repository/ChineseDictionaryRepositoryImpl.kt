package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.remote.GitHubMetadata
import com.rchdliang.copymanga_neo.data.remote.PublicResourceClient
import com.rchdliang.copymanga_neo.data.text.ChineseDictionaryStore
import com.rchdliang.copymanga_neo.domain.repository.ChineseDictionaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 公共下载与元数据都使用无凭据资源通道；更新经校验后原子替换。 */
@Singleton
class ChineseDictionaryRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    resources: PublicResourceClient,
    metadata: GitHubMetadata
) : ChineseDictionaryRepository {
    private val file = AtomicFile(File(context.filesDir, ChineseDictionaryStore.DOCUMENT_KEY))
    private val legacy =
        AtomicFile(File(context.filesDir, ChineseDictionaryStore.LEGACY_DOCUMENT_KEY))
    private val store = ChineseDictionaryStore(
        read = {
            withContext(Dispatchers.IO) {
                val selected = listOf(file, legacy).firstOrNull {
                    it.baseFile.exists() || File(it.baseFile.path + ".bak").exists()
                }
                selected?.let {
                    require(
                        maxOf(
                            it.baseFile.length(),
                            File(it.baseFile.path + ".bak").length()
                        ) <= ChineseDictionaryStore.MAX_DOCUMENT_BYTES
                    ) { "词典文件过大" }
                    val raw = it.openRead().bufferedReader(Charsets.UTF_8)
                        .use { reader -> reader.readText() }
                    JsonParser.parseString(raw).asJsonObject.entrySet()
                        .associate { entry -> entry.key to entry.value.asString }
                }
            }
        },
        write = { files ->
            withContext(Dispatchers.IO) {
                val bytes =
                    JsonObject().apply { files.forEach { (name, text) -> addProperty(name, text) } }
                        .toString().toByteArray(Charsets.UTF_8)
                val output = file.startWrite()
                try {
                    output.write(bytes); file.finishWrite(output)
                } catch (error: Exception) {
                    file.failWrite(output); throw error
                }
            }
        },
        fetch = { url -> resources.fetch(url, ChineseDictionaryStore.MAX_FILE_BYTES) },
        latest = metadata::latestDictionary
    )
    override val converter get() = store.converter
    override val version get() = store.version
    override val isWorking get() = store.isWorking
    override suspend fun load() = store.load()
    override suspend fun install() = store.install()
    override suspend fun update() = store.update()
}
