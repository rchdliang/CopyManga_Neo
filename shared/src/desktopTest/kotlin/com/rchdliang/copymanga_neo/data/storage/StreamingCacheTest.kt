package com.rchdliang.copymanga_neo.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class StreamingCacheTest {
    @Test fun parallelPagesCanCreateTheSameNewChapterDirectory() = runTest {
        val root = Files.createTempDirectory("copymanga-parallel-directory-")
        try {
            val storage = FileCacheStorage(root.toString())
            repeat(5) { chapter ->
                val gate = CompletableDeferred<Unit>()
                val pages = (0..9).map { page ->
                    async(Dispatchers.IO) {
                        gate.await()
                        storage.writeStream("downloads/$chapter/$page.jpg") { emit ->
                            emit(byteArrayOf(chapter.toByte(), page.toByte()))
                        }
                    }
                }
                gate.complete(Unit)
                pages.awaitAll()
                assertEquals(10, storage.list("downloads/$chapter").size)
                repeat(10) { page ->
                    assertContentEquals(byteArrayOf(chapter.toByte(), page.toByte()),
                        storage.read("downloads/$chapter/$page.jpg", 10))
                }
            }
        } finally { root.toFile().deleteRecursively() }
    }
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
