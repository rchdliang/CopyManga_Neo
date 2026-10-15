package com.rchdliang.copymanga_neo.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginValidationPolicyTest {
    @Test fun passwordMismatchIsRetryableOnlyOnLoginEndpoint() {
        for (message in listOf("密码错误", "密碼錯誤", " 密码错误 ")) {
            assertTrue(ApiBusinessErrors.isValidation("/api/v3/login", message))
            assertFalse(ApiBusinessErrors.isValidation("/api/v3/member/info", message))
        }
    }

    @Test fun rateLimitsAndUnknownLoginFailuresStillUseRiskGate() {
        for (message in listOf(null, "", "slow down", "密码错误次数过多", "访问频繁，请稍后重试")) {
            assertFalse(ApiBusinessErrors.isValidation("/api/v3/login", message))
        }
    }
}
