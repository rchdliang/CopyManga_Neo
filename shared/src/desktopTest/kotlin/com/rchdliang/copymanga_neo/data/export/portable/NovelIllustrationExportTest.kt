package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.data.image.portable.*
import com.rchdliang.copymanga_neo.data.local.portable.*
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.NovelIllustrationsViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NovelIllustrationExportTest {
    private fun png(color: Int) = ByteArrayOutputStream().use { out ->
        val bitmap = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
        bitmap.setRGB(0, 0, color)
        ImageIO.write(bitmap, "png", out)
        out.toByteArray()
    }
    private inner class Fixture {
        val root = Files.createTempDirectory("novel-illustration-test-")
        val cache = platformCacheStorage(root.resolve("cache").toString())
        val documents = platformDocumentStorage(root.resolve("documents").toString())
        val io = FileReaderImageIo(root.resolve("images").toString())
        val first = png(0xffff0000.toInt())
        val second = png(0xff00ff00.toInt())
        val requested = mutableListOf<String>()
        var fail = false
        val images = PortableReaderImageRepository(ReaderImageTransport { source, _ ->
            requested += source
            if (fail) error("offline")
            second
        }, io)
        val repo = PortableNovelIllustrationExportRepository(images, io,
            FileComicExportIo(root.resolve("cache/downloads").toString()), cache, documents) { 0L }
        fun volume(): NovelReadingVolume {
            val local = root.resolve("cached.png")
            Files.write(local, first)
            return NovelReadingVolume(
                NovelBook("book", "小说", "", emptyList()), NovelVolume("volume", "第一卷", 1, null, null),
                listOf(NovelSection("a", "插图 A", 2, -1, -1, "https://image.test/a.png"),
                    NovelSection("b", "插图 B", 2, -1, -1, "https://image.test/b.png")),
                NovelTextDocument.decode("text".encodeToByteArray(), "utf-8"),
                mapOf("https://image.test/a.png" to local.toString(),
                    "https://image.test/b.png" to root.resolve("missing.png").toString()), false)
        }
        fun close() {
            val target = root.toAbsolutePath().normalize()
            val temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
            check(target.startsWith(temp) && target != temp && target.fileName.toString().startsWith("novel-illustration-test-"))
            target.toFile().deleteRecursively()
        }
    }

    @Test fun archiveAndSingleImageKeepOriginalBytesAndReuseCachedImages() = runTest {
        val f = Fixture()
        try {
            val volume = f.volume()
            val draft = f.repo.prepareVolume(volume).getOrThrow()
            assertEquals(listOf("https://image.test/b.png"), f.requested)
            val destination = f.root.resolve("volume.zip")
            f.repo.saveToDocument(draft, destination.toUri().toString()).getOrThrow()
            ZipFile(destination.toFile()).use { zip ->
                val entries = zip.entries().asSequence().filter { it.name.endsWith(".png") }.toList()
                assertEquals(2, entries.size)
                assertContentEquals(f.first, zip.getInputStream(entries[0]).use { it.readBytes() })
                assertContentEquals(f.second, zip.getInputStream(entries[1]).use { it.readBytes() })
            }
            assertTrue(f.cache.list("downloads/novel-illustration-exports").isEmpty())
            assertTrue(Files.exists(f.root.resolve("cached.png")))
            val image = f.repo.prepareImage(volume, volume.sections.first()).getOrThrow()
            val single = f.root.resolve("single.png")
            f.repo.saveToDocument(image, single.toUri().toString()).getOrThrow()
            assertContentEquals(f.first, Files.readAllBytes(single))
        } finally { f.close() }
    }

    @Test fun cancellationAndPreparationFailureRemoveOnlyOwnDrafts() = runTest {
        val f = Fixture()
        try {
            val volume = f.volume()
            val draft = f.repo.prepareVolume(volume).getOrThrow()
            f.repo.discard(draft)
            assertTrue(f.cache.list("downloads/novel-illustration-exports").isEmpty())
            f.fail = true
            assertTrue(f.repo.prepareVolume(volume).isFailure)
            assertTrue(f.cache.list("downloads/novel-illustration-exports").isEmpty())
            assertTrue(Files.exists(f.root.resolve("cached.png")))
        } finally { f.close() }
    }

    @Test fun bookOverridePersistsAndTakesPriorityOverGlobalDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        val app = PortableAppSettings(f.documents)
        app.initialize().getOrThrow()
        val preferences = PortableNovelPreferences(f.documents)
        preferences.initialize().getOrThrow()
        val vm = NovelIllustrationsViewModel(preferences, app, f.repo, SavedStateHandle())
        try {
            assertFalse(vm.blurForBook("book").first())
            app.setBlurNovelIllustrationPreviews(true).getOrThrow()
            assertTrue(vm.blurForBook("book").first())
            preferences.setIllustrationBlur("book", false).getOrThrow()
            assertFalse(vm.blurForBook("book").first())
            val restored = PortableNovelPreferences(f.documents)
            assertEquals(false, restored.illustrationBlur("book").first())
            assertNull(restored.illustrationBlur("other").first())
            val restoredApp = PortableAppSettings(f.documents)
            restoredApp.initialize().getOrThrow()
            assertTrue(restoredApp.settings.first().blurNovelIllustrationPreviews)
            app.resetDefaults().getOrThrow()
            assertFalse(app.settings.first().blurNovelIllustrationPreviews)
            assertEquals(false, restored.illustrationBlur("book").first())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain(); f.close() }
    }
}
