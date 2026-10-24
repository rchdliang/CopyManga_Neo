package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import androidx.compose.runtime.*
internal class ReaderSettingsPage(val title: String, val content: @Composable () -> Unit)
internal val LocalReaderSettingsNavigation = staticCompositionLocalOf<(ReaderSettingsPage?) -> Unit> { {} }

/** 系统返回、点击外部与页内返回共用分发；宿主仍可直接关闭阅读器。 */
internal fun readerSettingsBack(hasSection: Boolean, returnToSettings: () -> Unit, dismiss: () -> Unit) {
    if (hasSection) returnToSettings() else dismiss()
}
