package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.reading.ReadingContentHash
import kotlinx.coroutines.CancellationException

/** 仅复用已完整发布、来源和长度均匹配的图片；临时文件和无回执文件重新下载。 */
internal class DownloadImageReceipts(private val storage: CacheStorage) {
    suspend fun isComplete(path: String, url: String): Boolean = try {
        val receipt = storage.read("$path.complete", 256)?.decodeToString(throwOnInvalidSequence = true)
        val size = storage.size(path)
        size > 0 && receipt == payload(url, size)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        false
    }

    /** 必须在传输内容校验与原子发布成功后调用；只保存来源摘要，不落盘签名 URL。 */
    suspend fun record(path: String, url: String) {
        val size = storage.size(path)
        check(size > 0) { "下载图片为空，无法记录完成状态" }
        storage.write("$path.complete", payload(url, size).encodeToByteArray())
    }

    private fun payload(url: String, size: Long) =
        "1\n${ReadingContentHash.sha256(url.encodeToByteArray())}\n$size"
}
