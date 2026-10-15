package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.rchdliang.copymanga_neo.multiplatform.ui.components.formattedReadingDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.NetworkDefaults
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost

@Composable
internal fun NetworkSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkSettingsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val enabled = !state.busy
    val toast = LocalToastHost.current
    val active = LocalRouteActive.current
    DisposableEffect(viewModel, active) { onDispose { if (active) viewModel.cancelWork() } }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val goBack = {
        if (advanced) advanced = false else if (state.dirty) discard = true else onNavigateBack()
    }
    BackHandler { goBack() }
    ActiveRouteLaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .imePadding()
        ) {
            TopAppBar(
                title = {
                    Text(
                        if (advanced) "高级连接配置" else "请求模式与网络",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回"
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!advanced) NetworkCard("请求模式") {
                    Column(Modifier.selectableGroup()) {
                        NetworkChoice(
                            "网页模式",
                            draft.requestMode == RequestMode.WEB,
                            enabled
                        ) {
                            viewModel.edit { it.copy(requestMode = RequestMode.WEB) }
                        }
                        NetworkChoice(
                            "App 模式",
                            draft.requestMode == RequestMode.OFFICIAL_APP,
                            enabled
                        ) {
                            viewModel.edit { it.copy(requestMode = RequestMode.OFFICIAL_APP) }
                        }
                    }
                }
                if (advanced) {
                    OutlinedButton(
                        onClick = viewModel::inspectDevice,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("设备信息", color = MaterialTheme.colorScheme.primary)
                    }
                    if (draft.requestMode == RequestMode.OFFICIAL_APP) {
                        OfficialAppSettingsCard(
                            draft.officialApp,
                            viewModel.officialAppDefaults,
                            enabled,
                            onRandomizePseudoId = viewModel::randomizePseudoId,
                            onRandomizeUmString = viewModel::randomizeUmString
                        ) { value ->
                            viewModel.edit { it.copy(officialApp = value) }
                        }
                    }
                    if (draft.requestMode == RequestMode.WEB) {
                        NetworkCard("拷贝地址") {
                            NetworkField(
                                draft.apiHost,
                                "API 域名",
                                enabled,
                                onReset = { viewModel.edit { it.copy(apiHost = NetworkDefaults.API_HOST) } }
                            ) { value -> viewModel.edit { it.copy(apiHost = value) } }
                            NetworkField(
                                draft.wwwHost,
                                "网页域名",
                                enabled,
                                onReset = { viewModel.edit { it.copy(wwwHost = NetworkDefaults.WWW_HOST) } }
                            ) { value -> viewModel.edit { it.copy(wwwHost = value) } }
                            NetworkField(
                                draft.loginHost.ifBlank { draft.apiHost },
                                "登录域名",
                                enabled,
                                onReset = { viewModel.edit { it.copy(loginHost = "") } }
                            ) { value -> viewModel.edit { it.copy(loginHost = value) } }
                            NetworkField(
                                draft.version,
                                "请求版本",
                                enabled,
                                onReset = { viewModel.edit { it.copy(version = NetworkDefaults.WEB_VERSION) } }
                            ) { value -> viewModel.edit { it.copy(version = value) } }
                        }
                        NetworkCard("线路与节点") {
                            Column(Modifier.selectableGroup()) {
                                NetworkSelection.entries.forEach { mode ->
                                    val label = when (mode) {
                                        NetworkSelection.DEFAULT -> "默认 API"
                                        NetworkSelection.ROUTE -> "线路内轮询"
                                        NetworkSelection.FIXED -> "固定节点"
                                    }
                                    NetworkChoice(
                                        label, draft.selection == mode,
                                        enabled && (mode == NetworkSelection.DEFAULT || draft.routes.isNotEmpty())
                                    ) {
                                        viewModel.edit {
                                            it.copy(
                                                selection = mode,
                                                fixedHost = it.fixedHost.ifBlank {
                                                    it.routes.flatten().firstOrNull().orEmpty()
                                                })
                                        }
                                    }
                                }
                            }
                            if (draft.routes.isEmpty()) NetworkHint("暂无线路")
                            Column(Modifier.selectableGroup()) {
                                draft.routes.forEachIndexed { index, nodes ->
                                    if (draft.selection == NetworkSelection.ROUTE) {
                                        NetworkChoice(
                                            "线路 ${index + 1}",
                                            draft.routeIndex == index,
                                            enabled
                                        ) {
                                            viewModel.edit { it.copy(routeIndex = index) }
                                        }
                                    } else NetworkHint("线路 ${index + 1}")
                                    nodes.forEach { host ->
                                        if (draft.selection == NetworkSelection.FIXED) {
                                            NetworkChoice(
                                                host,
                                                draft.fixedHost == host,
                                                enabled
                                            ) { viewModel.edit { it.copy(fixedHost = host) } }
                                        } else NetworkHint(host)
                                    }
                                }
                            }
                            if (draft.discoveredAt > 0) NetworkHint(
                                "上次获取：${
                                    formattedReadingDate(draft.discoveredAt)
                                }"
                            )
                            if (state.dirty) NetworkHint("请先保存配置")
                            OutlinedButton(
                                onClick = viewModel::discover,
                                enabled = enabled && !state.dirty
                            ) {
                                Text("获取拷贝线路", color = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "启动时检查线路更新",
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    draft.autoDiscover,
                                    { value -> viewModel.edit { it.copy(autoDiscover = value) } },
                                    enabled = enabled
                                )
                            }
                        }
                    }
                }
                if (!advanced) NetworkCard("代理") {
                    Column(Modifier.selectableGroup()) {
                        ProxyMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ProxyMode.SYSTEM -> "跟随系统代理"
                                ProxyMode.MANUAL -> "手动 HTTP 代理"
                                ProxyMode.DIRECT -> "直连"
                            }
                            NetworkChoice(
                                label,
                                draft.proxyMode == mode,
                                enabled
                            ) { viewModel.edit { it.copy(proxyMode = mode) } }
                        }
                    }
                    if (draft.proxyMode == ProxyMode.MANUAL) NetworkField(
                        draft.proxyAddress,
                        "代理地址",
                        enabled
                    ) { value ->
                        viewModel.edit { it.copy(proxyAddress = value) }
                    }
                }
                if (advanced) NetworkCard("连接诊断") {
                    OutlinedButton(onClick = viewModel::test, enabled = enabled) {
                        Text("连接测速", color = MaterialTheme.colorScheme.primary)
                    }
                    state.probes.forEach { probe ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                probe.host,
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(probe.latencyMillis?.let { "$it ms" } ?: "连接失败",
                                color = if (probe.latencyMillis == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (draft.requestMode == RequestMode.WEB && state.probes.isNotEmpty() && draft.routes.isNotEmpty()) TextButton(
                        onClick = viewModel::chooseFastest,
                        enabled = enabled
                    ) {
                        Text("选择最快项", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!advanced) ProfileListItem(
                    Icons.Outlined.Tune, "高级连接配置",
                    if (draft.requestMode == RequestMode.WEB) "域名、线路与连接诊断" else "设备配置、服务地址与连接诊断",
                    index = 0, count = 1, onClick = { advanced = true }, enabled = enabled
                )
                if (state.dirty) NetworkHint("未保存")
                Button(
                    onClick = viewModel::save,
                    enabled = enabled && state.dirty,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存并应用", color = MaterialTheme.colorScheme.onPrimary)
                }
                if (advanced) OutlinedButton(
                    onClick = viewModel::rollback,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复上次配置", color = MaterialTheme.colorScheme.primary)
                }
                if (advanced) TextButton(
                    onClick = { reset = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复默认", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    state.diagnosticReport?.let { report ->
        OfficialAppDiagnosticsDialog(
            report,
            state.busy,
            viewModel::inspectDevice,
            viewModel::closeDiagnosticReport
        )
    }
    if (discard) if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("放弃未保存的修改？", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = { discard = false; viewModel.discard(); onNavigateBack() }) {
                Text("放弃修改", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { discard = false }) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        })
    if (reset) if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text("恢复默认网络设置？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "地址、代理和线路将恢复默认设置。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = {
                reset = false; viewModel.reset()
            }) { Text("恢复默认", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { reset = false }) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        })
}

@Composable
private fun NetworkCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun NetworkHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NetworkField(
    value: String,
    label: String,
    enabled: Boolean,
    onReset: (() -> Unit)? = null,
    onRandomize: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (onReset == null && onRandomize == null) null else {
            {
                Row {
                    onReset?.let { reset ->
                        IconButton(onClick = reset, enabled = enabled) {
                            Icon(
                                Icons.Outlined.Clear, "恢复默认$label",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    onRandomize?.let { randomize ->
                        IconButton(onClick = randomize, enabled = enabled) {
                            Icon(
                                Icons.Outlined.Refresh, "重新生成$label",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NetworkChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OfficialAppSettingsCard(
    value: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings,
    defaults: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings,
    enabled: Boolean,
    onRandomizePseudoId: () -> Unit,
    onRandomizeUmString: () -> Unit,
    onChange: (com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings) -> Unit
) {
    NetworkCard("App 连接配置") {
        NetworkField(
            value.apiHost, "API 域名", enabled,
            onReset = { onChange(value.copy(apiHost = defaults.apiHost)) }
        ) { onChange(value.copy(apiHost = it)) }
        NetworkField(
            value.loginHost, "登录域名", enabled,
            onReset = { onChange(value.copy(loginHost = defaults.loginHost)) }
        ) { onChange(value.copy(loginHost = it)) }
        NetworkField(
            value.version, "App 版本", enabled,
            onReset = { onChange(value.copy(version = defaults.version)) }
        ) { onChange(value.copy(version = it)) }
        NetworkField(
            value.versionCode, "版本号", enabled,
            onReset = { onChange(value.copy(versionCode = defaults.versionCode)) }
        ) { onChange(value.copy(versionCode = it)) }
        NetworkField(
            value.pseudoId, "SDK 标识", enabled, onRandomize = onRandomizePseudoId
        ) { onChange(value.copy(pseudoId = it)) }
        NetworkField(
            value.umString, "统计标识", enabled, onRandomize = onRandomizeUmString
        ) { onChange(value.copy(umString = it)) }
        NetworkField(
            value.device, "设备构建号", enabled,
            onReset = { onChange(value.copy(device = defaults.device)) }
        ) { onChange(value.copy(device = it)) }
        NetworkField(
            value.deviceInfo, "设备型号", enabled,
            onReset = { onChange(value.copy(deviceInfo = defaults.deviceInfo)) }
        ) { onChange(value.copy(deviceInfo = it)) }
        NetworkField(
            value.buildTime, "构建日期", enabled,
            onReset = { onChange(value.copy(buildTime = defaults.buildTime)) }
        ) { onChange(value.copy(buildTime = it)) }
    }
}
