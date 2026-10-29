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
internal fun ReaderRestingOverlay(chapter: String, modifier: Modifier = Modifier) {
    ReaderOverlayText(metadataText(chapter),
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp))
}

/** 使用本地时区；每秒检查系统时间，分钟变化时才更新文字。 */
@Composable
internal fun ReaderDeviceStatusOverlay(platform: ReaderPlatform?, pages: String, modifier: Modifier = Modifier) {
    val time by produceState(readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())) {
        while (true) {
            value = readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())
            delay(1_000.milliseconds)
        }
    }
    val batteryFlow = remember(platform) { platform?.batteryStatuses() ?: flowOf(ReaderBatteryStatus()) }
    val battery by batteryFlow.collectAsState(initial = ReaderBatteryStatus())
    ReaderStatusOverlay(time, battery, modifier, pages)
}

@Composable
internal fun ReaderStatusOverlay(time: String, battery: ReaderBatteryStatus, modifier: Modifier = Modifier, pages: String? = null) {
    Row(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr)
        Box(Modifier.weight(1f)) { ReaderOverlayText(time, style = style) }
        pages?.let { ReaderOverlayText(it, style = style) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ReaderBatteryIndicator(battery) }
    }
}

internal fun readerClockLabel(instant: Instant, zone: TimeZone): String {
    val local = instant.toLocalDateTime(zone)
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun readerRestingPageLabel(current: String, total: Int): String = "$current / $total"
