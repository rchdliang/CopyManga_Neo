package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ThemeLoadingPolicy
import kotlin.math.roundToInt

@Composable
internal fun ThemeLoadingControls(
    settings: NetworkSettings,
    enabled: Boolean,
    onConcurrency: (Int) -> Unit,
    onInterval: (Int) -> Unit
) {
    Text("同时预加载：${settings.themeFetchConcurrency} 部", color = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..ThemeLoadingPolicy.MAX_CONCURRENCY).forEach { count ->
            FilterChip(
                selected = count == settings.themeFetchConcurrency,
                onClick = { onConcurrency(count) },
                enabled = enabled,
                label = { Text(count.toString()) }
            )
        }
    }
    Text("间隔：${settings.themeFetchIntervalMillis / 1000.0} 秒", color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = settings.themeFetchIntervalMillis.toFloat(),
        onValueChange = { onInterval((it / 100).roundToInt() * 100) },
        valueRange = 0f..ThemeLoadingPolicy.MAX_INTERVAL_MILLIS.toFloat(),
        steps = ThemeLoadingPolicy.MAX_INTERVAL_MILLIS / 100 - 1,
        enabled = enabled
    )
}
