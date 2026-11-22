package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.getDisplayCutoutBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.rchdliang.copymanga_neo.multiplatform.ui.components.metadataText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.ceil

/** 填充与细描边使用同一文本布局；不绘制覆盖漫画的背景。 */
@Composable
internal fun ReaderOverlayText(text: String, modifier: Modifier = Modifier,
    color: Color = Color.White, style: TextStyle = MaterialTheme.typography.titleMedium,
    maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Ellipsis) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, modifier.drawBehind {
        layout?.let { drawText(it, color = Color.Black.copy(alpha = 0.5f), drawStyle = Stroke(width = 1.dp.toPx())) }
    }, color = color, style = style, maxLines = maxLines, overflow = overflow, onTextLayout = { layout = it })
}

@Composable
internal fun ReaderRestingOverlay(chapter: String, pages: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderOverlayText(metadataText(chapter), Modifier.weight(1f))
        ReaderOverlayText(pages, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
    }
}

/** 使用本地时区；每秒检查系统时间，分钟变化时才更新文字。 */
@Composable
internal fun ReaderDeviceStatusOverlay(platform: ReaderPlatform?, modifier: Modifier = Modifier) {
    val time by produceState(readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())) {
        while (true) {
            value = readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())
            delay(1_000.milliseconds)
        }
    }
    val batteryFlow = remember(platform) { platform?.batteryStatuses() ?: flowOf(ReaderBatteryStatus()) }
    val battery by batteryFlow.collectAsState(initial = ReaderBatteryStatus())
    ReaderStatusOverlay(time, battery, modifier)
}

@Composable
internal fun ReaderStatusOverlay(time: String, battery: ReaderBatteryStatus, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.waterfall.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr)
        ReaderOverlayText(time, Modifier.avoidTopCutout(), style = style)
        ReaderBatteryIndicator(battery, Modifier.avoidTopCutout())
    }
}

/** 边界由 Compose 转换到当前元素坐标；旋转或窗口位置变化时重新放置。 */
private fun Modifier.avoidTopCutout(): Modifier = layout { measurable, constraints ->
    val label = measurable.measure(constraints)
    layout(label.width, label.height) {
        val gap = 6.dp.roundToPx()
        val cutouts = getDisplayCutoutBounds()
        if (cutouts.isEmpty()) {
            // 只有安全边距时也兼顾横屏下出现在左、右边缘的缺口。
            val safe = WindowInsetsRulers.DisplayCutout.current
            val left = (safe.left.current(-gap.toFloat()) + gap).coerceAtLeast(0f)
            val right = (label.width + gap - safe.right.current((label.width + gap).toFloat())).coerceAtLeast(0f)
            val top = (safe.top.current(-gap.toFloat()) + gap).coerceAtLeast(0f)
            label.place(ceil(left).toInt() - ceil(right).toInt(), ceil(top).toInt())
        } else {
            var dy = 0f
            for (cutout in cutouts) {
                val left = cutout.left.current(Float.POSITIVE_INFINITY)
                val right = cutout.right.current(Float.NEGATIVE_INFINITY)
                val top = cutout.top.current(Float.POSITIVE_INFINITY)
                val bottom = cutout.bottom.current(Float.NEGATIVE_INFINITY)
                if (left < label.width + gap && right > -gap && top < label.height + gap && bottom > -gap) {
                    dy = maxOf(dy, bottom + gap)
                }
            }
            label.place(0, ceil(dy).toInt())
        }
    }
}

internal fun readerClockLabel(instant: Instant, zone: TimeZone): String {
    val local = instant.toLocalDateTime(zone)
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun readerRestingPageLabel(current: String, total: Int): String = "$current / $total"
