package com.rchdliang.copymanga_neo.data.logging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ErrorLogTest {
    private class MemoryFiles : ErrorLogFiles {
        val content = mutableMapOf<String, String>()
        var failWrites = false
        override fun <T> locked(block: () -> T): T = block()
        override fun read(name: String) = content[name]
        override fun write(name: String, text: String, durable: Boolean) {
            check(!failWrites) { "disk full" }
            content[name] = text
        }
        override fun remove(name: String) { content.remove(name) }
    }

    @Test fun restartKeepsErrorsAndCrashStackAndClearDoesNotDisableRecording() = runTest {
        val files = MemoryFiles()
        AppErrorLog.install(ErrorLogStore(files), "test")
        AppErrorLog.record("W", "Reader", "图片加载失败", IllegalStateException("bad image"))
        AppErrorLog.record("E", "Crash", "退出", IllegalArgumentException("crash marker"), crash = true)
        AppErrorLog.install(ErrorLogStore(files), "test restarted")
        val result = AppErrorLog.export().getOrThrow().decodeToString()
        assertContains(result, "bad image")
        assertContains(result, "IllegalArgumentException")
        assertContains(result, "crash marker")
        assertTrue(AppErrorLog.state.value.bytes > 0)
        assertEquals(2, result.lineSequence().filter(String::isNotBlank).count())
        result.lineSequence().filter(String::isNotBlank).forEach { Json.parseToJsonElement(it).jsonObject }
        assertTrue(AppErrorLog.clear().isSuccess)
        assertEquals(0, AppErrorLog.state.value.bytes)
        AppErrorLog.record("E", "New", "new error")
        assertContains(AppErrorLog.export().getOrThrow().decodeToString(), "new error")
    }

    @Test fun rotationIsBoundedAndOrdinaryErrorsCannotEvictCrashes() {
        val files = MemoryFiles()
        val store = ErrorLogStore(files)
        store.append("""{"kind":"crash","message":"keep crash"}""", true)
        repeat(16) { store.append("""{"message":"$it-${"x".repeat(70000)}"}""", false) }
        val restarted = ErrorLogStore(files)
        assertContains(restarted.snapshot(), "keep crash")
        assertFalse(restarted.snapshot().contains("0-xxx"))
        assertContains(restarted.snapshot(), "15-xxx")
        assertTrue(files.content.size <= 8)
        assertTrue(files.content.values.all { it.encodeToByteArray().size <= ErrorLogStore.SEGMENT_BYTES })
    }

    @Test fun failedWriteDoesNotLosePreviouslySavedRecordsOrThrowFromLogger() = runTest {
        val files = MemoryFiles()
        AppErrorLog.install(ErrorLogStore(files), "test")
        AppErrorLog.record("E", "Before", "saved")
        files.failWrites = true
        AppErrorLog.record("E", "Crash", "unsaved", Error("original failure"), crash = true)
        assertNotNull(AppErrorLog.state.value.error)
        assertContains(AppErrorLog.export().getOrThrow().decodeToString(), "saved")
    }

    @Test fun credentialsAreRedactedAndHugeExceptionsRemainValidJson() = runTest {
        val files = MemoryFiles()
        AppErrorLog.install(ErrorLogStore(files), "test")
        AppErrorLog.record("E", "Network",
            "GET https://example.com/path?token=secret123&password=pass123 Authorization: Bearer abc123 token=abc456",
            IllegalStateException("password=\"hidden\" " + "汉字\\\n".repeat(20000)))
        val data = AppErrorLog.export().getOrThrow().decodeToString()
        listOf("secret123", "pass123", "abc123", "abc456", "hidden").forEach { assertFalse(data.contains(it)) }
        assertContains(data, "[redacted]")
        assertTrue(data.encodeToByteArray().size <= ErrorLogStore.SEGMENT_BYTES)
        assertEquals("error", Json.parseToJsonElement(data.trim()).jsonObject["kind"]?.jsonPrimitive?.content)
    }

    @Test fun debugAndCancellationAreNotRecorded() {
        val files = MemoryFiles()
        AppErrorLog.install(ErrorLogStore(files), "test")
        AppErrorLog.record("D", "debug", "verbose")
        AppErrorLog.record("E", "cancel", "cancelled", CancellationException())
        assertTrue(files.content.isEmpty())
    }
}
