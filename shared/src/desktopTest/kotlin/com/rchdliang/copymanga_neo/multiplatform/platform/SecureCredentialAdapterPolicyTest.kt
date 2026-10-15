package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.*

/** Pure adapter policy tests: never start secret-tool, DPAPI or access the user's credentials. */
class SecureCredentialAdapterPolicyTest {
    @Test fun absenceRequiresSuccessfulEmptySearch() {
        assertTrue(secretToolConfirmsAbsence(0, "", ""))
        assertFalse(secretToolConfirmsAbsence(1, "", ""))
        assertFalse(secretToolConfirmsAbsence(0, "[item]\nlabel = synthetic", ""))
        assertFalse(secretToolConfirmsAbsence(0, "", "locked"))
        assertFalse(secretToolConfirmsAbsence(1, "", "service unavailable"))
    }
    @Test fun inputIsBoundedBeforeSecretToolCanTruncateIt() {
        validateSecretToolEnvelope("x".repeat(8190))
        assertFailsWith<IllegalArgumentException> { validateSecretToolEnvelope("x".repeat(8191)) }
        assertFailsWith<IllegalArgumentException> { validateSecretToolEnvelope("测".repeat(3000)) }
    }
}
