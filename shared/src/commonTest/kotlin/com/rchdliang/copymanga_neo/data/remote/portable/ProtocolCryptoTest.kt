package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.remote.portable.util.*
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlin.test.*

class ProtocolCryptoTest {
    @Test fun sha256KnownAnswers() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",ProtocolCrypto.hex(ProtocolCrypto.sha256(byteArrayOf())))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",ProtocolCrypto.hex(ProtocolCrypto.sha256("abc".encodeToByteArray())))
    }
    @Test fun hmacRfc4231KnownAnswers() {
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",ProtocolCrypto.hex(ProtocolCrypto.hmacSha256(ByteArray(20){0x0b},"Hi There".encodeToByteArray())))
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",ProtocolCrypto.hex(ProtocolCrypto.hmacSha256("Jefe".encodeToByteArray(),"what do ya want for nothing?".encodeToByteArray())))
        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",ProtocolCrypto.hex(ProtocolCrypto.hmacSha256(ByteArray(131){0xaa.toByte()},"Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray())))
    }
    @Test fun aesCbcPkcs7MatchesIndependentOpenSslVectors() {
        val vectors=mapOf(16 to "eb7475b6b92e22620296fea0d386218715f230ed8ce92e256f754067e0a7c24c",24 to "895d3eac69c912bde004b937dcfb499595ecee3ab3a560982118eda4c09e4b5b",32 to "15263f53e7f98132e91b13657a3d2cbac03d633671e4342b96b38cdc774f68d7")
        vectors.forEach { (size,ciphertext)->assertEquals("Portable AES: 汉字 😀\n",ProtocolCrypto.aesCbcPkcs7Decrypt(ByteArray(size){it.toByte()},ByteArray(16){(it+16).toByte()},ProtocolCrypto.unhex(ciphertext)).decodeToString()) }
    }
    @Test fun aesRejectsMalformedCiphertextAndPadding() {
        assertFailsWith<IllegalArgumentException>{ProtocolCrypto.aesCbcPkcs7Decrypt(ByteArray(16),ByteArray(16),ByteArray(15))}
        assertFailsWith<IllegalArgumentException>{ProtocolCrypto.aesCbcPkcs7Decrypt(ByteArray(16),ByteArray(16),ByteArray(16))}
        assertFailsWith<IllegalArgumentException>{ProtocolCrypto.unhex("0g")}
    }
    @Test fun catalogProtocolRejectsAmbiguousOrCommentedKeys() {
        val html="""<!--<script>var ccz='ignored000000000';</script>--><script>
            const ccz = '0123456789abcdef';
            </script><input id="dnt" value="12345">"""
        assertEquals(WwwCatalogProtocol("0123456789abcdef","12345"),WwwCatalogProtocol.fromHtml(html))
        assertFailsWith<IllegalArgumentException>{WwwCatalogProtocol.fromHtml(html+"<script>\nvar ccz='fedcba9876543210';</script>")}
    }
    @Test fun allZeroCatalogTriggersRiskAndWrongBookIsRejected() {
        val body="""{"build":{"path_word":"book"},"groups":{"default":{"count":0,"chapters":[]}}}"""
        assertFailsWith<RiskControlException>{ChapterCatalogDecryptor.parseResponse(body,"book","0123456789abcdef")}
        assertFailsWith<IllegalArgumentException>{ChapterCatalogDecryptor.parseResponse(body,"other","0123456789abcdef")}
    }
}
