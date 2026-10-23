package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Experimental, session-only. Zero adds no layer and leaves the existing image path untouched. */
val LocalReaderSharpness = staticCompositionLocalOf { platformReaderSharpnessDefault() }

internal fun normalizedReaderSharpness(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 0.35f) else 0f

/** Bounds the extra offscreen surface; this is NOT a decode policy or long-image tiler. */
internal fun readerSharpenSurfaceAllowed(width: Int, height: Int): Boolean =
    width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304L

internal expect fun platformReaderSharpnessDefault(): Float

/** Platform implementations operate on the rendered image only; never on comments or controls. */
@Composable
internal expect fun Modifier.readerSharpen(strength: Float): Modifier
