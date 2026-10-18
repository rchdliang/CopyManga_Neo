package com.rchdliang.copymanga_neo.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class StreamingCacheTest {
    @Test fun interruptedTransferNeverReplacesCompleteFile() = runTest {
        val root = Files.createTempDirectory("copymanga-stream-")
        try {
            val storage = FileCacheStorage(root.toString())
            storage.write("downloads/page", byteArrayOf(9))
            for (cancel in listOf(false, true)) {
                assertFailsWith<Exception> {
                    storage.writeStream("downloads/page") { emit ->
                        emit(byteArrayOf(1, 2))
                        if (cancel) throw CancellationException("cancelled") else error("truncated response")
                    }
                }
                assertContentEquals(byteArrayOf(9), storage.read("downloads/page", 10))
                assertEquals(listOf("downloads/page"), storage.list())
            }
            storage.writeStream("downloads/page") { emit -> emit(byteArrayOf(3)); emit(byteArrayOf(4)) }
            assertContentEquals(byteArrayOf(3, 4), storage.read("downloads/page", 10))
        } finally { root.toFile().deleteRecursively() }
    }
}
