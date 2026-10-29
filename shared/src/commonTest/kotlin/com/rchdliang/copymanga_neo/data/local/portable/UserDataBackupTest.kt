package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlin.test.*

class UserDataBackupTest {
    private class Disk : DocumentStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    @Test
    fun personalDataRoundTripIncludesAnnotationsAndKeepsCredentialsAndDownloads() = runTest {
        val source = Disk()
        val annotations = PortableNovelAnnotations(source)
        annotations.initialize()
        val note = NovelAnnotation(
            "note", "owner", RequestMode.WEB.name, "book", "volume", "Volume",
            "selected text", NovelTextRange(0, 0, 0, 3), note = "my annotation", createdAt = 10
        )
        assertTrue(annotations.save(note).isSuccess)
        PortableNovelPreferences(source).apply {
            initialize(); update(NovelReaderPreferences(fontSize = 24f, systemFont = "desktop:JetBrains Mono", fontWeight = 553))
            setIllustrationBlur("book", true)
        }
        source.values["network-settings.json"] = "must not be exported"
        source.values["download-records"] = "must not be exported"
        val archive = PortableUserDataBackup(source).export()
        assertFalse(archive.contains("must not be exported"))
        val target = Disk().apply {
            values["network-settings.json"] = "keep identity"
            values["download-records"] = "keep downloads"
        }
        val restore = PortableUserDataBackup(target)
        restore.prepare(archive)
        assertFalse(target.values.containsKey("novel-annotations.json"))
        restore.recover()
        val loaded = PortableNovelAnnotations(target)
        assertTrue(loaded.initialize().isSuccess)
        assertEquals(note, loaded.annotations.first().single())
        val prefs = PortableNovelPreferences(target)
        assertTrue(prefs.initialize().isSuccess)
        assertEquals(24f, prefs.settings.first().fontSize)
        assertEquals("desktop:JetBrains Mono", prefs.settings.first().systemFont)
        assertEquals(553, prefs.settings.first().fontWeight)
        assertEquals("keep identity", target.values["network-settings.json"])
        assertEquals("keep downloads", target.values["download-records"])
    }

    @Test
    fun shelfCatchUpEvidenceRoundTripsAndLegacyRestoreRemovesExistingEvidence() = runTest {
        val source = Disk()
        val key = ShelfReadingKey("owner", RequestMode.WEB, "novel", "book")
        val record = ShelfReadingRecord(key).read("one", "第一卷", true, 1)
            .observeCatalog(listOf(ShelfChapter("one", "第一卷")))
        val document = DocumentStore(source, "shelf-reading.json", ShelfReadingSnapshot.serializer(), ShelfReadingSnapshot())
        document.update { ShelfReadingSnapshot(mapOf(key.id to record)) }.getOrThrow()
        val archive = PortableUserDataBackup(source).export()
        val target = Disk()
        PortableUserDataBackup(target).apply { prepare(archive); recover() }
        val loaded = DocumentStore(target, "shelf-reading.json", ShelfReadingSnapshot.serializer(), ShelfReadingSnapshot())
        loaded.load().getOrThrow()
        assertEquals(record, loaded.state.value.records[key.id])
        val root = Json.parseToJsonElement(archive).jsonObject
        val legacy = JsonObject(root + ("entries" to JsonObject(root.getValue("entries").jsonObject - "shelf-reading.json")))
        PortableUserDataBackup(target).apply { prepare(legacy.toString()); recover() }
        assertFalse(target.values.containsKey("shelf-reading.json"))
    }

    @Test
    fun unknownArchiveVersionAndUnlistedPathsCannotBeImported() = runTest {
        val storage = Disk()
        val backup = PortableUserDataBackup(storage)
        val root = Json.parseToJsonElement(backup.export()).jsonObject
        val future = JsonObject(root + ("version" to JsonPrimitive(2))).toString()
        assertTrue(runCatching { backup.prepare(future) }.isFailure)
        assertFalse(storage.values.containsKey(PortableUserDataBackup.JOURNAL))
        val injected =
            root.getValue("entries").jsonObject + ("../credentials" to JsonPrimitive("secret"))
        assertTrue(runCatching { backup.prepare(JsonObject(root + ("entries" to JsonObject(injected))).toString()) }.isFailure)
        assertFalse(storage.values.containsKey(PortableUserDataBackup.JOURNAL))
    }
}
