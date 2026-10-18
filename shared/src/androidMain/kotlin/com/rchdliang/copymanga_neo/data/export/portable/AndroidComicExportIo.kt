package com.rchdliang.copymanga_neo.data.export.portable

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI

/** SAF 只承担文档访问，文件命名、归档格式和任务状态使用共享实现。 */
class AndroidComicExportIo(context: Context, downloadRoot: File) : ComicExportIo {
    private val resolver = context.applicationContext.contentResolver
    private val root = downloadRoot.canonicalFile
    private fun source(location: String): File {
        val file =
            (if (location.startsWith("file:")) File(URI(location)) else File(location)).canonicalFile
        require(
            file.toPath()
                .startsWith(root.toPath()) && file != root && file.isFile && file.length() > 0
        ) {
            "下载图片缺失或不在下载目录"
        }
        return file
    }

    override suspend fun inspect(source: String): ComicExportPage = withContext(Dispatchers.IO) {
        val file = source(source)
        val header = file.inputStream().use { input ->
            val data = ByteArray(64)
            var size = 0
            while (size < data.size) {
                val count =
                    input.read(data, size, data.size - size); if (count < 0) break; size += count
            }
            data.copyOf(size)
        }
        ComicExportPage(file.toURI().toString(), file.length(), imageExtension(header))
    }

    private suspend fun readPage(page: ComicExportPage, emit: suspend (ByteArray) -> Unit) {
        val file = source(page.source)
        check(file.length() == page.size) { "下载图片已变化，请重新选择章节" }
        var count = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val length = input.read(buffer)
                if (length < 0) break
                count += length
                check(count <= page.size) { "下载图片长度已变化" }
                emit(buffer.copyOf(length))
            }
        }
        check(count == page.size) { "下载图片不完整" }
    }

    override suspend fun write(
        unit: ExportUnit,
        destination: String,
        directory: Boolean,
        format: ComicExportFormat,
        onCreated: suspend (String) -> Unit,
        onBytes: suspend (Long) -> Unit,
        onChapter: (String) -> Unit
    ): ComicExportOutput = withContext(Dispatchers.IO) {
        val selected = Uri.parse(destination)
        require(selected.scheme == "content") { "请选择系统文档位置" }
        val parent = if (directory) DocumentsContract.buildDocumentUriUsingTree(
            selected,
            DocumentsContract.getTreeDocumentId(selected)
        ) else null
        val target = when {
            format == ComicExportFormat.IMAGES -> {
                requireNotNull(parent) { "原图导出需要目录" }
                val comic = findDirectory(parent, comicExportName(unit.comic))
                val chapter = unit.chapters.single()
                val group = findDirectory(comic, exportGroupName(unit.comic, chapter.group))
                create(group, DocumentsContract.Document.MIME_TYPE_DIR, chapterExportName(chapter))
            }

            parent != null -> create(parent, format.mimeType, unit.name)
            else -> {
                // CreateDocument 创建的空文件才能作为单文件目标，不能覆盖已有内容。
                val size = resolver.query(
                    selected,
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                    null,
                    null,
                    null
                )?.use {
                    if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
                }
                require(size == 0L) { "目标文件已存在或无法确认为空，请重新选择保存位置" }
                selected
            }
        }
        onCreated(target.toString())
        if (format == ComicExportFormat.IMAGES) {
            val chapter = unit.chapters.single()
            onChapter(unit.comic.name + "／" + chapter.name)
            for ((index, page) in chapter.pages.withIndex()) {
                val uri = create(
                    target,
                    "image/" + if (page.extension == "jpg") "jpeg" else page.extension,
                    exportPageName(index, page)
                )
                output(uri).use { out -> readPage(page) { out.write(it); onBytes(it.size.toLong()) } }
            }
            output(create(target, "application/json", "manifest.json")).use {
                it.write(
                    exportManifest(unit).encodeToByteArray()
                )
            }
        } else output(target).use { out ->
            writeComicArchive(unit, format, { out.write(it) }, ::readPage, onBytes, onChapter)
        }
        currentCoroutineContext().ensureActive()
        ComicExportOutput(
            unit.id, target.toString(), unit.name,
            if (format == ComicExportFormat.IMAGES) DocumentsContract.Document.MIME_TYPE_DIR else format.mimeType
        )
    }

    private fun output(uri: Uri) =
        resolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入所选位置")

    private fun children(parent: Uri): Map<String, Pair<String, String>> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent)
        )
        return resolver.query(
            query, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(
                    cursor.getString(1),
                    cursor.getString(0) to cursor.getString(2)
                )
            }
        } ?: throw IOException("无法读取所选目录，请重新授权")
    }

    private fun findDirectory(parent: Uri, name: String): Uri {
        val existing = children(parent)[name]
        return if (existing?.second == DocumentsContract.Document.MIME_TYPE_DIR)
            DocumentsContract.buildDocumentUriUsingTree(parent, existing.first)
        else create(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    private fun create(parent: Uri, mime: String, name: String): Uri {
        val names = children(parent).keys
        val extension =
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) "" else name.substringAfterLast(
                '.',
                ""
            )
        val stem = if (extension.isEmpty()) name else name.removeSuffix(".$extension")
        var candidate = name
        var index = 2
        while (candidate in names) candidate =
            "$stem (${index++})" + if (extension.isEmpty()) "" else ".$extension"
        return DocumentsContract.createDocument(resolver, parent, mime, candidate)
            ?: throw IOException("无法创建导出文档")
    }

    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        } catch (_: Exception) {
            false
        }
    }
}
