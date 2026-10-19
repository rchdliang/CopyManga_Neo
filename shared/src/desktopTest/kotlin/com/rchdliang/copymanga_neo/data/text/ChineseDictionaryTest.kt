package com.rchdliang.copymanga_neo.data.text

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.text.TraditionalChineseConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ChineseDictionaryTest {
    private fun fixtures(folder: String, names: List<String>) = names.associateWith { name ->
        requireNotNull(javaClass.getResourceAsStream("/opencc/$folder$name")).use {
            it.readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n")
        }
    }
    private val files = fixtures("latest/", ChineseDictionaryStore.allowedFiles)
    private val legacy = fixtures("", TraditionalChineseConverter.dictionaryFiles)
    private fun release(version: String = "1.4.2", revision: String = "025f371dc76b598d77384fbdab90c937471844d8") =
        DictionaryRelease(version, revision, files.mapValues { (_, text) ->
            val bytes = text.toByteArray()
            DictionaryFile(ChineseDictionaryStore.gitBlobHash(bytes), bytes.size)
        })
    private fun bundle() = files + mapOf("@version" to "1.4.2", "@revision" to release().revision) +
        files.mapKeys { "@hash:${it.key}" }.mapValues { ChineseDictionaryStore.sha256(it.value.toByteArray()) }
    private fun store(
        read: suspend () -> Map<String, String>? = { null },
        write: suspend (Map<String, String>) -> Unit = {},
        fetch: suspend (String) -> ByteArray = { files.getValue(it.substringAfterLast('/')).toByteArray() },
        latest: suspend () -> DictionaryRelease = { release() }
    ) = ChineseDictionaryStore(read, write, fetch, latest)

    @Test fun blobHashMatchesOfficialGitObjectId() {
        assertEquals("fbe8912067fda73c3d3a491d5cc8bc3698125291",
            ChineseDictionaryStore.gitBlobHash(files.getValue("TSCharacters.txt").toByteArray()))
    }

    @Test fun latestDictionariesConvertTaiwanVariantsAndProtectPhrases() {
        val converter = TraditionalChineseConverter.fromDictionaries(files)
        assertEquals("异世界冒险与校园恋爱", converter.convert("異世界冒險與校園戀愛"))
        assertEquals("里面的头发干了，名著与著作。", converter.convert("裡面的頭髮乾了，名著與著作。"))
        assertEquals("乾隆与乾坤", converter.convert("乾隆與乾坤"))
        assertEquals("岂", converter.convert("\uF900"))
        assertEquals("ABC 123 👩‍🚀\nかな", converter.convert("ABC 123 👩‍🚀\nかな"))
    }
    @Test fun displayConversionDoesNotChangeBlacklistIdentity() {
        val theme = Theme("戀愛", "love")
        val fallback = Theme("戀愛", null)
        assertEquals("恋爱", TraditionalChineseConverter.fromDictionaries(files).convert(theme.name))
        assertEquals("戀愛", theme.name)
        assertTrue(theme.matches(Theme("恋爱", "love")))
        assertTrue(fallback.matches(Theme("戀愛", null)))
        assertFalse(fallback.matches(Theme("恋爱", null)))
    }
    @Test fun localLoadNeverConnectsToNetwork() = runBlocking {
        var calls = 0
        val dictionary = store(fetch = { calls++; error("unexpected") }, latest = { calls++; error("unexpected") })
        assertTrue(dictionary.load().isFailure)
        assertEquals(0, calls)
    }
    @Test fun firstInstallSavesLatestAndRestoresOffline() = runBlocking {
        var saved: Map<String, String>? = null
        var requests = 0
        val dictionary = store(read = { saved }, write = { saved = it },
            fetch = { requests++; files.getValue(it.substringAfterLast('/')).toByteArray() })
        assertTrue(dictionary.install().isSuccess)
        assertEquals(files.size, requests)
        assertEquals("1.4.2", dictionary.version.value)
        val restored = store(read = { saved }, fetch = { error("offline") }, latest = { error("offline") })
        assertTrue(restored.load().isSuccess)
        assertEquals("1.4.2", restored.version.value)
        assertEquals("里面", restored.converter.value!!.convert("裡面"))
    }
    @Test fun updateDistinguishesNewAndAlreadyLatestWithoutRedownloading() = runBlocking {
        var saved = bundle()
        var latest = release("1.4.3", "1".repeat(40))
        var downloads = 0
        val dictionary = store(read = { saved }, write = { saved = it }, latest = { latest },
            fetch = { downloads++; files.getValue(it.substringAfterLast('/')).toByteArray() })
        val changed = dictionary.update().getOrThrow()
        assertTrue(changed.updated)
        assertEquals("1.4.3", changed.version)
        assertEquals(files.size, downloads)
        assertFalse(dictionary.update().getOrThrow().updated)
        assertEquals(files.size, downloads)
        assertFalse(dictionary.isWorking.value)
    }
    @Test fun failedUpdatePreservesPreviousUsableVersion() = runBlocking {
        var saved = bundle()
        val original = saved
        val dictionary = store(read = { saved }, write = { saved = it },
            latest = { release("1.4.3", "2".repeat(40)) }, fetch = { "<html>error</html>".toByteArray() })
        assertTrue(dictionary.load().isSuccess)
        val converter = dictionary.converter.value
        assertTrue(dictionary.update().isFailure)
        assertEquals(original, saved)
        assertEquals("1.4.2", dictionary.version.value)
        assertSame(converter, dictionary.converter.value)
        assertFalse(dictionary.isWorking.value)
    }
    @Test fun cancellationDoesNotPersistPartialDictionary() = runBlocking {
        var writes = 0
        val dictionary = store(write = { writes++ }, fetch = { throw CancellationException("cancelled") })
        try { dictionary.install(); fail("Cancellation must propagate") }
        catch (_: CancellationException) {
            assertEquals(0, writes)
            assertNull(dictionary.converter.value)
            assertFalse(dictionary.isWorking.value)
        }
    }
    @Test fun legacyDictionaryRemainsReadableAndCanUpgrade() = runBlocking {
        var saved = legacy
        val dictionary = store(read = { saved }, write = { saved = it })
        assertTrue(dictionary.load().isSuccess)
        assertEquals("1.1.9", dictionary.version.value)
        assertTrue(dictionary.update().getOrThrow().updated)
        assertEquals("1.4.2", dictionary.version.value)
    }
    @Test fun corruptCachedDictionaryIsRejectedAndReplaced() = runBlocking {
        var saved = bundle() + ("TSCharacters.txt" to "corrupt")
        val dictionary = store(read = { saved }, write = { saved = it })
        assertTrue(dictionary.load().isFailure)
        assertTrue(dictionary.install().isSuccess)
        assertEquals("1.4.2", dictionary.version.value)
    }
    @Test fun languageDefaultsRecognizeSimplifiedAndTraditionalScripts() {
        listOf("zh-CN", "zh-Hans", "zh-Hans-SG", "zh_SG", "zh").forEach { assertTrue(ChineseLanguageDefault.enabled(it)) }
        listOf("en-US", "ja-JP", "zh-TW", "zh-HK", "zh-MO", "zh-Hant-CN").forEach { assertFalse(ChineseLanguageDefault.enabled(it)) }
    }
    @Test fun applicationVersionsCompareNumerically() {
        assertTrue(AppVersion.isNewer("v0.10.4", "0.10.3.9"))
        assertTrue(AppVersion.isNewer("v0.10.10", "0.10.9"))
        assertFalse(AppVersion.isNewer("v0.10.3", "0.10.3.0"))
        assertFalse(AppVersion.isNewer("v0.10.2", "0.10.3"))
        assertTrue(AppVersion.isNewer("v0.10.3", "0.10.3-dev"))
    }
}
