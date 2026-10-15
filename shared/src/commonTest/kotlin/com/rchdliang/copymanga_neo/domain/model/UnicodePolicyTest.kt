package com.rchdliang.copymanga_neo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class UnicodePolicyTest {
    @Test
    fun passwordAndCommentLimitsCountSupplementaryCharactersOnce() {
        assertEquals(3, CommentText.length(" 😀中文 "))
        assertNull(RegistrationPolicy.passwordError("😀".repeat(6)))
        assertNotNull(RegistrationPolicy.passwordError("😀".repeat(5)))
        assertNotNull(RegistrationPolicy.passwordError("😀".repeat(37)))
        assertEquals(1, "\uD800".unicodeCodePointCount())
    }
}
