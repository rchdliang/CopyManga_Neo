package com.rchdliang.copymanga_neo.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkSettingsScreen(onNavigateBack: () -> Unit, viewModel: NetworkSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val enabled = !state.busy
    val toast = LocalToastHost.current
    DisposableEffect(viewModel) { onDispose { viewModel.cancelWork() } }
    var discard by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val goBack = { if (state.dirty) discard = true else onNavigateBack() }
    BackHandler { goBack() }
    LaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    Column(Modifier
        .fillMaxSize()
        .imePadding()) {
        TopAppBar(
            title = { Text("网络设置", color = MaterialTheme.colorScheme.onSurface) },
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
        Column(Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NetworkLogCard()
            NetworkCard("请求模式") {
                NetworkHint("两套地址分别保存。App 模式通过 API 加载目录和章节；失败时不会自动切换网页。更改模式或 App 身份后需重新登录。")
                Column(Modifier.selectableGroup()) {
                    NetworkChoice(
                        "网页模式（原有路径）",
                        draft.requestMode == RequestMode.WEB,
                        enabled
                    ) {
                        viewModel.edit { it.copy(requestMode = RequestMode.WEB) }
                    }
                    NetworkChoice(
                        "App 模式（实验性）",
                        draft.requestMode == RequestMode.OFFICIAL_APP,
                        enabled
                    ) {
                        viewModel.edit { it.copy(requestMode = RequestMode.OFFICIAL_APP) }
                    }
                }
            }
            OutlinedButton(
                onClick = viewModel::inspectDevice,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("实机属性检查（离线）", color = MaterialTheme.colorScheme.primary)
            }
            if (draft.requestMode == RequestMode.OFFICIAL_APP) {
                OfficialAppSettingsCard(
                    draft.officialApp,
                    viewModel.officialAppDefaults,
                    enabled
                ) { value ->
                    viewModel.edit { it.copy(officialApp = value) }
                }
            }
            if (draft.requestMode == RequestMode.WEB) {
                NetworkCard("拷贝地址") {
                    NetworkHint("填写拷贝域名或 HTTPS 地址。网页地址同时用于章节、Origin 和 Referer；请求协议保持 Web/H5。")
                    NetworkField(
                        draft.apiHost,
                        "默认 API 域名",
                        enabled
                    ) { value -> viewModel.edit { it.copy(apiHost = value) } }
                    NetworkField(
                        draft.wwwHost,
                        "网页域名",
                        enabled
                    ) { value -> viewModel.edit { it.copy(wwwHost = value) } }
                    NetworkField(
                        draft.loginHost,
                        "登录 API 域名（留空跟随 API）",
                        enabled
                    ) { value -> viewModel.edit { it.copy(loginHost = value) } }
                    NetworkField(
                        draft.version,
                        "网页请求版本",
                        enabled
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
                    if (draft.routes.isEmpty()) NetworkHint("尚未获取线路，可先使用默认 API。")
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
                            DateFormat.getDateTimeInstance().format(Date(draft.discoveredAt))
                        }"
                    )
                    NetworkHint("使用已保存的地址获取官方网络配置；先保存地址修改。失败时保留原线路，不影响默认 API。")
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
                    NetworkHint("启用后，距上次获取超过一天时尝试更新，不阻塞启动。")
                }
            }
            NetworkCard("代理") {
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
                    "代理地址，例如 127.0.0.1:7890",
                    enabled
                ) {
                    value -> viewModel.edit { it.copy(proxyAddress = value) }
                }
                NetworkHint("对接口、封面、阅读图片和下载统一生效。直连仅忽略 HTTP 代理，不会绕过系统 VPN。手动代理暂不支持账号密码。")
            }
            NetworkCard("测试当前填写配置") {
                NetworkHint("按当前填写的代理设置测量连接耗时；不验证登录或业务接口。最快项只在已发现节点中选择，保存后生效。")
                OutlinedButton(onClick = viewModel::test, enabled = enabled) {
                    Text("测试当前填写配置", color = MaterialTheme.colorScheme.primary)
                }
                state.probes.forEach { probe ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(probe.host, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Text(probe.latencyMillis?.let { "$it ms" } ?: "连接失败或超时",
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
            NetworkHint(if (state.dirty) "有未保存的修改" else "当前显示已保存配置")
            NetworkHint("应用配置会取消正在进行的网络请求。返回后请刷新页面，未完成的下载可在下载页继续。")
            Button(onClick = viewModel::save, enabled = enabled && state.dirty, modifier = Modifier.fillMaxWidth()) {
                Text("保存并应用", color = MaterialTheme.colorScheme.onPrimary)
            }
            OutlinedButton(onClick = viewModel::rollback, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("恢复上次配置", color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { reset = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("恢复默认", color = MaterialTheme.colorScheme.error)
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
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text("放弃未保存的修改？", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = { discard = false; viewModel.discard(); onNavigateBack() }) {
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
    if (reset) AlertDialog(onDismissRequest = { reset = false },
        title = { Text("恢复默认网络设置？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "恢复内置拷贝地址与系统代理，清除发现的线路；账号、阅读记录和下载会保留。",
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun NetworkHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NetworkField(
    value: String,
    label: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) })
}

@Composable
private fun NetworkChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier
        .fillMaxWidth()
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OfficialAppSettingsCard(
    value: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings,
    defaults: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings,
    enabled: Boolean,
    onChange: (com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings) -> Unit
) {
    NetworkCard("App 请求与设备标识") {
        NetworkHint("下方身份字段留空时使用默认值。SDK 标识首次随机生成后固定保存在本机；设备字段读取系统信息，buildTime 默认沿用 HAR 的 2025.05.22。填写后使用你的自定义值。")
        NetworkField(value.apiHost, "默认 API 域名", enabled) { onChange(value.copy(apiHost = it)) }
        NetworkField(value.loginHost, "登录 API 域名（留空跟随 API）", enabled) {
            onChange(
                value.copy(
                    loginHost = it
                )
            )
        }
        NetworkField(value.version, "App 协议版本", enabled) { onChange(value.copy(version = it)) }
        NetworkField(value.versionCode, "App versionCode", enabled) {
            onChange(
                value.copy(
                    versionCode = it
                )
            )
        }
        NetworkField(
            value.pseudoId,
            "pseudoID（SDK 标识）",
            enabled
        ) { onChange(value.copy(pseudoId = it)) }
        NetworkHint("留空使用：${defaults.pseudoId}")
        NetworkField(
            value.umString,
            "umString（SDK 标识）",
            enabled
        ) { onChange(value.copy(umString = it)) }
        NetworkHint("留空使用：${defaults.umString}")
        NetworkField(
            value.device,
            "设备 ID / androidId（系统构建号）",
            enabled
        ) { onChange(value.copy(device = it)) }
        NetworkHint("留空使用：${defaults.device}")
        NetworkField(value.deviceInfo, "deviceInfo（型号-设备代号）", enabled) {
            onChange(
                value.copy(
                    deviceInfo = it
                )
            )
        }
        NetworkHint("留空使用：${defaults.deviceInfo}")
        NetworkField(value.buildTime, "buildTime（可自定义）", enabled) {
            onChange(
                value.copy(
                    buildTime = it
                )
            )
        }
        NetworkHint("留空使用：${defaults.buildTime}")
        NetworkHint("App 模式所需配置随安装包提供；时间戳和签名在每次请求时生成。")
    }
}
