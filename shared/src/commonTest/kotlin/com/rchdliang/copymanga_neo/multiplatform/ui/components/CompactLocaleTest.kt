package com.rchdliang.copymanga_neo.multiplatform.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactLocaleTest {
    @Test fun chineseUnitsAndSignificantDigits() {
        assertEquals("0", 0.toCompactLocale())
        assertEquals("9999", 9999.toCompactLocale())
        assertEquals("1万", 10000.toCompactLocale())
        assertEquals("1.2万", 12345.toCompactLocale())
        assertEquals("12万", 123456.toCompactLocale())
        assertEquals("1亿", 99999999.toCompactLocale())
        assertEquals("1.2亿", 123456789.toCompactLocale())
        assertEquals("-1.2万", (-12345).toCompactLocale())
        assertEquals("-21亿", Int.MIN_VALUE.toCompactLocale())
    }
}
