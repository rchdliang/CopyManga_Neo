package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 数据库记录和文件均完整时，才允许标记为本地可读。由 I/O 协程调用。 */
internal fun DownloadedChapterEntity.hasCompleteFiles(): Boolean {
    val paths = imagePaths.split(",").filter { it.isNotBlank() }
    return status == "downloaded" && imageCount > 0 && paths.size == imageCount &&
            paths.distinct().size == paths.size && paths.all {
        val file = File(it)
        file.isFile && file.length() > 0
    }
}

/** 图片完整写入后才发布正式文件；中断留下的半张图片不能被重试当成成功。 */
internal fun writeDownloadedImage(
    target: File,
    input: InputStream,
    expectedLength: Long,
    onBytes: (Int) -> Unit = {}
) {
    val temporary = File(target.parentFile, "${target.name}.part")
    try {
        var total = 0L
        temporary.outputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                total += read
                onBytes(read)
            }
        }
        if (total <= 0 || (expectedLength >= 0 && total != expectedLength)) {
            throw IOException("图片下载不完整：$total/$expectedLength 字节")
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}

/** 网络流逐块挂起限速；发布与任务取消互斥，失败时只清理本次临时文件。 */
internal suspend fun writeDownloadedImageSuspending(
    target: File,
    input: InputStream,
    expectedLength: Long,
    publish: suspend (() -> Unit) -> Unit,
    onBytes: suspend (Int) -> Unit
) {
    val temporary = File(target.parentFile, "${target.name}.part")
    try {
        var total = 0L
        temporary.outputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                onBytes(read)
                currentCoroutineContext().ensureActive()
                output.write(buffer, 0, read)
                total += read
            }
        }
        if (total <= 0 || (expectedLength >= 0 && total != expectedLength)) {
            throw IOException("图片下载不完整：$total/$expectedLength 字节")
        }
        publish {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}
