package com.rchdliang.copymanga_neo.data.export

import android.content.ClipData
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportState

/** 通知直接交给系统打开或分享，授予接收应用读取本次导出文档的权限。 */
internal object ComicExportIntents {
    fun open(output: ComicExportOutput): Intent = chooser(
        Intent(Intent.ACTION_VIEW).apply {
            val uri = output.uri.toUri()
            setDataAndType(uri, output.mimeType)
            if (output.mimeType == DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            clipData = ClipData.newRawUri(output.name, uri)
        }, "选择应用打开漫画"
    )

    fun share(outputs: List<ComicExportOutput>): Intent {
        require(outputs.isNotEmpty())
        val uris = ArrayList(outputs.map { it.uri.toUri() })
        return chooser(Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = outputs.map { it.mimeType }.distinct().singleOrNull() ?: "application/*"
            putExtra(Intent.EXTRA_TITLE, "分享导出的漫画")
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri(outputs.first().name, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }, "分享导出的漫画")
    }

    fun openFolder(state: ComicExportState): Intent {
        val tree = state.destination.toUri()
        val root = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        return open(
            ComicExportOutput(
                "directory",
                root.toString(),
                "漫画保存位置",
                DocumentsContract.Document.MIME_TYPE_DIR
            )
        )
    }

    private fun chooser(target: Intent, title: String): Intent {
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(target, title).apply {
            clipData = target.clipData
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        (target.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            )
        }
    }
}
