package com.rchdliang.copymanga_neo.data.novel.cache

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NovelCacheStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun indexCanBeRebuiltOfflineAndKeepsAccountAndModeIdentity() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        listOf(
            RequestMode.WEB to "guest",
            RequestMode.OFFICIAL_APP to "user:a"
        ).forEach { (mode, owner) ->
            store.publish(mode, owner, "book", "v1", response(), "正文\n".toByteArray(), null) {}
        }
        val reopened = NovelCacheStore(root).scan()
        assertEquals(2, reopened.size)
        assertEquals(setOf("guest", "user:a"), reopened.map { it.owner }.toSet())
        assertEquals(
            setOf(RequestMode.WEB, RequestMode.OFFICIAL_APP),
            reopened.map { it.mode }.toSet()
        )
        assertTrue(reopened.all { it.offlineReady && it.bytes > 0 && it.book.name == "测试小说" })
        assertTrue(reopened.all { it.downloadRequested == false })
    }

    @Test
    fun readingCacheStaysSeparateEvenWhenAllImagesAreCached() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        var volume = store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(image = "https://images.example/picture"), "正文\n".toByteArray(), null
        ) {}
        volume = store.writeImage(volume, "https://images.example/picture", byteArrayOf(1, 2, 3))
        store.touch(volume)
        val entry = NovelCacheStore(root).scan().single()
        assertTrue(entry.offlineReady)
        assertEquals(false, entry.downloadRequested)
        assertFalse(entry.visibleInDownloads)
        assertTrue(entry.bytes > 0)
    }

    @Test
    fun explicitDownloadPromotesExistingCacheAndSurvivesReadingRefresh() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        val reading = store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(), "正文\n".toByteArray(), null
        ) {}
        val text = File(reading.directory, reading.manifest["text_file"].asString)
        val before = text.readBytes()
        val downloaded = store.markDownloadRequested(reading)
        assertTrue(before.contentEquals(text.readBytes()))
        assertEquals(true, NovelCacheStore(root).scan().single().downloadRequested)
        assertTrue(NovelCacheStore(root).scan().single().visibleInDownloads)
        store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(), "刷新正文\n".toByteArray(), downloaded
        ) {}
        assertEquals(true, NovelCacheStore(root).scan().single().downloadRequested)
    }

    @Test
    fun explicitPartialDownloadIsRecordedBeforeImagesFinish() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(image = "https://images.example/picture"), "正文\n".toByteArray(), null,
            downloadRequested = true
        ) {}
        val entry = NovelCacheStore(root).scan().single()
        assertEquals(true, entry.downloadRequested)
        assertFalse(entry.offlineReady)
        assertEquals(1, entry.totalImages)
    }

    @Test
    fun missingDownloadMarkerKeepsContentReadableWithoutListingAsDownload() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        val volume = store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(), "正文\n".toByteArray(), null
        ) {}
        val legacyManifest = volume.manifest.deepCopy().apply { remove("download_requested") }
        val manifestFile = File(volume.directory, "manifest.json")
        manifestFile.writeText(legacyManifest.toString())
        val before = manifestFile.readText()
        val reopened = NovelCacheStore(root)
        assertFalse(reopened.scan().single().downloadRequested)
        assertFalse(reopened.scan().single().visibleInDownloads)
        assertEquals(before, manifestFile.readText())
        val legacy = reopened.read(RequestMode.WEB, "guest", "book", "v1")!!
        assertEquals("正文", legacy.reading.document.lines.first())
        val refreshed = reopened.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(), "刷新正文\n".toByteArray(), legacy
        ) {}
        assertFalse(reopened.describe(refreshed).downloadRequested)
        reopened.markDownloadRequested(refreshed)
        assertEquals(true, NovelCacheStore(root).scan().single().downloadRequested)
    }

    @Test
    fun explicitIntentDoesNotLeakAcrossAccountsOrModes() {
        val store = NovelCacheStore(temporary.newFolder())
        store.publish(
            RequestMode.WEB, "guest", "book", "v1",
            response(), "正文\n".toByteArray(), null, downloadRequested = true
        ) {}
        store.publish(
            RequestMode.WEB, "user:a", "book", "v1",
            response(), "正文\n".toByteArray(), null
        ) {}
        store.publish(
            RequestMode.OFFICIAL_APP, "guest", "book", "v1",
            response(), "正文\n".toByteArray(), null
        ) {}
        val entries = store.scan()
        assertEquals(1, entries.count { it.downloadRequested == true })
        assertEquals(2, entries.count { it.downloadRequested == false })
    }

    @Test
    fun refreshReclaimsPreviousTextAndImagesWithoutEvictingAnotherVolume() {
        val store = NovelCacheStore(temporary.newFolder())
        var first = store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(image = "https://images.example/old"),
            "第一版\n".toByteArray(),
            null
        ) {}
        first = store.writeImage(first, "https://images.example/old", byteArrayOf(1, 2, 3))
        val oldText = File(first.directory, first.manifest["text_file"].asString)
        val oldImage = File(first.reading.cachedImages.values.single())
        store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v2",
            response(id = "v2"),
            "另一卷\n".toByteArray(),
            null
        ) {}
        val replaced = store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(image = "https://images.example/new"),
            "第二版\n".toByteArray(),
            first
        ) {}
        assertFalse(oldText.exists())
        assertFalse(oldImage.exists())
        assertFalse(replaced.reading.offlineReady)
        assertEquals(2, store.scan().size)
        assertEquals(
            "第二版",
            store.read(RequestMode.WEB, "guest", "book", "v1")!!.reading.document.lines.first()
        )
    }

    @Test
    fun corruptImageIsRemovedAndVolumeRemainsReadableAsPartialDownload() {
        val store = NovelCacheStore(temporary.newFolder())
        var volume = store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(image = "https://images.example/picture"),
            "正文\n".toByteArray(),
            null
        ) {}
        volume = store.writeImage(volume, "https://images.example/picture", byteArrayOf(1, 2, 3))
        File(volume.reading.cachedImages.values.single()).writeBytes(byteArrayOf(4, 5, 6))
        val entries = store.scan()
        assertEquals(1, entries.size)
        assertEquals(0, entries.single().cachedImages)
        assertEquals(1, entries.single().totalImages)
        assertFalse(entries.single().offlineReady)
        assertEquals(
            "正文",
            store.read(RequestMode.WEB, "guest", "book", "v1")!!.reading.document.lines.first()
        )
    }

    @Test
    fun metadataUsesLastAccessEvictionAndCannotReappearAfterClear() {
        var clock = 1_000L
        val root = temporary.newFolder()
        val store = NovelCacheStore(
            root,
            metadataBytesLimit = 10,
            metadataCountLimit = 2,
            metadataAgeMillis = 100,
            now = { clock })
        store.writeMetadata("first", byteArrayOf(1, 2), store.generation)
        clock++
        store.writeMetadata("second", byteArrayOf(3, 4), store.generation)
        clock++
        assertNotNull(store.readMetadata("first"))
        clock++
        store.writeMetadata("third", byteArrayOf(5, 6), store.generation)
        assertNull(store.readMetadata("second"))
        assertNotNull(store.readMetadata("first"))
        val pendingGeneration = store.generation
        store.clear()
        store.writeMetadata("late-response", byteArrayOf(7), pendingGeneration)
        assertEquals(0L, store.usageBytes())
        assertTrue(store.scan().isEmpty())
    }

    @Test
    fun metadataByteAndAgeLimitsDoNotRemoveDownloadedText() {
        var clock = 1_000L
        val store = NovelCacheStore(
            temporary.newFolder(),
            metadataBytesLimit = 3,
            metadataCountLimit = 10,
            metadataAgeMillis = 10,
            now = { clock })
        store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(),
            "正文\n".toByteArray(),
            null
        ) {}
        store.writeMetadata("first", byteArrayOf(1, 2), store.generation)
        clock++
        store.writeMetadata("second", byteArrayOf(3, 4), store.generation)
        assertNull(store.readMetadata("first"))
        assertNotNull(store.readMetadata("second"))
        clock += 11
        store.pruneMetadata()
        assertNull(store.readMetadata("second"))
        assertEquals(1, store.scan().size)
    }

    @Test
    fun removingVolumePreservesOtherOwnerAndOrphanCleanupIsBoundedToCache() {
        val root = temporary.newFolder()
        val store = NovelCacheStore(root)
        store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(),
            "正文\n".toByteArray(),
            null
        ) {}
        store.publish(
            RequestMode.WEB,
            "user:a",
            "book",
            "v1",
            response(),
            "正文\n".toByteArray(),
            null
        ) {}
        File(root, "volumes/orphan").apply { mkdirs(); resolve("partial").writeText("incomplete") }
        store.removeVolume(RequestMode.WEB, "guest", "book", "v1")
        assertEquals("user:a", store.scan().single().owner)
        assertFalse(File(root, "volumes/orphan").exists())
    }

    @Test
    fun removingBookAlsoDeletesItsCorruptVolumeButPreservesAnotherOwner() {
        val store = NovelCacheStore(temporary.newFolder())
        val first = store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(),
            "正文\n".toByteArray(),
            null
        ) {}
        store.publish(
            RequestMode.WEB,
            "user:a",
            "book",
            "v1",
            response(),
            "正文\n".toByteArray(),
            null
        ) {}
        File(first.directory, first.manifest["text_file"].asString).writeText("broken")
        store.removeBook(RequestMode.WEB, "guest", "book")
        assertFalse(first.directory.exists())
        assertEquals("user:a", store.scan().single().owner)
    }

    @Test
    fun readOnlySnapshotDoesNotRewriteManifestOrRemoveUnpublishedFiles() {
        val store = NovelCacheStore(temporary.newFolder())
        var volume = store.publish(
            RequestMode.WEB,
            "guest",
            "book",
            "v1",
            response(image = "https://images.example/picture"),
            "正文\n".toByteArray(),
            null
        ) {}
        volume = store.writeImage(volume, "https://images.example/picture", byteArrayOf(1, 2, 3))
        val image = File(volume.reading.cachedImages.values.single())
        image.writeBytes(byteArrayOf(4, 5, 6))
        val inFlight = File(volume.directory, "next-image.partial").apply { writeText("in-flight") }
        val manifest = File(volume.directory, "manifest.json")
        val before = manifest.readText()
        val snapshot = store.read(RequestMode.WEB, "guest", "book", "v1", repair = false)!!
        assertEquals("正文", snapshot.reading.document.lines.first())
        assertFalse(snapshot.reading.offlineReady)
        assertEquals(before, manifest.readText())
        assertTrue(image.exists())
        assertTrue(inFlight.exists())
    }

    private fun response(id: String = "v1", image: String? = null): JsonObject =
        JsonObject().apply {
            add("book", JsonObject().apply {
                addProperty("path_word", "book"); addProperty("name", "测试小说")
                addProperty("cover", "https://images.example/cover")
                add(
                    "author",
                    JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty(
                                "name",
                                "作者"
                            ); addProperty("path_word", "author")
                        })
                    })
            })
            add("volume", JsonObject().apply {
                addProperty("id", id); addProperty("name", "第一卷"); addProperty("index", 1)
                addProperty("txt_encoding", "UTF-8")
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("content_type", 1); addProperty(
                        "name",
                        "正文"
                    ); addProperty("start_lines", 0); addProperty("end_lines", 1)
                    })
                    if (image != null) add(JsonObject().apply {
                        addProperty(
                            "content_type",
                            2
                        ); addProperty("name", "插图"); addProperty(
                        "start_lines",
                        0
                    ); addProperty("end_lines", 0); addProperty("content", image)
                    })
                })
            })
        }
}
