package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialCopyFeedbackTest {
    @Test fun cancelledCopyDoesNotAnnounceCopiedOrError() {
        assertNull(credentialCopyFeedback(Result.failure(UserActionCancelled())))
        assertEquals("登录凭据已复制到剪切板", credentialCopyFeedback(Result.success(Unit)))
        assertEquals("无法写入剪切板", credentialCopyFeedback(Result.failure(Exception("secret"))))
    }
}
