package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager

@Composable
internal actual fun rememberTextClipboard(): TextClipboard {
    // Android 的 AnnotatedString → ClipData 样式编码仍是 Compose 内部 API。
    // 暂用兼容桥保留原有富文本样式，避免改为纯文本后丢失小说选区格式。
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) { TextClipboard { clipboard.setText(it) } }
}
