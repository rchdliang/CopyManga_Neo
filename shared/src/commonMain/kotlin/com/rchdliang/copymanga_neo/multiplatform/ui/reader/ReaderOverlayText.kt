package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoItem
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoPosition
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoSettings
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoStyle
import androidx.compose.ui.text.style.TextDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.rchdliang.copymanga_neo.multiplatform.ui.components.metadataText
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ReaderOverlayText(
    text: String, modifier: Modifier = Modifier,
    color: Color = Color.White, style: TextStyle = MaterialTheme.typography.titleMedium,
    outlineColor: Color = Color.Black, outlineWidth: Dp = 1.dp
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, modifier.drawBehind {
        if (outlineWidth > 0.dp) layout?.let {
            drawText(it, color = outlineColor.copy(alpha = 0.5f), drawStyle = Stroke(width = outlineWidth.toPx()))
        }
    }, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = { layout = it })
}

internal val ReaderInfoStyle.textColor get() = if (darkText) Color.Black else Color.White
internal val ReaderInfoStyle.outlineColor get() = if (darkText) Color.White else Color.Black

@Composable
internal fun ReaderInfoLabel(text: String, appearance: ReaderInfoStyle, numeric: Boolean = false) {
    val base = MaterialTheme.typography.titleMedium
    ReaderOverlayText(text, color = appearance.textColor, outlineColor = appearance.outlineColor,
        outlineWidth = appearance.outlineDp.dp, style = base.copy(fontSize = appearance.fontSizeSp.sp,
            lineHeight = (appearance.fontSizeSp * 1.5f).sp,
            textDirection = if (numeric) TextDirection.Ltr else base.textDirection))
}

@Composable
internal fun ReaderLiveInfoOverlay(
    chapter: String, pages: String, settings: ReaderInfoSettings, platform: ReaderPlatform?, deviceStatusActive: Boolean
) {
    val time = if (deviceStatusActive && settings[ReaderInfoItem.CLOCK].position != ReaderInfoPosition.HIDDEN) {
        val clock by produceState(readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())) {
            while (true) {
                value = readerClockLabel(Clock.System.now(), TimeZone.currentSystemDefault())
                delay(1_000.milliseconds)
            }
        }
        clock
    } else null
    val battery = if (deviceStatusActive && settings[ReaderInfoItem.BATTERY].position != ReaderInfoPosition.HIDDEN) {
        val flow = remember(platform) { platform?.batteryStatuses() ?: flowOf(ReaderBatteryStatus()) }
        flow.collectAsState(initial = ReaderBatteryStatus()).value
    } else null
    ReaderInfoOverlay(chapter, pages, time, battery, settings)
}

@Composable
internal fun ReaderInfoOverlay(
    chapter: String, pages: String, time: String?, battery: ReaderBatteryStatus?,
    settings: ReaderInfoSettings = ReaderInfoSettings(),
    freeAreaModifier: Modifier = Modifier,
    itemModifier: @Composable (ReaderInfoItem) -> Modifier = { Modifier }
) {
    val groups = remember(settings, time != null, battery != null) {
        ReaderInfoItem.entries.filter {
            (it != ReaderInfoItem.CLOCK || time != null) && (it != ReaderInfoItem.BATTERY || battery != null)
        }.groupBy { settings[it].position }
    }
    Box(Modifier.fillMaxSize()) {
        for (top in listOf(true, false)) {
            val left = groups[if (top) ReaderInfoPosition.TOP_LEFT else ReaderInfoPosition.BOTTOM_LEFT].orEmpty()
            val right = groups[if (top) ReaderInfoPosition.TOP_RIGHT else ReaderInfoPosition.BOTTOM_RIGHT].orEmpty()
            val center = groups[if (top) ReaderInfoPosition.TOP_CENTER else ReaderInfoPosition.BOTTOM_CENTER].orEmpty()
            if (left.isEmpty() && right.isEmpty() && center.isEmpty()) continue
            val insets = if (top) WindowInsets.waterfall.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            else WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            ReaderInfoCorners(Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(insets).padding(horizontal = 12.dp, vertical = 6.dp),
                top = top, measureLeftFirst = ReaderInfoItem.CHAPTER in right,
                bothVisible = left.isNotEmpty() && right.isNotEmpty(),
                centerHasNeighbors = center.isNotEmpty() && (left.isNotEmpty() || right.isNotEmpty())) {
                for ((alignment, items) in listOf(AbsoluteAlignment.Left to left, AbsoluteAlignment.Right to right,
                    Alignment.CenterHorizontally to center)) {
                    Column(horizontalAlignment = alignment) {
                        for (item in items) key(item) {
                            Box(itemModifier(item)) { ReaderInfoValue(item, settings[item], chapter, pages, time, battery) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 6.dp).then(freeAreaModifier)) {
            for (item in groups[ReaderInfoPosition.CUSTOM].orEmpty()) key(item) {
                val style = settings[item]
                val alignment = BiasAbsoluteAlignment(style.horizontalFraction * 2 - 1, style.verticalFraction * 2 - 1)
                Box(Modifier.align(alignment).then(itemModifier(item))) {
                    ReaderInfoValue(item, style, chapter, pages, time, battery)
                }
            }
        }
    }
}

@Composable
private fun ReaderInfoValue(
    item: ReaderInfoItem, style: ReaderInfoStyle, chapter: String, pages: String, time: String?, battery: ReaderBatteryStatus?
) {
    when (item) {
        ReaderInfoItem.CLOCK -> ReaderInfoLabel(time.orEmpty(), style, numeric = true)
        ReaderInfoItem.BATTERY -> battery?.let { ReaderBatteryIndicator(it, appearance = style) }
        ReaderInfoItem.CHAPTER -> ReaderInfoLabel(metadataText(chapter), style)
        ReaderInfoItem.PAGES -> ReaderInfoLabel(pages, style, numeric = true)
    }
}

internal fun readerClockLabel(instant: Instant, zone: TimeZone): String {
    val local = instant.toLocalDateTime(zone)
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun readerRestingPageLabel(current: String, total: Int): String = "$current / $total"
