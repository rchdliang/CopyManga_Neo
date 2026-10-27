package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    return remember(clipboard) {
        TextClipboard { clipboard.setClipEntry(ClipEntry.withPlainText(it.text)) }
    }
}
