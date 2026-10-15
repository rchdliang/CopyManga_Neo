package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable

/** Material3 renamed Label on Android beta01; CMP still exposes its alpha name. */
@Composable
internal expect fun DownloadMenuGroupLabel(content: @Composable () -> Unit)
