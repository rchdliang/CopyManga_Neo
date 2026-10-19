package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.data.image.portable.ReaderImageIo
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStore
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
private data class IllustrationDraft(val name: String, val book: String, val volume: String, val images: List<String>)

/** 逐张准备原图，共用现有流式 ZIP 和各平台文档写入；不把整卷装进内存。 */
class PortableNovelIllustrationExportRepository(
    private val images: ReaderImageRepository,
    private val imageIo: ReaderImageIo,
    private val archives: ComicExportIo,
    private val cache: CacheStorage,
    private val documents: DocumentStorage,
    private val generation: () -> Long
) : NovelIllustrationExportRepository {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val index = DocumentStore(documents, "novel-illustration-export-index.json",
        MapSerializer(String.serializer(), Long.serializer()), emptyMap())

    override suspend fun prepareImage(volume: NovelReadingVolume, image: NovelSection): Result<ReaderExportImage> {
        val version = generation()
        val title = "${volume.book.name}_${volume.volume.name}_${image.name}"
        val local = volume.cachedImages[image.image]
        if (local != null) {
            val cached = images.prepare(local, title)
            cached.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (cached.isSuccess) return current(version, cached)
        }
        return current(version, images.prepare(image.image.orEmpty(), title))
    }

    private fun current(version: Long, result: Result<ReaderExportImage>): Result<ReaderExportImage> =
        if (version == generation()) result else Result.failure(IllegalStateException("会话已变化，请重新导出"))

    override suspend fun prepareVolume(volume: NovelReadingVolume): Result<ReaderExportImage> = mutex.withLock {
        val id = Uuid.random().toString()
        try {
            val version = generation()
            val illustrations = NovelReaderDocument(volume.document, volume.sections).images
            require(illustrations.isNotEmpty()) { "本卷没有可导出的插图" }
            index.ensureLoaded().fold({}, { throw it })
            val cutoff = Clock.System.now().toEpochMilliseconds() - 2L * 24 * 60 * 60 * 1000
            index.state.value.filterValues { it < cutoff }.keys.toList().forEach { cleanup(it) }
            index.update { it + (id to Clock.System.now().toEpochMilliseconds()) }.fold({}, { throw it })
            val prepared = mutableListOf<String>()
            for ((position, illustration) in illustrations.withIndex()) {
                currentCoroutineContext().ensureActive()
                check(version == generation()) { "会话已变化，请重新导出" }
                val image = prepareImage(volume, illustration).fold({ it }, { throw it })
                val bytes = imageIo.readPrepared(image, 64 * 1024 * 1024)
                val extension = imageExtension(bytes.copyOf(minOf(bytes.size, 64)))
                val path = prefix(id) + (position + 1).toString().padStart(5, '0') + "." + extension
                cache.write(path, bytes)
                prepared += path
            }
            check(version == generation()) { "会话已变化，请重新导出" }
            val name = exportFileName("${exportName(volume.book.name)}_${exportName(volume.volume.name)}_插图", "zip")
            documents.write(key(id), json.encodeToString(IllustrationDraft.serializer(),
                IllustrationDraft(name, volume.book.name, volume.volume.name, prepared)))
            Result.success(ReaderExportImage("novel-zip:$id", "application/zip", name))
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) { runCatching { cleanup(id) } }
            throw cancel
        } catch (error: Exception) {
            withContext(NonCancellable) { runCatching { cleanup(id) } }
            Result.failure(error)
        }
    }

    override suspend fun saveToDocument(file: ReaderExportImage, destination: String): Result<Unit> {
        if (file.mimeType != "application/zip") return images.saveToDocument(file, destination)
        return mutex.withLock {
            var created: String? = null
            var id: String? = null
            try {
                id = archiveId(file)
                require(destination.isNotBlank()) { "请选择保存位置" }
                val draft = documents.read(key(id))?.let { json.decodeFromString(IllustrationDraft.serializer(), it) }
                    ?: error("导出临时文件已过期，请重试")
                require(file.displayName == draft.name && draft.images.isNotEmpty()) { "导出记录无效" }
                val pages = draft.images.map { path ->
                    require(path.startsWith(prefix(id)) && path.removePrefix(prefix(id)).matches(Regex("[0-9]+[.](jpg|png|webp|gif|avif|heic)"))) { "插图路径无效" }
                    archives.inspect(cache.imageLocation(path))
                }
                val chapter = ComicExportChapter(id, draft.volume, 1, "插图", pages)
                val comic = ComicExportComic(id, draft.book, "", listOf(chapter))
                archives.write(ExportUnit(id, comic, listOf(chapter), draft.name), destination, false, ComicExportFormat.ZIP,
                    onCreated = { created = it }, onBytes = {}, onChapter = {})
                created = null
                Result.success(Unit)
            } catch (cancel: CancellationException) {
                withContext(NonCancellable) { created?.let { archives.delete(it) } }
                throw cancel
            } catch (error: Exception) {
                withContext(NonCancellable) { created?.let { archives.delete(it) } }
                Result.failure(error)
            } finally {
                id?.let { withContext(NonCancellable) { runCatching { cleanup(it) } } }
            }
        }
    }

    override suspend fun discard(file: ReaderExportImage) {
        if (file.mimeType == "application/zip") mutex.withLock { cleanup(archiveId(file)) }
    }

    private fun archiveId(file: ReaderExportImage): String {
        require(file.filePath.startsWith("novel-zip:")) { "导出记录无效" }
        return file.filePath.removePrefix("novel-zip:").also { require(it.matches(Regex("[a-f0-9-]{36}"))) }
    }
    private fun prefix(id: String) = "downloads/novel-illustration-exports/$id/"
    private fun key(id: String) = "novel-illustration-export-$id.json"
    private suspend fun cleanup(id: String) {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val prefix = prefix(id)
        cache.list(prefix.removeSuffix("/")).filter { it.startsWith(prefix) }.forEach { path ->
            require(path.startsWith(prefix))
            cache.remove(path)
        }
        documents.remove(key(id))
        index.update { it - id }.fold({}, { throw it })
    }
}
