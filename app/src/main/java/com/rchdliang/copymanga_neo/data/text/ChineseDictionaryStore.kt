package com.rchdliang.copymanga_neo.data.text

import com.rchdliang.copymanga_neo.domain.repository.ChineseDictionaryRepository
import com.rchdliang.copymanga_neo.domain.repository.DictionaryUpdate
import com.rchdliang.copymanga_neo.domain.text.TraditionalChineseConverter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString

data class DictionaryFile(val sha: String, val size: Int)
data class DictionaryRelease(
    val version: String,
    val revision: String,
    val files: Map<String, DictionaryFile>
)

/** 同一提交的词典整套校验并原子提交，更新失败继续使用原版本。 */
class ChineseDictionaryStore(
    private val read: suspend () -> Map<String, String>?,
    private val write: suspend (Map<String, String>) -> Unit,
    private val fetch: suspend (String) -> ByteArray,
    private val latest: suspend () -> DictionaryRelease
) : ChineseDictionaryRepository {
    private val mutableConverter = MutableStateFlow<TraditionalChineseConverter?>(null)
    override val converter = mutableConverter.asStateFlow()
    private val mutableVersion = MutableStateFlow<String?>(null)
    override val version = mutableVersion.asStateFlow()
    private val mutableWorking = MutableStateFlow(false)
    override val isWorking = mutableWorking.asStateFlow()
    private var revision: String? = null
    private val mutex = Mutex()

    override suspend fun load(): Result<Unit> = attempt {
        mutex.withLock { if (mutableConverter.value == null) restore() }
    }

    override suspend fun install(): Result<Unit> = attempt {
        mutex.withLock {
            if (mutableConverter.value != null) return@withLock
            try {
                restore()
                return@withLock
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { /* 缺失或损坏时重新下载，完整写入前保留原文件。 */
            }
            download()
        }
    }

    override suspend fun update(): Result<DictionaryUpdate> = attempt {
        mutex.withLock {
            if (mutableConverter.value == null) {
                try {
                    restore()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                }
            }
            download()
        }
    }

    private suspend fun download(): DictionaryUpdate {
        mutableWorking.value = true
        try {
            val release = latest()
            require(release.version.matches(Regex("[0-9]+(\\.[0-9]+){1,3}"))) { "OpenCC 版本格式无效" }
            require(release.revision.matches(Regex("[0-9a-f]{40}"))) { "OpenCC 提交标识无效" }
            require(
                release.files.keys.containsAll(TraditionalChineseConverter.dictionaryFiles) &&
                        release.files.keys.all { it in allowedFiles }) { "OpenCC 词典文件不完整" }
            if (revision == release.revision && mutableVersion.value == release.version && mutableConverter.value != null)
                return DictionaryUpdate(release.version, false)
            val bundle = linkedMapOf("@version" to release.version, "@revision" to release.revision)
            release.files.forEach { (name, entry) ->
                require(entry.size in 1..MAX_FILE_BYTES && entry.sha.matches(Regex("[0-9a-f]{40}"))) { "OpenCC 文件元数据无效" }
                val bytes = fetch("$RAW_SOURCE/${release.revision}/data/dictionary/$name")
                require(bytes.size == entry.size && gitBlobHash(bytes) == entry.sha) { "词典校验失败，请重试" }
                bundle[name] = bytes.decodeToString(throwOnInvalidSequence = true)
                bundle["@hash:$name"] = sha256(bytes)
            }
            val resolved =
                TraditionalChineseConverter.fromDictionaries(bundle.filterKeys { !it.startsWith("@") })
            currentCoroutineContext().ensureActive()
            write(bundle)
            publish(release.version, release.revision, resolved)
            return DictionaryUpdate(release.version, true)
        } finally {
            mutableWorking.value = false
        }
    }

    private suspend fun restore() {
        val bundle = read() ?: error("请先下载繁转简词典")
        val files = bundle.filterKeys { !it.startsWith("@") }
        require(
            files.keys.containsAll(TraditionalChineseConverter.dictionaryFiles) &&
                    files.keys.all { it in allowedFiles }) { "词典不完整，请重新下载" }
        val legacy = "@version" !in bundle
        files.forEach { (name, text) ->
            val bytes = text.encodeToByteArray()
            val expected = if (legacy) legacyHashes[name] else bundle["@hash:$name"]
            require(bytes.size <= MAX_FILE_BYTES && expected != null && sha256(bytes) == expected) { "本地词典校验失败，请重新下载" }
        }
        val version = if (legacy) "1.1.9" else bundle.getValue("@version")
        val rev = if (legacy) "" else bundle.getValue("@revision")
        publish(version, rev, TraditionalChineseConverter.fromDictionaries(files))
    }

    private fun publish(version: String, revision: String, resolved: TraditionalChineseConverter) {
        this.revision = revision
        mutableVersion.value = version
        mutableConverter.value = resolved
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.Default) {
            try {
                Result.success(block())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    companion object {
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
        const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024
        const val MAX_METADATA_BYTES = 4 * 1024 * 1024
        const val DOCUMENT_KEY = "chinese-dictionary.json"
        const val LEGACY_DOCUMENT_KEY = "chinese-dictionary-1.1.9.json"
        const val API_SOURCE = "https://api.github.com/repos/BYVoid/OpenCC"
        const val RAW_SOURCE = "https://raw.githubusercontent.com/BYVoid/OpenCC"
        val allowedFiles =
            TraditionalChineseConverter.dictionaryFiles + "CJK_Compatibility_Ideographs.txt"

        fun sha256(bytes: ByteArray) = bytes.toByteString().sha256().hex()
        fun gitBlobHash(bytes: ByteArray) =
            ("blob ${bytes.size}\u0000".encodeToByteArray() + bytes).toByteString().sha1().hex()

        private val legacyHashes = mapOf(
            "TWVariantsRevPhrases.txt" to "bef60ceb4e57b6b062351406cb5d4644875574231d64787e03711317b7e773f3",
            "TWVariants.txt" to "30e6f8395edbfdd74e293fd8b9c62105d787c849fbb208d2a7832eac696734d7",
            "TSPhrases.txt" to "504169029c43f7f234b8e2ae470720af3657675c5574ff8aa0feb257e1dc5ce2",
            "TSCharacters.txt" to "6b5a0a799bea2bb22c001f635eaa3fc2904310f0c08addbff275477a80ecf09a"
        )
    }
}
