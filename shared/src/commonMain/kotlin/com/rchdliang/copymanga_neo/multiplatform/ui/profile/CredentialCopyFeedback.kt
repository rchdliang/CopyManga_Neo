package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled

internal fun credentialCopyFeedback(result: Result<Unit>): String? = result.fold(
    onSuccess = { "登录凭据已复制到剪切板" },
    onFailure = { if (it is UserActionCancelled) null else "无法写入剪切板" }
)
