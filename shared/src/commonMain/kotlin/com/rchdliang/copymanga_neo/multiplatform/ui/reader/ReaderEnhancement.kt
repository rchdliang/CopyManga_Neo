package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Experimental, session-only. Zero adds no layer and leaves the existing image path untouched. */
internal val LocalReaderSharpenStatus = staticCompositionLocalOf<((String, Boolean) -> Unit)?> { null }

val LocalReaderSharpness = staticCompositionLocalOf { platformReaderSharpnessDefault() }

internal fun normalizedReaderSharpness(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 0.35f) else 0f

/** Bounds the extra offscreen surface; this is NOT a decode policy or long-image tiler. */
internal fun readerSharpenSurfaceAllowed(width: Int, height: Int): Boolean =
    width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304L

internal expect fun platformReaderSharpnessDefault(): Float

@Composable
internal expect fun readerSharpnessAvailable(): Boolean

/** Platform implementations operate on the rendered image only; never on comments or controls. */
@Composable
internal expect fun Modifier.readerSharpen(strength: Float, pageKey: String = ""): Modifier

internal const val READER_SHARPNESS_MAX = 0.35f
internal const val READER_SHARPNESS_DEFAULT = 0f
internal const val READER_SHARPNESS_RESTORE = 0.20f

@Composable
internal fun ReaderSharpnessOptions(strength: Float, restore: Float, onReset: () -> Unit, onChange: (Float) -> Unit) {
    ReaderSharpnessControls(readerSharpnessAvailable(), strength, restore, onReset, onChange)
}

@Composable
internal fun ReaderSharpnessControls(available: Boolean, strength: Float, restore: Float, onReset: () -> Unit, onChange: (Float) -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Outlined.Tune, null,
            modifier = Modifier.padding(end = 12.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Text("锐化", androidx.compose.ui.Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Switch(checked = strength > 0f,
            onCheckedChange = { onChange(if (it) restore else 0f) }, enabled = available, modifier = Modifier.testTag("reader-sharpen-switch"))
    }
    if (!available) androidx.compose.material3.Text("当前系统或渲染环境不支持锐化，显示原图")
    else if (strength > 0f) {
        androidx.compose.material3.Text("强度 ${(strength / READER_SHARPNESS_MAX * 100).toInt()}%")
        androidx.compose.material3.Slider(value = strength, onValueChange = { onChange(normalizedReaderSharpness(it)) }, valueRange = 0f..READER_SHARPNESS_MAX, steps = 34)
        androidx.compose.material3.TextButton(onClick = onReset) { androidx.compose.material3.Text("恢复默认") }
    }
}
