package com.rchdliang.copymanga_neo.data.novel.portable.cache

import com.rchdliang.copymanga_neo.data.novel.portable.PortableNovelCache
import com.rchdliang.copymanga_neo.data.novel.portable.PortableNovelJson
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.domain.reading.ReadingContentHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
private data class MetadataDocument(val accessed: Long, val value: kotlinx.serialization.json.JsonElement)

@Serializable
private data class VolumeManifest(
    val version: Int = 2,
    val mode: RequestMode,
    val owner: String,
    @SerialName("last_accessed") val lastAccessed: Long,
    val result: JsonObject,
    @SerialName("text_sha256") val textHash: String,
    @SerialName("text_file") val textFile: String,
    val images: Map<String, String> = emptyMap()
)

/**
 * Version-2 Android-compatible manifests and content-addressed text. Every read validates
 * ownership, raw text digest, official ranges and individual image digests before exposing URIs.
 */
class VerifiedPortableNovelCache(
    private val storage: CacheStorage,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) : PortableNovelCache {
    private val mutex = Mutex()
    private val cacheGeneration = MutableStateFlow(0L)
    override val generation: Long get() = cacheGeneration.value
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val digestPattern = Regex("[0-9a-f]{64}")
    private val root = "novel/volumes"

    private fun directory(mode: RequestMode, owner: String, path: String, id: String): String {
        require(owner.isNotBlank() && path.isNotBlank() && id.isNotBlank()) { "小说缓存身份无效" }
        val parts = listOf(mode.name, owner, path, id)
        require(parts.none { '\u0000' in it }) { "小说缓存身份包含无效字符" }
        return "$root/${NovelTextDocument.sha256(parts.joinToString("\u0000"))}"
    }
    private suspend fun manifest(directory: String): VolumeManifest? =
        storage.read("$directory/manifest.json", JSON_LIMIT)?.let {
            json.decodeFromString(VolumeManifest.serializer(), it.decodeToString(throwOnInvalidSequence = true))
        }
    private suspend fun writeManifest(directory: String, manifest: VolumeManifest) {
        val bytes = json.encodeToString(VolumeManifest.serializer(), manifest).encodeToByteArray()
        require(bytes.size <= JSON_LIMIT) { "小说缓存元数据超过大小限制" }
        storage.write("$directory/manifest.json", bytes)
    }
    private data class Loaded(val manifest: VolumeManifest, val reading: NovelReadingVolume)
    private suspend fun load(directory: String, repair: Boolean = true): Loaded? {
        var manifest = manifest(directory) ?: return null
        require(manifest.version == 2) { "小说缓存格式无效" }
        require(digestPattern.matches(manifest.textHash) && manifest.textFile == "text.${manifest.textHash}.txt") {
            "小说缓存正文身份无效"
        }
        val bytes = storage.read("$directory/${manifest.textFile}", TEXT_LIMIT)
            ?: throw PlatformIOException("小说缓存正文缺失")
        require(ReadingContentHash.sha256(bytes) == manifest.textHash) { "小说缓存正文校验失败" }
        val reading = PortableNovelJson.parseReading(manifest.result.toString(), bytes)
        require(directory == directory(manifest.mode, manifest.owner, reading.book.path, reading.volume.id)) {
            "小说缓存身份无效"
        }
        val urls = reading.sections.filter { it.type == 2 }.mapNotNull { it.image }.distinct()
        val validImages = linkedMapOf<String, String>()
        val locations = linkedMapOf<String, String>()
        for (url in urls) {
            val key = NovelTextDocument.sha256(url)
            val hash = manifest.images[key]?.takeIf { digestPattern.matches(it) } ?: continue
            val imagePath = "$directory/$key.img"
            try {
                val image = storage.read(imagePath, IMAGE_LIMIT) ?: continue
                if (ReadingContentHash.sha256(image) == hash) {
                    validImages[key] = hash
                    locations[url] = storage.imageLocation(imagePath)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Keep the text readable; report this image as uncached. */ }
        }
        if (repair && manifest.images != validImages) {
            manifest = manifest.copy(images = validImages)
            writeManifest(directory, manifest)
        }
        return Loaded(manifest, reading.copy(cachedImages = locations, offlineReady = locations.size == urls.size))
    }
    private suspend fun clean(directory: String, manifest: VolumeManifest) {
        val retained = manifest.images.keys.map { "$directory/$it.img" }.toSet() +
            setOf("$directory/manifest.json", "$directory/${manifest.textFile}")
        storage.list(directory).filterNot { it in retained }.forEach { storage.remove(it) }
    }
    override suspend fun read(mode: RequestMode, owner: String, path: String, id: String): NovelReadingVolume? = mutex.withLock {
        val dir = directory(mode, owner, path, id)
        val loaded = load(dir) ?: return@withLock null
        require(loaded.manifest.mode == mode && loaded.manifest.owner == owner &&
            loaded.reading.book.path == path && loaded.reading.volume.id == id) { "小说缓存身份不一致" }
        writeManifest(dir, loaded.manifest.copy(lastAccessed = now()))
        loaded.reading
    }
    override suspend fun publish(
        mode: RequestMode, owner: String, path: String, id: String,
        metadataJson: String, textBytes: ByteArray
    ): NovelReadingVolume = mutex.withLock {
        require(metadataJson.encodeToByteArray().size <= JSON_LIMIT && textBytes.size <= TEXT_LIMIT) {
            "小说缓存资源超过大小限制"
        }
        val dir = directory(mode, owner, path, id)
        // Validate before either file is published, retaining the prior revision on any failure.
        val reading = PortableNovelJson.parseReading(metadataJson, textBytes)
        require(reading.book.path == path && reading.volume.id == id) { "官方返回的小说或分卷标识不一致" }
        // Corrupt metadata must not block a validated network replacement. Do not remove the
        // previous files: the atomic manifest write below remains the publication boundary.
        val previous = try { manifest(dir) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
        val hash = ReadingContentHash.sha256(textBytes)
        val manifest = VolumeManifest(
            mode = mode, owner = owner, lastAccessed = now(),
            result = Json.parseToJsonElement(metadataJson).jsonObject,
            textHash = hash, textFile = "text.$hash.txt", images = previous?.images.orEmpty()
        )
        storage.write("$dir/${manifest.textFile}", textBytes)
        writeManifest(dir, manifest)
        val loaded = requireNotNull(load(dir))
        clean(dir, loaded.manifest)
        loaded.reading
    }
    override suspend fun writeImage(
        mode: RequestMode, owner: String, path: String, id: String, url: String, bytes: ByteArray
    ): NovelReadingVolume = mutex.withLock {
        require(bytes.isNotEmpty() && bytes.size <= IMAGE_LIMIT) { "小说插图资源超过大小限制或为空" }
        val dir = directory(mode, owner, path, id)
        val loaded = load(dir) ?: throw PlatformIOException("请先缓存小说正文")
        require(loaded.reading.sections.any { it.type == 2 && it.image == url }) { "插图不属于当前分卷" }
        val key = NovelTextDocument.sha256(url)
        storage.write("$dir/$key.img", bytes)
        writeManifest(dir, loaded.manifest.copy(images = loaded.manifest.images + (key to ReadingContentHash.sha256(bytes))))
        requireNotNull(load(dir)).reading
    }
    private suspend fun scan(): List<NovelCachedVolume> = storage.list(root)
        .filter { it.endsWith("/manifest.json") && digestPattern.matches(it.removeSuffix("/manifest.json").substringAfterLast('/')) }
        .mapNotNull { path ->
            val directory = path.removeSuffix("/manifest.json")
            try {
                val loaded = load(directory) ?: return@mapNotNull null
                NovelCachedVolume(
                    loaded.manifest.mode, loaded.manifest.owner, loaded.reading.book, loaded.reading.volume,
                    storage.list(directory).sumOf { storage.size(it) }, loaded.reading.cachedImages.size,
                    loaded.reading.sections.filter { it.type == 2 }.mapNotNull { it.image }.distinct().size,
                    loaded.manifest.lastAccessed
                )
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null } // Never delete a manifest-backed volume because reading failed.
        }.sortedByDescending { it.lastAccessedAt }
    override suspend fun list(): List<NovelCachedVolume> = mutex.withLock { scan() }
    override suspend fun removeVolume(mode: RequestMode, owner: String, path: String, id: String) = mutex.withLock {
        storage.remove(directory(mode, owner, path, id))
    }
    override suspend fun removeBook(mode: RequestMode, owner: String, path: String) = mutex.withLock {
        // Read identities from manifests, even if a damaged text file makes a volume unlistable.
        for (entry in storage.list(root).filter { it.endsWith("/manifest.json") }) {
            val dir = entry.removeSuffix("/manifest.json")
            val manifest = try { manifest(dir) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
                ?: continue
            val book = (manifest.result["book"] as? JsonObject)?.let(PortableNovelJson::book) ?: continue
            val volume = (manifest.result["volume"] as? JsonObject)?.let(PortableNovelJson::volume) ?: continue
            if (manifest.mode == mode && manifest.owner == owner && book.path == path &&
                dir == directory(mode, owner, path, volume.id)) storage.remove(dir)
        }
    }
    override suspend fun clear() = mutex.withLock {
        cacheGeneration.value += 1
        storage.remove("novel")
    }
    override suspend fun readMetadata(key: String): String? = mutex.withLock {
        require(digestPattern.matches(key)) { "Invalid metadata key" }
        val path = "novel/metadata/$key.json"
        val bytes = storage.read(path, JSON_LIMIT + 1024) ?: return@withLock null
        val entry = json.decodeFromString(MetadataDocument.serializer(), bytes.decodeToString(throwOnInvalidSequence = true))
        if (now() - entry.accessed > METADATA_MAX_AGE) return@withLock null
        require(entry.value.toString().encodeToByteArray().size <= JSON_LIMIT) { "Metadata exceeds cache limit" }
        storage.write(path, json.encodeToString(MetadataDocument.serializer(), entry.copy(accessed = now())).encodeToByteArray())
        entry.value.toString()
    }
    override suspend fun writeMetadata(key: String, value: String, expectedGeneration: Long) = mutex.withLock {
        if (generation != expectedGeneration) return@withLock
        require(digestPattern.matches(key)) { "Invalid metadata key" }
        require(value.encodeToByteArray().size <= JSON_LIMIT) { "Metadata exceeds cache limit" }
        val path = "novel/metadata/$key.json"
        val entry = MetadataDocument(now(), Json.parseToJsonElement(value))
        storage.write(path, json.encodeToString(MetadataDocument.serializer(), entry).encodeToByteArray())
        val retained = mutableListOf<Pair<String, MetadataDocument>>()
        for (candidate in storage.list("novel/metadata")) {
            val bytes = storage.read(candidate, 2 * JSON_LIMIT + 1024) ?: continue
            val record = json.decodeFromString(MetadataDocument.serializer(), bytes.decodeToString(throwOnInvalidSequence = true))
            if (now() - record.accessed > METADATA_MAX_AGE) storage.remove(candidate)
            else retained += candidate to record
        }
        var total = 0L
        retained.sortedByDescending { it.second.accessed }.forEachIndexed { index, (candidate, record) ->
            total += record.value.toString().encodeToByteArray().size
            if (index >= 256 || total > 16L * 1024 * 1024) storage.remove(candidate)
        }
    }
    override suspend fun usage(): NovelCacheUsage = mutex.withLock {
        NovelCacheUsage(storage.list("novel").sumOf { storage.size(it) }, scan().size)
    }
    companion object {
        private const val METADATA_MAX_AGE = 30L * 24 * 60 * 60 * 1000
        const val JSON_LIMIT = 2 * 1024 * 1024
        const val TEXT_LIMIT = 16 * 1024 * 1024
        const val IMAGE_LIMIT = 16 * 1024 * 1024
    }
}
