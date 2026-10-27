package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CancellationException

/** 等待写入完成后再更新复制提示或清除选区。 */
internal fun interface TextClipboard {
    suspend fun setText(text: AnnotatedString)
}

/** 权限拒绝等复制错误交给界面提示，协程取消继续向上传播。 */
internal suspend fun TextClipboard.copyText(text: AnnotatedString): Result<Unit> = try {
    setText(text)
    Result.success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

/** 各平台保留原有的文本与样式复制能力。 */
@Composable
internal expect fun rememberTextClipboard(): TextClipboard
