package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import kotlin.test.*

class DownloadSettingsTest {
    @Test fun decimalLimitsRoundExactlyAndRejectInvalidValues() {
        assertEquals(0, parseDownloadLimitKiB(" "))
        assertEquals(1536, parseDownloadLimitKiB("1,5"))
        assertEquals(1, parseDownloadLimitKiB("0.001"))
        assertNull(parseDownloadLimitKiB("0.0001"))
        assertEquals(Int.MAX_VALUE, parseDownloadLimitKiB("2097151.999"))
        for (invalid in listOf("0", "-1", ".", "1e3", "NaN", "2097151.9990234376", "2097152")) {
            assertNull(parseDownloadLimitKiB(invalid), invalid)
        }
        assertEquals("0.1", formatDownloadLimitMiB(102))
        assertEquals("0.001", formatDownloadLimitMiB(1))
        assertEquals("1.5", formatDownloadLimitMiB(1536))
        for (limit in listOf(1, 3, 21, 1023, 1024, 1025, 1536, Int.MAX_VALUE)) {
            val formatted = formatDownloadLimitMiB(limit)
            assertTrue(formatted.substringAfter('.', "").length <= 3)
            assertEquals(limit, parseDownloadLimitKiB(formatted, limit))
        }
    }
}
