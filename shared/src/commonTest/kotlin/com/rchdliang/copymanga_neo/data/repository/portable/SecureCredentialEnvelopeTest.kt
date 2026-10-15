package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.PortableSession
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class SecureCredentialEnvelopeTest {
    private val web = RequestMode.WEB
    private val app = RequestMode.OFFICIAL_APP
    private fun credential(name: String) = StoredCredential(PortableSession("synthetic-token-$name", "user:$name", name),
        name, "synthetic-password-$name", true, false)
    private class Backend : SecureCredentialEnvelopeBackend {
        var envelope: String? = null
        val legacy = mutableMapOf<RequestMode, String>()
        var legacyReads = 0
        var writes = 0
        var failRead = false
        var failBeforeWrite = false
        var failAfterWrite = false
        override suspend fun readEnvelope(): String? { check(!failRead) { "locked" }; return envelope }
        override suspend fun readLegacy(mode: RequestMode): String? { legacyReads++; return legacy[mode] }
        override suspend fun writeEnvelope(value: String) {
            writes++
            if (failBeforeWrite) throw CancellationException("simulated process stop before atomic commit")
            envelope = value
            if (failAfterWrite) throw CancellationException("simulated process stop after atomic commit")
        }
    }
    @Test fun freshInstallReadsWithoutCreatingSecureItemButExplicitEmptyWriteIsDurable() = runTest {
        val backend = Backend()
        assertTrue(EnvelopeSecureCredentialStorage(backend).readAll().isEmpty())
        assertNull(backend.envelope)
        assertEquals(0, backend.writes)
        assertEquals(RequestMode.entries.size, backend.legacyReads)
        EnvelopeSecureCredentialStorage(backend).writeAll(emptyMap())
        assertNotNull(backend.envelope)
        assertEquals(1, backend.writes)
        backend.legacy[web] = Json.encodeToString(StoredCredential.serializer(), credential("alice"))
        val previousReads = backend.legacyReads
        assertTrue(EnvelopeSecureCredentialStorage(backend).readAll().isEmpty())
        assertEquals(previousReads, backend.legacyReads)
    }
    @Test fun legacyMigrationPublishesBothModesTogetherAndDoesNotDeleteLegacy() = runTest {
        val backend = Backend()
        val values = mapOf(web to credential("alice"), app to credential("bob"))
        values.forEach { (mode, credential) -> backend.legacy[mode] = Json.encodeToString(StoredCredential.serializer(), credential) }
        assertEquals(values, EnvelopeSecureCredentialStorage(backend).readAll())
        assertEquals(1, backend.writes)
        assertEquals(2, backend.legacy.size)
        val reads = backend.legacyReads
        assertEquals(values, EnvelopeSecureCredentialStorage(backend).readAll())
        assertEquals(reads, backend.legacyReads)
    }
    @Test fun interruptedMigrationPreservesLegacyAndCanRetry() = runTest {
        val backend = Backend()
        backend.legacy[web] = Json.encodeToString(StoredCredential.serializer(), credential("alice"))
        backend.failBeforeWrite = true
        assertFailsWith<CancellationException> { EnvelopeSecureCredentialStorage(backend).readAll() }
        assertNull(backend.envelope)
        assertEquals(1, backend.legacy.size)
        backend.failBeforeWrite = false
        assertEquals(credential("alice"), EnvelopeSecureCredentialStorage(backend).readAll()[web])
    }
    @Test fun aRestartObservesOnlyWholeOldOrWholeNewSnapshots() = runTest {
        for (stopAfterCommit in listOf(false, true)) {
            val backend = Backend()
            val old = mapOf(web to credential("alice"), app to credential("alice"))
            val next = mapOf(web to credential("new-alice"), app to credential("alice").copy(session = PortableSession()))
            EnvelopeSecureCredentialStorage(backend).writeAll(old)
            backend.failBeforeWrite = !stopAfterCommit
            backend.failAfterWrite = stopAfterCommit
            assertFailsWith<CancellationException> { EnvelopeSecureCredentialStorage(backend).writeAll(next) }
            backend.failBeforeWrite = false; backend.failAfterWrite = false
            assertEquals(if (stopAfterCommit) next else old, EnvelopeSecureCredentialStorage(backend).readAll())
            assertEquals(0, backend.legacyReads)
        }
    }
    @Test fun emptyEnvelopePreventsLegacySessionResurrection() = runTest {
        val backend = Backend()
        backend.legacy[web] = Json.encodeToString(StoredCredential.serializer(), credential("alice"))
        EnvelopeSecureCredentialStorage(backend).writeAll(emptyMap())
        assertNotNull(backend.envelope)
        assertTrue(EnvelopeSecureCredentialStorage(backend).readAll().isEmpty())
        assertEquals(0, backend.legacyReads)
    }
    @Test fun lockedCorruptAndUnsupportedEnvelopeNeverFallBackToLegacy() = runTest {
        for (raw in listOf("broken synthetic-secret", "{\"version\":2,\"sessions\":{}}", "{\"version\":1}")) {
            val backend = Backend().apply { envelope = raw }
            backend.legacy[web] = Json.encodeToString(StoredCredential.serializer(), credential("alice"))
            val failure = assertFails { EnvelopeSecureCredentialStorage(backend).readAll() }
            assertFalse(failure.message.orEmpty().contains("synthetic-secret"))
            assertEquals(raw, backend.envelope)
            assertEquals(0, backend.legacyReads)
            assertEquals(0, backend.writes)
        }
        val backend = Backend().apply { failRead = true }
        assertFails { EnvelopeSecureCredentialStorage(backend).readAll() }
        assertEquals(0, backend.legacyReads)
        assertEquals(0, backend.writes)
    }
    @Test fun malformedLegacyBlocksMigrationWithoutPublishingPartialModes() = runTest {
        val backend = Backend()
        backend.legacy[web] = Json.encodeToString(StoredCredential.serializer(), credential("alice"))
        backend.legacy[app] = "broken synthetic-secret"
        val failure = assertFails { EnvelopeSecureCredentialStorage(backend).readAll() }
        assertFalse(failure.message.orEmpty().contains("synthetic-secret"))
        assertNull(backend.envelope)
        assertEquals(0, backend.writes)
        assertEquals(2, backend.legacy.size)
    }
}
