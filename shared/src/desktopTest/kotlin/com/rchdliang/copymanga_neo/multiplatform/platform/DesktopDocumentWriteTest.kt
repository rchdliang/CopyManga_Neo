package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.platform.NetworkLogOutputStream
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogState
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDocumentWriteTest {
    private val directory = Files.createTempDirectory("desktop-document-write")
    private val host = DesktopPlatformHost()

    @AfterTest fun cleanUp() { directory.toFile().deleteRecursively() }

    @Test fun cancelledExportNeverPublishesOrReplacesDestination() = runBlocking {
        for (existing in listOf(false, true)) {
            val destination = directory.resolve(if (existing) "existing.jsonl" else "new.jsonl")
            val original = "original log".encodeToByteArray()
            if (existing) Files.write(destination, original)
            val job = launch {
                host.exportNetworkLog(destination.toString(), repository { output ->
                    output.write("cancelled replacement".encodeToByteArray())
                    // Cancellation can arrive after a synchronous producer writes its final bytes.
                    currentCoroutineContext().cancel()
                    Result.success(Unit)
                }).getOrThrow()
            }
            job.join()
            assertTrue(job.isCancelled)
            if (existing) assertContentEquals(original, Files.readAllBytes(destination))
            else assertFalse(Files.exists(destination))
            assertNoStagingFiles()
        }
    }

    @Test fun failedExportPreservesOriginalAndRemovesStaging() = runBlocking {
        val destination = directory.resolve("existing.jsonl")
        val original = "original log".encodeToByteArray()
        Files.write(destination, original)
        val result = host.exportNetworkLog(destination.toString(), repository { output ->
            output.write("incomplete replacement".encodeToByteArray())
            Result.failure(IOException("synthetic write failure"))
        })
        assertTrue(result.isFailure)
        assertContentEquals(original, Files.readAllBytes(destination))
        assertNoStagingFiles()
    }

    @Test fun successfulExportPublishesCompleteBytes() = runBlocking {
        val destination = directory.resolve("existing.jsonl")
        Files.writeString(destination, "old log")
        val bytes = "complete log\nsecond line\n".encodeToByteArray()
        host.exportNetworkLog(destination.toString(), repository { output ->
            output.write(bytes)
            Result.success(Unit)
        }).getOrThrow()
        assertContentEquals(bytes, Files.readAllBytes(destination))
        assertNoStagingFiles()
    }

    private fun assertNoStagingFiles() {
        Files.list(directory).use { files ->
            assertFalse(files.anyMatch { it.fileName.toString().startsWith(".copymanga-") })
        }
    }

    private fun repository(write: suspend (NetworkLogOutputStream) -> Result<Unit>) = object : NetworkLogRepository {
        override val state = MutableStateFlow(NetworkLogState())
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun clear(): Result<Unit> = Result.success(Unit)
        override suspend fun export(output: NetworkLogOutputStream): Result<Unit> = write(output)
    }
}
