package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberReaderRestoreEngine(): ReaderRestoreEngine = remember {
    UnsupportedReaderRestoreEngine("iOS暂未接入画质修复，显示原图")
}
