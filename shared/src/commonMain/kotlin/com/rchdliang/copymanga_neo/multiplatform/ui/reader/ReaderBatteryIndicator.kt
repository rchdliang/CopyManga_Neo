package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.sp
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoItem
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoStyle
import androidx.compose.ui.unit.dp

/** 电池整体顺时针旋转，百分比保持水平；浅描边兼顾白色漫画背景。 */
@Composable
internal fun ReaderBatteryIndicator(
    battery: ReaderBatteryStatus, modifier: Modifier = Modifier,
    appearance: ReaderInfoStyle = ReaderInfoStyle(ReaderInfoItem.BATTERY.defaultPosition)
) {
    val icon = when (battery.glyph()) {
        ReaderBatteryGlyph.EMPTY -> Icons.Filled.Battery0Bar
        ReaderBatteryGlyph.BAR_1 -> Icons.Filled.Battery1Bar
        ReaderBatteryGlyph.BAR_2 -> Icons.Filled.Battery2Bar
        ReaderBatteryGlyph.BAR_3 -> Icons.Filled.Battery3Bar
        ReaderBatteryGlyph.BAR_4 -> Icons.Filled.Battery4Bar
        ReaderBatteryGlyph.BAR_5 -> Icons.Filled.Battery5Bar
        ReaderBatteryGlyph.BAR_6 -> Icons.Filled.Battery6Bar
        ReaderBatteryGlyph.FULL -> Icons.Filled.BatteryFull
        ReaderBatteryGlyph.CHARGING -> Icons.Filled.BatteryChargingFull
        ReaderBatteryGlyph.UNKNOWN -> Icons.AutoMirrored.Filled.BatteryUnknown
    }
    val painter = rememberVectorPainter(icon)
    val description = when {
        battery.charging -> "电池正在充电"
        battery.validPercentage == null -> "电池状态未知"
        else -> "电池电量"
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter, description,
            Modifier.size(with(LocalDensity.current) { (appearance.fontSizeSp * 1.125f).sp.toDp() }).rotate(90f).drawBehind {
                if (appearance.outlineDp == 0) return@drawBehind
                val offset = appearance.outlineDp.dp.toPx() / 2
                val outline = ColorFilter.tint(appearance.outlineColor.copy(alpha = 0.3f))
                with(painter) {
                    translate(left = -offset) { draw(size, colorFilter = outline) }
                    translate(left = offset) { draw(size, colorFilter = outline) }
                    translate(top = -offset) { draw(size, colorFilter = outline) }
                    translate(top = offset) { draw(size, colorFilter = outline) }
                }
            },
            tint = appearance.textColor
        )
        battery.validPercentage?.let {
            ReaderInfoLabel("$it%", appearance, numeric = true)
        }
    }
}
