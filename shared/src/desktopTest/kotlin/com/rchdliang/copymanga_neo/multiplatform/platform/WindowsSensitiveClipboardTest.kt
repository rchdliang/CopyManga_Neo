package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.*

class WindowsSensitiveClipboardTest {
    private class Fake(
        val failAt: String? = null,
        override val owner: Long = 77,
        val extraFailures: Set<String> = emptySet()
    ) : WindowsClipboardApi {
        val events = mutableListOf<String>()
        val allocated = linkedMapOf<Long, ByteArray>()
        val sourceArrays = mutableListOf<ByteArray>()
        val transferred = mutableSetOf<Long>()
        val freed = mutableListOf<Long>()
        var publishedText = false
        private var registers = 0
        private var empties = 0
        private fun success(event: String): Boolean { events += event; return event != failAt && event !in extraFailures }
        override fun registerFormat(name: String): Int {
            val index = registers++
            assertEquals(WindowsSensitiveClipboard.SUPPRESSION_FORMATS[index], name)
            return if (success("register:$index")) 100 + index else 0
        }
        override fun allocate(bytes: ByteArray): Long {
            val handle = allocated.size.toLong() + 1
            check(success("allocate:$handle")) { "allocation failed" }
            sourceArrays += bytes
            allocated[handle] = bytes.copyOf()
            return handle
        }
        override fun open() = success("open")
        override fun empty() = success("empty:${empties++}")
        override fun publish(format: Int, handle: Long): Boolean {
            if (!success("publish:$format")) return false
            assertTrue(transferred.add(handle))
            if (format == WindowsSensitiveClipboard.CF_UNICODETEXT) publishedText = true
            return true
        }
        override fun close() = success("close")
        override fun wipeAndFree(handle: Long) {
            check(handle !in transferred) { "freed system-owned memory" }
            check(handle !in freed) { "double free" }
            success("free:$handle")
            allocated.getValue(handle).fill(0)
            freed += handle
        }
        fun assertOwnershipComplete() {
            assertEquals(allocated.keys, transferred + freed)
            assertTrue(freed.none { it in transferred })
        }
    }

    @Test fun suppressionPrecedesTextAndAllAllocationPrecedesEmpty() {
        val api = Fake()
        WindowsSensitiveClipboard(api).copy("中文😀")
        assertEquals(listOf("register:0", "register:1", "register:2", "allocate:1", "allocate:2", "allocate:3", "allocate:4",
            "open", "empty:0", "publish:100", "publish:101", "publish:102", "publish:13", "close"), api.events)
        assertContentEquals(byteArrayOf(1), api.allocated[1])
        assertContentEquals(ByteArray(4), api.allocated[2])
        assertContentEquals(ByteArray(4), api.allocated[3])
        assertContentEquals("中文😀".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0), api.allocated[4])
        assertTrue(api.sourceArrays.last().all { it == 0.toByte() })
        assertTrue(api.freed.isEmpty())
        api.assertOwnershipComplete()
    }

    @Test fun everyMarkerFailurePreventsPlaintextAndReleasesOnlyUntransferredMemory() {
        for (format in 100..102) {
            val api = Fake("publish:$format")
            assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic secret") }
            assertFalse(api.publishedText)
            assertFalse("publish:13" in api.events)
            assertTrue("empty:1" in api.events)
            assertEquals(1, api.events.count { it == "close" })
            assertTrue(api.sourceArrays.last().all { it == 0.toByte() })
            api.assertOwnershipComplete()
        }
    }

    @Test fun registrationFailuresLeaveOldClipboardUntouched() {
        for (index in 0..2) {
            val api = Fake("register:$index")
            assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
            assertFalse("open" in api.events)
            assertTrue(api.allocated.isEmpty())
        }
    }

    @Test fun allocationFailuresLeaveClipboardUntouchedAndFreeEarlierAllocations() {
        for (handle in 1..4) {
            val api = Fake("allocate:$handle")
            assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
            assertFalse("open" in api.events)
            assertFalse(api.publishedText)
            api.assertOwnershipComplete()
        }
    }

    @Test fun busyClipboardIsNotEmptiedAndIsNotClosedByNonOwner() {
        val api = Fake("open")
        assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
        assertFalse(api.events.any { it.startsWith("empty:") || it == "close" || it.startsWith("publish:") })
        api.assertOwnershipComplete()
    }

    @Test fun failedEmptyClosesClipboardAndPublishesNothing() {
        val api = Fake("empty:0")
        assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
        assertFalse(api.events.any { it.startsWith("publish:") })
        assertEquals(1, api.events.count { it == "close" })
        api.assertOwnershipComplete()
    }

    @Test fun textFailureClearsMarkersAndFreesUntransferredText() {
        val api = Fake("publish:13")
        assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
        assertFalse(api.publishedText)
        assertEquals(listOf(4L), api.freed)
        assertTrue("empty:1" in api.events)
        assertEquals(1, api.events.count { it == "close" })
        api.assertOwnershipComplete()
    }

    @Test fun closeFailureDoesNotFreeAlreadyTransferredDataOrRetryAsPlainText() {
        val api = Fake("close")
        assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
        assertTrue(api.publishedText)
        assertEquals(1, api.events.count { it == "publish:13" })
        assertTrue(api.freed.isEmpty())
        assertTrue(api.sourceArrays.last().all { it == 0.toByte() })
        api.assertOwnershipComplete()
    }

    @Test fun rollbackFailureStillClosesAndReleasesLocalMemoryWithoutPublishingText() {
        val api = Fake("publish:101", extraFailures = setOf("empty:1"))
        val failure = assertFailsWith<IllegalStateException> { WindowsSensitiveClipboard(api).copy("synthetic") }
        assertTrue(failure.suppressed.isNotEmpty())
        assertFalse(api.publishedText)
        assertEquals(1, api.events.count { it == "close" })
        api.assertOwnershipComplete()
    }

    @Test fun nullOwnerAndMalformedTextAreRejectedBeforeAnyNativeWrite() {
        val owner = Fake(owner = 0)
        assertFailsWith<IllegalArgumentException> { WindowsSensitiveClipboard(owner).copy("synthetic") }
        assertTrue(owner.events.isEmpty())
        for (text in listOf("a\u0000b", "\uD800", "\uDC00", "x".repeat(512 * 1024 + 1))) {
            val api = Fake()
            assertFailsWith<IllegalArgumentException> { WindowsSensitiveClipboard(api).copy(text) }
            assertTrue(api.events.isEmpty())
        }
    }
}
