package com.rchdliang.copymanga_neo.ui.reader
import androidx.compose.runtime.*
internal class ReaderSettingsPage(val title: String, val content: @Composable () -> Unit)
internal val LocalReaderSettingsNavigation = staticCompositionLocalOf<(ReaderSettingsPage?) -> Unit> { {} }
