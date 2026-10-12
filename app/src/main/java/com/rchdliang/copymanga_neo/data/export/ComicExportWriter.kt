package com.rchdliang.copymanga_neo.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 逐张流式写入文档提供者；压缩包不依赖目标文件可随机访问，也不缓存整部漫画。 */
@Singleton
class ComicExportWriter @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver get() = context.contentResolver

    internal suspend fun write(
        unit: ExportUnit, destination: String, directory: Boolean, format: ComicExportFormat,
        onCreated: suspend (Uri) -> Unit, onBytes: suspend (Long) -> Unit,
        onChapter: (String) -> Unit
    ): ComicExportOutput {
        val parent = if (directory) treeRoot(destination.toUri()) else null
        val target = when {
            format == ComicExportFormat.IMAGES -> {
                val comicDir = findOrCreateDirectory(parent!!, comicExportName(unit.comic))
                val chapter = unit.chapters.single()
                val groupDir = findOrCreateDirectory(
                    comicDir,
                    ComicArchiveWriter.groupName(unit.comic, chapter.group)
                )
                create(
                    groupDir,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    chapterExportName(chapter)
                )
            }

            parent != null -> create(parent, format.mimeType, unit.name)
            else -> destination.toUri()
        }
        // 在第一次写入之前记录本次新建的目标，以便异常退出后清理。
        onCreated(target)
        if (format == ComicExportFormat.IMAGES) {
            val chapter = unit.chapters.single()
            onChapter("${unit.comic.name}／${chapter.name}")
            chapter.pages.forEachIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val uri = create(
                    target,
                    "image/" + if (page.extension == "jpg") "jpeg" else page.extension,
                    ComicArchiveWriter.pageName(index, page)
                )
                output(uri).use { ComicArchiveWriter.copyPage(page, it, onBytes) }
            }
            val manifest = create(target, "application/json", "manifest.json")
            output(manifest).use {
                it.write(
                    ComicArchiveWriter.manifest(unit).toByteArray(Charsets.UTF_8)
                )
            }
        } else {
            output(target).use { output ->
                ComicArchiveWriter.write(unit, format, output, onBytes, onChapter)
            }
        }
        currentCoroutineContext().ensureActive()
        val actualName = runCatching { displayName(target) }.getOrNull() ?: unit.name
        return ComicExportOutput(unit.id, target.toString(), actualName, format.mimeType)
    }

    private fun output(uri: Uri) =
        resolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入所选位置")

    private fun treeRoot(uri: Uri) =
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

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
        } ?: throw IOException("无法读取所选目录，请重新选择保存位置")
    }

    private fun findOrCreateDirectory(parent: Uri, name: String): Uri {
        val existing = children(parent)[name]
        if (existing?.second == DocumentsContract.Document.MIME_TYPE_DIR)
            return DocumentsContract.buildDocumentUriUsingTree(parent, existing.first)
        return create(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    private fun create(parent: Uri, mime: String, name: String): Uri {
        val names = children(parent).keys
        var safe = name
        var count = 2
        val extension =
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) "" else name.substringAfterLast(
                '.',
                ""
            )
        val stem = if (extension.isEmpty()) name else name.removeSuffix(".$extension")
        while (safe in names) safe =
            "$stem (${count++})" + if (extension.isEmpty()) "" else ".$extension"
        return DocumentsContract.createDocument(resolver, parent, mime, safe)
            ?: throw IOException("无法创建导出文件")
    }

    internal fun delete(uri: String): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri.toUri())
    } catch (error: Exception) {
        android.util.Log.w("ComicExport", "未完成文件清理失败", error)
        false
    }

    internal fun displayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
    )?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}
