package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt

/** Experimental, session-only. Zero adds no layer and leaves the existing image path untouched. */
internal val LocalReaderSharpenStatus = staticCompositionLocalOf<((String, Boolean) -> Unit)?> { null }

val LocalReaderSharpness = staticCompositionLocalOf { platformReaderSharpnessDefault() }

internal fun normalizedReaderSharpness(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, READER_SHARPNESS_MAX) else 0f

/** 支持平板与 4K 单页；每个 RGBA8 离屏表面最多 32 MiB，超长图仍回退。 */
internal fun readerSharpenSurfaceAllowed(width: Int, height: Int): Boolean =
    width in 1..4096 && height in 1..4096 && width.toLong() * height <= 8_388_608L

internal expect fun platformReaderSharpnessDefault(): Float

@Composable
internal expect fun readerSharpnessAvailable(): Boolean

/** Platform implementations operate on the rendered image only; never on comments or controls. */
@Composable
internal expect fun Modifier.readerSharpen(strength: Float, pageKey: String = ""): Modifier

internal const val READER_SHARPNESS_MAX = 1f
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
        androidx.compose.material3.Text("锐化", androidx.compose.ui.Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.Switch(checked = strength > 0f,
            onCheckedChange = { onChange(if (it) restore else 0f) }, enabled = available, modifier = Modifier.testTag("reader-sharpen-switch"))
    }
    if (!available) androidx.compose.material3.Text("当前系统或渲染环境不支持锐化，显示原图", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (strength > 0f) {
        androidx.compose.material3.Text("强度 ${(strength / READER_SHARPNESS_MAX * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.Slider(value = strength, onValueChange = { onChange(normalizedReaderSharpness(it)) }, valueRange = 0f..READER_SHARPNESS_MAX, steps = 19, modifier = Modifier.testTag("reader-sharpen-strength"))
        androidx.compose.material3.TextButton(onClick = onReset) { androidx.compose.material3.Text("恢复默认", color = MaterialTheme.colorScheme.primary) }
    }
}

/** 对比只改变本次显示，不写入阅读设置；保留其他画质处理以单独观察锐化。 */
@Composable
internal fun ReaderSharpnessComparison(
    before: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (before) "锐化前" else "锐化后", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("reader-sharpen-comparison-state"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onToggle, modifier = Modifier.testTag("reader-sharpen-compare-toggle")) {
                    Text(if (before) "查看锐化后" else "查看锐化前", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                TextButton(onClick = onClose) { Text("完成对比", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
