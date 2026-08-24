package com.rchdliang.copymanga_neo.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoginCredentialCodecTest {

    @Test
    fun roundTripPreservesTokenAndUnicodeUsername() {
        val serialized = LoginCredentialCodec.encode(
            token = "token.with_symbols-_=+",
            username = "测试用户"
        )

        assertTrue(serialized.startsWith("copymanga-neo:v1:"))
        assertEquals(
            LoginCredential(token = "token.with_symbols-_=+", username = "测试用户"),
            LoginCredentialCodec.decode(serialized)
        )
    }

    @Test
    fun decodeAllowsClipboardWhitespace() {
        val serialized = LoginCredentialCodec.encode(token = "token", username = "user")

        assertEquals(
            LoginCredential(token = "token", username = "user"),
            LoginCredentialCodec.decode("  $serialized\n")
        )
    }

    @Test
    fun decodeRejectsUnknownVersion() {
        assertInvalid("copymanga-neo:v2:payload", "不支持的登录凭据版本")
    }

    @Test
    fun decodeRejectsForeignClipboardText() {
        assertInvalid("username=password", "不是 CopyManga Neo 登录凭据")
    }

    @Test
    fun decodeRejectsMalformedPayload() {
        assertInvalid("copymanga-neo:v1:not_base64!", "登录凭据格式无效")
    }

    private fun assertInvalid(serialized: String, expectedMessage: String) {
        try {
            LoginCredentialCodec.decode(serialized)
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }
}
