package com.rchdliang.copymanga_neo.data.image.portable

import coil3.disk.DiskCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.buffer

/** Called on the adapter's I/O dispatcher. Snapshot pins the original bytes until the bounded read ends. */
internal suspend fun readCoilReaderImageCache(cache: DiskCache?, source: String, maximumBytes: Int): ByteArray? {
    val snapshot = cache?.openSnapshot(source) ?: return null
    try {
        val input = cache.fileSystem.source(snapshot.data).buffer()
        try {
            val output = Buffer()
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(output, minOf(8192L, maximumBytes.toLong() + 1 - output.size))
                    if (count < 0) break
                    require(output.size <= maximumBytes) { "图片超过单张安全大小限制" }
                }
                return output.readByteArray()
            } finally { output.clear() }
        } finally { input.close() }
    } finally { snapshot.close() }
}
