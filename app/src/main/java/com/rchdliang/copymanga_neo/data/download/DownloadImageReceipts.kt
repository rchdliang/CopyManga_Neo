package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.domain.reading.ReadingContentHash
import java.io.File
import java.io.IOException

/** 调用方持有文件写租约；仅复用来源和长度均匹配、已完整发布的图片。 */
internal suspend fun downloadImageIfNeeded(
    image: File,
    url: String,
    publish: suspend (() -> Unit) -> Unit,
    download: suspend () -> Unit
) {
    val receipt = File(image.parentFile, "${image.name}.complete")
    fun payload(size: Long) =
        "1\n${ReadingContentHash.sha256(url.toByteArray(Charsets.UTF_8))}\n$size"

    val reusable = try {
        image.isFile && image.length() > 0 && receipt.isFile && receipt.length() <= 256 &&
                receipt.readBytes()
                    .decodeToString(throwOnInvalidSequence = true) == payload(image.length())
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
    if (reusable) return

    // 旧版未完成章节没有回执，不能把历史中断留下的非空文件当成完整图片。
    download()
    if (!image.isFile || image.length() <= 0) throw IOException("下载图片为空，无法记录完成状态")
    val bytes = payload(image.length()).toByteArray(Charsets.UTF_8)
    publish { writeDownloadedImage(receipt, bytes.inputStream(), bytes.size.toLong()) }
}
