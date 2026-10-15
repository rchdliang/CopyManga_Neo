package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun DownloadMenuGroupLabel(content: @Composable () -> Unit) {
    MenuDefaults.DropdownMenuGroupLabel(content = content)
}
