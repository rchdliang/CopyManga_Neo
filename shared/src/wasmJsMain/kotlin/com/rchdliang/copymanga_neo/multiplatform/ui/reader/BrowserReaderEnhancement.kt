package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual fun platformReaderSharpnessDefault(): Float = 0f

// No verified renderer backend on this platform. Preserve the original image.
@Composable
internal actual fun Modifier.readerSharpen(strength: Float): Modifier = this
