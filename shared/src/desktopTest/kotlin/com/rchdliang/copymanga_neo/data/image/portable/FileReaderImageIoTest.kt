package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileReaderImageIoTest {
    private fun png() = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB), "png", output)
        output.toByteArray()
    }
    @Test fun metadataOnlyProbeAndOriginalBytesRoundTripToBothDestinations() = runTest {
        val root = Files.createTempDirectory("reader-image-test").toFile()
        try {
            val io = FileReaderImageIo(File(root, "cache").path, picturesDirectory = File(root, "pictures").path)
            val bytes = png()
            val repo = PortableReaderImageRepository(ReaderImageTransport { _, _ -> bytes }, io)
            val prepared = repo.prepare("https://cdn/image.png", "漫画").getOrThrow()
            assertContentEquals(bytes, File(prepared.filePath).readBytes())
            val metadata = io.inspect(bytes)
            assertEquals(2, metadata.width)
            assertEquals(3, metadata.height)
            val destination = File(root, "chosen.png").apply { writeText("old content") }
            repo.saveToDocument(prepared, destination.toURI().toString()).getOrThrow()
            assertContentEquals(bytes, destination.readBytes())
            repo.saveToGallery(prepared).getOrThrow()
            assertContentEquals(bytes, File(root, "pictures/CopyManga Neo/${prepared.displayName}").readBytes())
            assertTrue(File(prepared.filePath).isFile) // share recipient lease survives saving
        } finally { root.deleteRecursively() }
    }
    @Test fun restoredPreparedReferenceCannotReadOutsideExportDirectory() = runTest {
        val root = Files.createTempDirectory("reader-image-test").toFile()
        try {
            val source = File(root, "private.png").apply { writeBytes(png()) }
            val store = FileReaderImageStore(File(root, "cache").path)
            assertFailsWith<IllegalArgumentException> { store.readPrepared(ReaderExportImage(source.path, "image/png", source.name), 1024) }
        } finally { root.deleteRecursively() }
    }
    @Test fun pruneRetainsRecentFilesAndNeverTouchesSiblingCache() = runTest {
        val root = Files.createTempDirectory("reader-image-test").toFile()
        try {
            val store = FileReaderImageStore(root.path)
            val old = File(store.publish("old.png", png())).apply { setLastModified(10) }
            val recent = File(store.publish("recent.png", png())).apply { setLastModified(100) }
            val sibling = File(root, "other-cache.png").apply { writeBytes(png()); setLastModified(10) }
            store.prune(50)
            assertFalse(old.exists())
            assertTrue(recent.exists())
            assertTrue(sibling.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun boundedLocalReadRejectsBeforeReturningAnyPayload() = runTest {
        val root = Files.createTempDirectory("reader-image-test").toFile()
        try {
            val source = File(root, "large.bin").apply { writeBytes(ByteArray(33)) }
            assertFailsWith<IllegalArgumentException> { FileReaderImageStore(root.path).readLocal(source.toURI().toString(), 32) }
        } finally { root.deleteRecursively() }
    }
}
