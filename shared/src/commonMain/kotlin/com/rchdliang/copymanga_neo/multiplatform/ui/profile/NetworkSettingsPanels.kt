package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.RequestMode

internal val NetworkWideWidth = 840.dp

@Composable
internal fun NetworkColumns(primary: @Composable () -> Unit, secondary: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = Arrangement.spacedBy(12.dp)
        if (maxWidth >= NetworkWideWidth) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.12f), verticalArrangement = gap) { primary() }
                Column(Modifier.weight(1f), verticalArrangement = gap) { secondary() }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = gap) {
                primary()
                secondary()
            }
        }
    }
}

@Composable
internal fun NetworkPanel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun <T> NetworkOptions(
    title: String, options: List<T>, selected: T, enabled: Boolean,
    label: (T) -> String, select: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 86.dp * options.size * LocalDensity.current.fontScale) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = selected == value, onClick = { select(value) }, enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text(label(value), textAlign = TextAlign.Center) }
                    }
                }
            } else {
                Column(Modifier.selectableGroup()) {
                    options.forEach { value -> NetworkChoice(label(value), value == selected, enabled) { select(value) } }
                }
            }
        }
    }
}

@Composable
internal fun NetworkDiagnostics(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    NetworkPanel("连接测试", Icons.Outlined.NetworkCheck, action = {
        FilledTonalButton(onClick = viewModel::test, enabled = !state.busy) {
            if (state.testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(when {
                state.testing -> "测试中…"
                state.dirty -> "测试草稿"
                state.probes.isNotEmpty() -> "重测"
                else -> "测试"
            })
        }
    }) {
        if (state.probes.isNotEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            HorizontalDivider()
            Text("已响应 ${state.probes.count { it.latencyMillis != null }} / ${state.probes.size}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val results = if (expanded) state.probes else state.probes.take(4)
            results.forEach { probe ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(probe.host, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val latency = probe.latencyMillis
                    val outcome = latency?.let { "$it ms" } ?: if (probe.error == "连接超时") "连接超时" else "连接失败"
                    Text(outcome,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (latency == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            if (state.probes.size > 4 || (state.draft.requestMode == RequestMode.WEB && state.draft.routes.isNotEmpty())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.probes.size > 4) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起结果" else "查看全部 ${state.probes.size} 个地址")
                        }
                    }
                    if (state.draft.requestMode == RequestMode.WEB && state.draft.routes.isNotEmpty()) {
                        TextButton(onClick = viewModel::chooseFastest, enabled = !state.busy) { Text("选择最快项") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NetworkHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun NetworkField(
    value: String,
    label: String,
    enabled: Boolean,
    onReset: (() -> Unit)? = null,
    onRandomize: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    val fieldError = networkFieldError(label, value)
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(label) },
        isError = fieldError != null,
        supportingText = if (fieldError != null) { { Text(fieldError) } } else null,
        trailingIcon = if (onReset == null && onRandomize == null) null else {
            {
                Row {
                    onReset?.let { reset ->
                        IconButton(onClick = reset, enabled = enabled) {
                            Icon(
                                Icons.Outlined.Restore, "恢复默认$label"
                            )
                        }
                    }
                    onRandomize?.let { randomize ->
                        IconButton(onClick = randomize, enabled = enabled) {
                            Icon(
                                Icons.Outlined.Refresh, "重新生成$label"
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun NetworkChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}
