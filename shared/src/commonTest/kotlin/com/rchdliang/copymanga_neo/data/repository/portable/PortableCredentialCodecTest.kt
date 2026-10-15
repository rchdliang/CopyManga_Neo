package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class PortableCredentialCodecTest {
    private val identity=AppCredentialIdentity("3.0.9","83","testPseudo0000000","0123456789abcdef0123456789abcdef","testDevice","testInfo","2025.05.22")
    @Test fun webCredentialRoundTripRetainsCurrentModeOnly() {
        val input=LoginCredentialBundle(RequestMode.WEB,listOf(LoginCredential(RequestMode.WEB,"test-token","test-user")))
        val encoded=PortableCredentialCodec.encode(input)
        assertTrue(encoded.startsWith("copymanga-neo:v3:"));assertFalse(encoded.endsWith('='));assertEquals(input,PortableCredentialCodec.decode(encoded))
    }
    @Test fun appCredentialRetainsExactIdentityAndOptionalPassword() {
        val input=LoginCredentialBundle(RequestMode.OFFICIAL_APP,listOf(LoginCredential(RequestMode.OFFICIAL_APP,"test-token","test-user")),identity,SavedLoginCredential("test-user","explicit-test-password"))
        assertEquals(input,PortableCredentialCodec.decode(PortableCredentialCodec.encode(input)))
    }
    @Test fun currentAppExportCannotOmitIdentity() {
        assertFailsWith<IllegalArgumentException>{PortableCredentialCodec.encode(LoginCredentialBundle(RequestMode.OFFICIAL_APP,listOf(LoginCredential(RequestMode.OFFICIAL_APP,"test-token","test-user"))))}
    }
    @Test fun legacyMultiModeImportRetainsActiveModeAndMarksOtherAvailable() {
        val body="""{"active_mode":"WEB","sessions":[{"mode":"WEB","token":"web-token","username":"web-user"},{"mode":"OFFICIAL_APP","token":"app-token","username":"app-user"}]}"""
        val input="copymanga-neo:v2:"+Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).encode(body.encodeToByteArray())
        val decoded=PortableCredentialCodec.decode(input)
        assertEquals(RequestMode.WEB,decoded.activeMode);assertEquals(2,decoded.sessions.size);assertNull(decoded.appIdentity)
    }
    @Test fun duplicateModesAndOversizedImportsAreRejected() {
        val input=LoginCredentialBundle(RequestMode.WEB,List(2){LoginCredential(RequestMode.WEB,"test-token","test-user")})
        assertFailsWith<IllegalArgumentException>{PortableCredentialCodec.encode(input)}
        assertFailsWith<IllegalArgumentException>{PortableCredentialCodec.decode("x".repeat(32769))}
    }
}
