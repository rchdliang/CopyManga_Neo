package com.rchdliang.copymanga_neo.data.novel.cache

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.novel.NovelJson
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import java.io.File
import java.io.IOException

internal data class StoredNovelVolume(
    val directory: File,
    val manifest: JsonObject,
    val reading: NovelReadingVolume
)

/** 仓库持有卷操作互斥锁；元数据操作另行同步，避免搜索请求阻塞正文下载。 */
internal class NovelCacheStore(
    private val root: File,
    private val metadataBytesLimit: Long = 16L * 1024 * 1024,
    private val metadataCountLimit: Int = 256,
    private val metadataAgeMillis: Long = 30L * 24 * 60 * 60 * 1000,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val JSON_LIMIT = 2 * 1024 * 1024
        const val TEXT_LIMIT = 16 * 1024 * 1024
        const val IMAGE_LIMIT = 16 * 1024 * 1024
        private const val VERSION = 2
        private val cacheName = Regex("[0-9a-f]{64}")
    }

    private val volumesRoot get() = File(root, "volumes")
    private val metadataRoot get() = File(root, "metadata")

    @Volatile
    var generation: Long = 0L
        private set

    fun directory(mode: RequestMode, owner: String, path: String, id: String): File =
        File(volumesRoot, identity(mode, owner, path, id))

    private fun identity(mode: RequestMode, owner: String, path: String, id: String): String =
        NovelTextDocument.sha256(listOf(mode.name, owner, path, id).joinToString("\u0000"))

    fun read(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String,
        repair: Boolean = true
    ): StoredNovelVolume? {
        val directory = directory(mode, owner, path, id)
        if (!File(directory, "manifest.json").isFile) return null
        return load(directory, repair).also {
            require(it.manifest["mode"].asString == mode.name && it.manifest["owner"].asString == owner)
            require(it.reading.book.path == path && it.reading.volume.id == id)
        }
    }

    private fun load(directory: File, repair: Boolean = true): StoredNovelVolume {
        val manifest = JsonParser.parseString(
            readFile(File(directory, "manifest.json"), JSON_LIMIT).toString(Charsets.UTF_8)
        ).asJsonObject
        require(manifest["version"]?.asInt == VERSION) { "小说缓存格式无效" }
        val mode = RequestMode.valueOf(manifest["mode"].asString)
        val owner = manifest["owner"].asString
        val result = manifest.getAsJsonObject("result")
        val book = NovelJson.book(result.getAsJsonObject("book"))
        val volumeJson = result.getAsJsonObject("volume")
        val volume = NovelJson.volume(volumeJson)
        require(owner.isNotBlank() && book.path.isNotBlank() && volume.id.isNotBlank())
        require(
            directory.name == identity(
                mode,
                owner,
                book.path,
                volume.id
            )
        ) { "小说缓存身份无效" }
        val raw = VerifiedVolumeCache.read(
            directory,
            manifest["text_file"].asString,
            manifest["text_sha256"].asString,
            TEXT_LIMIT
        )
        val document = NovelTextDocument.decode(raw, NovelJson.text(volumeJson, "txt_encoding"))
        val sections = NovelJson.sections(volumeJson, document)
        val metadata = manifest.getAsJsonObject("images")
        val images = linkedMapOf<String, String>()
        val validMetadata = JsonObject()
        for (url in sections.mapNotNull { it.image.takeIf { _ -> it.type == 2 } }.distinct()) {
            val key = NovelTextDocument.sha256(url)
            val file = File(directory, "$key.img")
            val digest = runCatching { metadata[key]?.asString }.getOrNull()
            if (digest != null && runCatching {
                    AtomicCacheFile.digest(
                        readFile(
                            file,
                            IMAGE_LIMIT
                        )
                    ) == digest
                }.getOrDefault(false)) {
                images[url] = file.absolutePath
                validMetadata.addProperty(key, digest)
            }
        }
        if (metadata != validMetadata) {
            manifest.add("images", validMetadata)
            if (repair) writeManifest(directory, manifest)
        }
        if (repair) cleanUnreferenced(directory, manifest)
        val total = sections.filter { it.type == 2 }.map { it.image }.distinct().size
        return StoredNovelVolume(
            directory,
            manifest,
            NovelReadingVolume(book, volume, sections, document, images, images.size == total)
        )
    }

    fun publish(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String,
        result: JsonObject,
        bytes: ByteArray,
        previous: StoredNovelVolume?,
        downloadRequested: Boolean = false,
        validateSession: () -> Unit
    ): StoredNovelVolume {
        val directory = directory(mode, owner, path, id)
        val book = NovelJson.book(result.getAsJsonObject("book"))
        val volume = NovelJson.volume(result.getAsJsonObject("volume"))
        require(book.path == path && volume.id == id) { "官方返回的小说或分卷标识不一致" }
        val manifest = JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("mode", mode.name)
            addProperty("owner", owner)
            addProperty("last_accessed", now())
            // 刷新正文不能降级已下载卷；是否缓存完整与下载意图无关。
            addProperty(
                "download_requested", downloadRequested ||
                    previous?.manifest?.get("download_requested")
                        ?.takeUnless { it.isJsonNull }?.asBoolean == true
            )
            add("result", result.deepCopy())
            addProperty("text_sha256", AtomicCacheFile.digest(bytes))
            addProperty("text_file", VerifiedVolumeCache.textName(bytes))
            add("images", previous?.manifest?.getAsJsonObject("images")?.deepCopy() ?: JsonObject())
        }
        VerifiedVolumeCache.publish(
            directory,
            bytes,
            manifest.toString().toByteArray(Charsets.UTF_8)
        ) { body ->
            val json = result.getAsJsonObject("volume")
            NovelJson.sections(
                json,
                NovelTextDocument.decode(body, NovelJson.text(json, "txt_encoding"))
            )
            validateSession()
        }
        return load(directory)
    }

    fun writeImage(cached: StoredNovelVolume, url: String, bytes: ByteArray): StoredNovelVolume {
        require(cached.reading.sections.any { it.type == 2 && it.image == url })
        val key = NovelTextDocument.sha256(url)
        val file = File(cached.directory, "$key.img")
        val manifest = cached.manifest.deepCopy()
        AtomicCacheFile.write(file, bytes)
        manifest.getAsJsonObject("images").addProperty(key, AtomicCacheFile.digest(bytes))
        writeManifest(cached.directory, manifest)
        val images = cached.reading.cachedImages + (url to file.absolutePath)
        val total = cached.reading.sections.filter { it.type == 2 }.map { it.image }.distinct().size
        return cached.copy(
            manifest = manifest,
            reading = cached.reading.copy(
                cachedImages = images,
                offlineReady = images.size == total
            )
        )
    }

    fun touch(cached: StoredNovelVolume): StoredNovelVolume {
        val manifest = cached.manifest.deepCopy().apply { addProperty("last_accessed", now()) }
        writeManifest(cached.directory, manifest)
        return cached.copy(manifest = manifest)
    }

    /** 用户明确下载已有正文时只更新意图，复用阅读缓存及插图。 */
    fun markDownloadRequested(cached: StoredNovelVolume): StoredNovelVolume {
        if (cached.manifest["download_requested"]?.takeUnless { it.isJsonNull }?.asBoolean == true) return cached
        val manifest = cached.manifest.deepCopy().apply {
            addProperty("download_requested", true)
        }
        writeManifest(cached.directory, manifest)
        return cached.copy(manifest = manifest)
    }

    fun describe(cached: StoredNovelVolume): NovelCachedVolume = NovelCachedVolume(
        RequestMode.valueOf(cached.manifest["mode"].asString),
        cached.manifest["owner"].asString,
        cached.reading.book,
        cached.reading.volume,
        directoryBytes(cached.directory),
        cached.reading.cachedImages.size,
        cached.reading.sections.filter { it.type == 2 }.map { it.image }.distinct().size,
        cached.manifest["last_accessed"].asLong,
        cached.manifest["download_requested"]?.takeUnless { it.isJsonNull }?.asBoolean == true
    )

    fun scan(): List<NovelCachedVolume> =
        volumesRoot.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
            try {
                require(cacheName.matches(directory.name))
                describe(load(directory))
            } catch (_: Exception) {
                // 仅孤立临时卷可直接回收；读取失败不得删除仍有清单的已下载内容。
                if (!File(directory, "manifest.json").isFile) remove(directory)
                null
            }
        }.sortedByDescending { it.lastAccessedAt }

    fun removeVolume(mode: RequestMode, owner: String, path: String, id: String) =
        remove(directory(mode, owner, path, id))

    fun removeBook(mode: RequestMode, owner: String, path: String) {
        volumesRoot.listFiles().orEmpty().filter { it.isDirectory }.forEach { directory ->
            val manifest = runCatching {
                JsonParser.parseString(
                    readFile(
                        File(directory, "manifest.json"),
                        JSON_LIMIT
                    ).toString(Charsets.UTF_8)
                ).asJsonObject
            }.getOrNull() ?: return@forEach
            val matches = runCatching {
                val result = manifest.getAsJsonObject("result")
                val id = NovelJson.text(result.getAsJsonObject("volume"), "id")
                manifest["mode"].asString == mode.name && manifest["owner"].asString == owner &&
                        NovelJson.text(result.getAsJsonObject("book"), "path_word") == path &&
                        directory.name == identity(mode, owner, path, id)
            }.getOrDefault(false)
            if (matches) remove(directory)
        }
    }

    private fun remove(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("小说缓存删除失败")
    }

    private fun cleanUnreferenced(directory: File, manifest: JsonObject) {
        val retained = manifest.getAsJsonObject("images").keySet().map { "$it.img" }.toSet() +
                setOf("manifest.json", manifest["text_file"].asString)
        directory.listFiles().orEmpty().filter { it.name !in retained }.forEach {
            if (!it.deleteRecursively()) throw IOException("小说失效缓存清理失败")
        }
    }

    private fun writeManifest(directory: File, manifest: JsonObject) =
        AtomicCacheFile.write(
            File(directory, "manifest.json"),
            manifest.toString().toByteArray(Charsets.UTF_8)
        )

    @Synchronized
    fun readMetadata(key: String): ByteArray? {
        val file = File(metadataRoot, "$key.json")
        if (!file.isFile || now() - file.lastModified() > metadataAgeMillis) return null
        return readFile(file, JSON_LIMIT).also { file.setLastModified(now()) }
    }

    @Synchronized
    fun writeMetadata(key: String, bytes: ByteArray, expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        AtomicCacheFile.write(File(metadataRoot, "$key.json"), bytes)
        File(metadataRoot, "$key.json").setLastModified(now())
        pruneMetadata()
    }

    @Synchronized
    fun pruneMetadata() {
        val valid = metadataRoot.listFiles().orEmpty().filter { file ->
            val expired =
                !file.isFile || !file.name.endsWith(".json") || now() - file.lastModified() > metadataAgeMillis
            if (expired) remove(file)
            !expired
        }.sortedByDescending { it.lastModified() }
        var bytes = 0L
        valid.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= metadataCountLimit || bytes > metadataBytesLimit) remove(file)
        }
    }

    @Synchronized
    fun usageBytes(): Long = directoryBytes(root)

    @Synchronized
    fun clear() {
        generation++
        remove(root)
    }

    private fun directoryBytes(directory: File): Long =
        if (!directory.exists()) 0L else directory.walkTopDown().filter { it.isFile }
            .sumOf { it.length() }

    private fun readFile(file: File, limit: Int) =
        file.inputStream().use { AtomicCacheFile.readBounded(it, limit) }
}
