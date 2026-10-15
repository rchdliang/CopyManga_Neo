package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadPersistenceTest {
    private class Documents : DocumentStorage {
        val data = mutableMapOf<String, String>()
        var failWrites = false
        override suspend fun read(key: String) = data[key]
        override suspend fun write(key: String, value: String) {
            if (failWrites) throw PlatformIOException("disk full")
            data[key] = value
        }
        override suspend fun remove(key: String) { data.remove(key) }
    }
    private class Files : CacheStorage {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String, limit: Int) = files[path]?.also { require(it.size <= limit) }
        override suspend fun write(path: String, bytes: ByteArray) { files[path] = bytes }
        override suspend fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }
        override suspend fun remove(path: String) { files.remove(path) }
        override suspend fun size(path: String) = files[path]?.size?.toLong() ?: throw PlatformIOException("missing")
        override suspend fun availableBytes() = 1_000_000L
        override suspend fun imageLocation(path: String) = "file:///cache/$path"
    }
    private val chapter = DownloadedChapterRecord(
        "chapter", "book", "第一章", 1, 2, listOf("one.jpg", "two.jpg"), "downloaded", 123L
    )

    @Test fun incompleteFilesNeverBecomeReadableMetadata() = runTest {
        val files = Files()
        val records = DownloadRecords(Documents(), files)
        files.write("one.jpg", byteArrayOf(1))
        assertFalse(records.hasCompleteFiles(chapter))
        assertFailsWith<IllegalStateException> { records.insertChapter(chapter) }
        assertNull(records.getChapter(chapter.uuid))
        files.write("two.jpg", byteArrayOf(2))
        records.insertChapter(chapter)
        assertTrue(records.hasCompleteFiles(chapter))
        assertEquals(listOf("file:///cache/one.jpg", "file:///cache/two.jpg"), records.toInfo(chapter).imagePaths)
        files.remove("two.jpg")
        assertFalse(records.hasCompleteFiles(chapter))
    }

    @Test fun metadataFailureDoesNotPublishAndCorruptDocumentIsPreserved() = runTest {
        val documents = Documents()
        val files = Files()
        files.write("one.jpg", byteArrayOf(1)); files.write("two.jpg", byteArrayOf(2))
        val records = DownloadRecords(documents, files)
        records.awaitReady()
        documents.failWrites = true
        assertFailsWith<PlatformIOException> { records.insertChapter(chapter) }
        assertNull(records.getChapter(chapter.uuid))
        documents.failWrites = false
        documents.data["download-records"] = "corrupt original"
        val restored = DownloadRecords(documents, files)
        assertTrue(runCatching { restored.insertChapter(chapter) }.isFailure)
        assertEquals("corrupt original", documents.data["download-records"])
    }

    @Test fun commentsRequireVerifiedImagesAndDeletionRemovesSnapshots() = runTest {
        val files = Files()
        files.write("one.jpg", byteArrayOf(1)); files.write("two.jpg", byteArrayOf(2))
        val records = DownloadRecords(Documents(), files)
        val comments = DownloadedCommentStore(records)
        assertFailsWith<IllegalStateException> { comments.save("chapter", DownloadedChapterComments(emptyList(), 0, true, savedAt = 1)) }
        records.insertChapter(chapter)
        comments.save("chapter", DownloadedChapterComments(emptyList(), 0, true, savedAt = 1))
        assertTrue(comments.read("chapter")?.complete == true)
        records.deleteChaptersByUuids(listOf("chapter"))
        assertNull(comments.read("chapter"))
        assertNull(records.readComments("chapter"))
    }

    @Test fun leaseOverlapAndRepeatedReleaseAreSafe() = runTest {
        val access = LocalDownloadAccess()
        val read = access.export(listOf("book/chapter", "book/chapter"))
        assertFailsWith<PlatformIOException> { access.mutate("book/") }
        access.mutate("other/chapter").release()
        read.release(); read.release()
        val write = access.mutate("book/")
        assertFailsWith<PlatformIOException> { access.export(listOf("book/chapter")) }
        write.release()
        access.export(listOf("book/chapter")).release()
    }

    @Test fun networkRulesFailClosedForUnvalidatedOrCellularConnections() {
        assertFalse(downloadNetworkAllowed(true, false, true))
        assertFalse(downloadNetworkAllowed(true, true, false))
        assertTrue(downloadNetworkAllowed(true, true, true))
        assertTrue(downloadNetworkAllowed(false, true, false))
    }
    @Test fun cancellationStillReleasesAnExportLease() = runTest {
        val access = LocalDownloadAccess()
        val acquired = CompletableDeferred<Unit>()
        val exporter = launch {
            val lease = access.export(listOf("book/chapter"))
            acquired.complete(Unit)
            try { awaitCancellation() } finally { lease.release() }
        }
        acquired.await()
        exporter.cancelAndJoin()
        access.mutate("book/").release()
    }

}
