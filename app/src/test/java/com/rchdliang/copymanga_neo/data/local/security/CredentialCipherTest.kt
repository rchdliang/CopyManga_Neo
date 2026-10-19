package com.rchdliang.copymanga_neo.data.local.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec
import java.io.IOException

class CredentialCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    @Test fun roundTripUsesFreshNonceAndAuthenticatedIdentity() {
        val cipher = CredentialCipher("auth") { key }
        val original = "private-token-and-password".toByteArray()
        val first = cipher.encrypt(original, true)
        val second = cipher.encrypt(original, true)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("private-token"))
        assertArrayEquals(original, cipher.decrypt(first))
        assertThrows(IOException::class.java) { CredentialCipher("cookies") { key }.decrypt(first) }
    }
    @Test fun damagedVersionOrCiphertextNeverRequestsNewKey() {
        val encoded = CredentialCipher("auth") { key }.encrypt(byteArrayOf(1, 2), true)
        var creationRequested = false
        val reader = CredentialCipher("auth") { create -> creationRequested = create; key }
        val damaged = encoded.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IOException::class.java) { reader.decrypt(damaged) }
        val future = encoded.copyOf().also { it[3] = 2 }
        assertThrows(IOException::class.java) { reader.decrypt(future) }
        assertFalse(creationRequested)
        assertArrayEquals(byteArrayOf(1, 2), reader.decrypt(encoded))
    }
    @Test fun missingDeviceKeyFailsWithoutLosingCiphertext() {
        val original = CredentialCipher("auth") { key }.encrypt(byteArrayOf(3), true)
        val saved = original.copyOf()
        val reader = CredentialCipher("auth") { create -> assertFalse(create); error("unavailable") }
        assertThrows(IOException::class.java) { reader.decrypt(original) }
        assertArrayEquals(saved, original)
    }
}
