package com.rchdliang.copymanga_neo.ui.novel

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模拟重建后的路由重投递，覆盖继续阅读与从目录切卷后的入口幂等。 */
class NovelEntryGateTest {
    @Test
    fun recreatedDetailDoesNotReplaceCurrentReaderOrRequestAgain() {
        val gate = NovelEntryGate()
        var requests = 0
        var currentVolume: String? = null
        fun deliver() {
            if (gate.accept("book", null)) {
                requests++; currentVolume = null
            }
        }
        deliver()
        currentVolume = "volume-2"
        deliver()
        assertEquals(1, requests)
        assertEquals("volume-2", currentVolume)
    }

    @Test
    fun reconstructedResumeValueDoesNotReplaySavedAnchor() {
        val gate = NovelEntryGate()
        val resume = LocalReadingRecord(
            "guest",
            "novel",
            "book",
            "volume",
            "卷",
            line = 8,
            character = 2,
            updatedAt = 1
        )
        assertTrue(gate.accept(null, resume))
        assertFalse(gate.accept(null, resume.copy()))
        assertTrue(gate.accept(null, resume.copy(chapter = "other-volume")))
    }

    @Test
    fun firstBrowseAndChangedBookStillInitialize() {
        val gate = NovelEntryGate()
        assertTrue(gate.accept(null, null))
        assertFalse(gate.accept(null, null))
        assertTrue(gate.accept("a", null))
        assertFalse(gate.accept("a", null))
        assertTrue(gate.accept("b", null))
        assertTrue(gate.accept(null, null))
    }
}
