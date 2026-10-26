package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.NetworkDiagnostic
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortableNetworkLogStartupTest {
    private class Disk : DocumentStorage {
        val rows = mutableMapOf<String, String>()
        var reads = 0
        var historyReads = 0
        var appends = 0
        var writes = 0
        override suspend fun read(key: String): String? {
            reads++
            if (key.endsWith(".jsonl") || key == "network-diagnostics.json") historyReads++
            return rows[key]
        }
        override suspend fun write(key: String, value: String) { writes++; rows[key] = value }
        override suspend fun append(key: String, value: String) { appends++; rows[key] = rows[key].orEmpty() + value }
        override suspend fun size(key: String) = rows[key]?.encodeToByteArray()?.size?.toLong() ?: 0L
        override suspend fun readPrefix(key: String, maxChars: Int) = rows[key]?.take(maxChars)
        override suspend fun remove(key: String) { rows.remove(key) }
    }

    private fun event(phase: String = "response", endpoint: String = "/api/v3/homeIndex") =
        NetworkDiagnostic(phase, "GET", "example.test", endpoint, RequestMode.WEB, 0)

    private suspend fun exported(logs: PortableNetworkLog): List<JsonObject> {
        val text = StringBuilder()
        logs.exportChunks { text.append(it.decodeToString()) }.getOrThrow()
        return text.lines().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject }
    }

    @Test fun earlyRequestsQueueAndLaterWritesOnlyAppendWithoutReadingHistory() = runTest {
        val disk = Disk()
        PortableNetworkLog(disk, backgroundScope).setEnabled(true)
        disk.reads = 0
        val logs = PortableNetworkLog(disk, backgroundScope)
        logs.record(event())
        runCurrent()
        assertEquals(0, disk.reads)
        logs.initialize().getOrThrow()
        val readsAfterInit = disk.reads
        val writesAfterInit = disk.writes
        runCurrent()
        assertTrue(logs.state.value.enabled)
        assertEquals(1, disk.appends)
        assertEquals(0, disk.historyReads)
        logs.record(event("second", "/目录/😀")); runCurrent()
        logs.initialize().getOrThrow()
        assertEquals(readsAfterInit, disk.reads)
        assertEquals(writesAfterInit, disk.writes)
        assertEquals(2, disk.appends)
        assertTrue(disk.rows.getValue("network-log-0.jsonl").all { it.code < 128 })
        val entries = exported(logs)
        assertEquals(listOf("response", "second"), entries.map { it.getValue("phase").jsonPrimitive.content })
        assertEquals("/目录/😀", entries.last().getValue("endpoint").jsonPrimitive.content)
    }

    @Test fun oldLogIsDiscardedWithoutParsingItsBodyAndSwitchIsRetained() = runTest {
        val disk = Disk()
        disk.rows["network-diagnostics.json"] = "{\"enabled\":true,\"lines\":[" + "broken".repeat(100_000)
        disk.rows["reading-library.json"] = "keep personal data"
        val logs = PortableNetworkLog(disk, backgroundScope)
        logs.initialize().getOrThrow()
        assertTrue(logs.state.value.enabled)
        assertEquals(0, disk.historyReads)
        assertFalse(disk.rows.containsKey("network-diagnostics.json"))
        assertEquals("keep personal data", disk.rows["reading-library.json"])
        assertEquals(0L, logs.state.value.bytes)
    }

    @Test fun restartStatsSegmentsWithoutReadingThemAndExportSkipsInterruptedTail() = runTest {
        val disk = Disk()
        PortableNetworkLog(disk, backgroundScope).setEnabled(true)
        disk.rows["network-log-0.jsonl"] = "{\"phase\":\"previous\"}\n{\"phase\":\"interrupted"
        val logs = PortableNetworkLog(disk, backgroundScope)
        logs.initialize().getOrThrow()
        logs.record(event("next")); runCurrent()
        assertEquals(0, disk.historyReads)
        assertEquals(listOf("previous", "next"), exported(logs).map { it.getValue("phase").jsonPrimitive.content })
    }

    @Test fun rotationBoundsStorageKeepsOrderAndClearPreservesEnabledSwitch() = runTest {
        val disk = Disk()
        val logs = PortableNetworkLog(disk, backgroundScope)
        logs.setEnabled(true)
        repeat(18) { logs.record(event("event-$it", "x".repeat(200_000))) }
        runCurrent()
        assertTrue(logs.state.value.bytes <= 4L * 1024 * 1024)
        assertEquals((2..17).map { "event-$it" }, exported(logs).map { it.getValue("phase").jsonPrimitive.content })
        logs.clear().getOrThrow()
        assertTrue(logs.state.value.enabled)
        assertEquals(0L, logs.state.value.bytes)
        assertTrue(exported(logs).isEmpty())
    }

    @Test fun disabledLogDoesNotPersistQueuedStartupRequests() = runTest {
        val disk = Disk()
        PortableNetworkLog(disk, backgroundScope).setEnabled(false)
        val before = disk.rows.toMap()
        val logs = PortableNetworkLog(disk, backgroundScope)
        logs.record(event())
        logs.initialize().getOrThrow()
        runCurrent()
        assertEquals(before, disk.rows)
        assertEquals(0, disk.appends)
        assertFalse(logs.state.value.enabled)
    }
}
