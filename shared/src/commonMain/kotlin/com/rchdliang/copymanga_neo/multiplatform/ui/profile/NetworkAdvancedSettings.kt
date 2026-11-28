package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.NetworkDefaults
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.multiplatform.ui.components.formattedReadingDate

@Composable
internal fun NetworkAdvancedSettings(
    state: NetworkSettingsUiState,
    viewModel: NetworkSettingsViewModel,
    onRollback: () -> Unit,
    onReset: () -> Unit
) {
    val enabled = !state.busy
    val official = state.draft.requestMode == RequestMode.OFFICIAL_APP
    NetworkColumns(
        primary = {
            if (official) OfficialAddressPanel(state, viewModel) else WebAddressPanel(state, viewModel)
        },
        secondary = {
            if (official) DeviceIdentityPanel(state, viewModel) else WebRoutesPanel(state, viewModel)
            NetworkPanel("配置管理", Icons.Outlined.Settings) {
                if (viewModel.canChooseRequestMode) {
                    OutlinedButton(onClick = viewModel::inspectDevice, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text("查看已应用设备信息")
                    }
                }
                OutlinedButton(onClick = onRollback, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("载入上次配置")
                }
                TextButton(onClick = onReset, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("载入默认配置")
                }
            }
        }
    )
}

@Composable
private fun OfficialAddressPanel(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    val value = state.draft.officialApp
    val defaults = viewModel.officialAppDefaults
    val enabled = !state.busy

    NetworkPanel("服务地址与版本", Icons.Outlined.Dns) {
        NetworkField(value.apiHost, "API 域名", enabled,
            onReset = { viewModel.editApp { it.copy(apiHost = defaults.apiHost) } }) { host -> viewModel.editApp { it.copy(apiHost = host) } }
        NetworkField(value.loginHost, "登录域名", enabled,
            onReset = { viewModel.editApp { it.copy(loginHost = defaults.loginHost) } }) { host -> viewModel.editApp { it.copy(loginHost = host) } }
        NetworkField(value.version, "App 版本", enabled,
            onReset = { viewModel.editApp { it.copy(version = defaults.version) } }) { version -> viewModel.editApp { it.copy(version = version) } }
        NetworkField(value.versionCode, "版本号", enabled,
            onReset = { viewModel.editApp { it.copy(versionCode = defaults.versionCode) } }) { code -> viewModel.editApp { it.copy(versionCode = code) } }
    }
}

private fun NetworkSettingsViewModel.editApp(transform: (OfficialAppSettings) -> OfficialAppSettings) =
    edit { it.copy(officialApp = transform(it.officialApp)) }

@Composable
private fun DeviceIdentityPanel(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    val value = state.draft.officialApp
    val defaults = viewModel.officialAppDefaults
    val enabled = !state.busy
    val hasInvalidIdentity = networkFieldError("SDK 标识", value.pseudoId) != null ||
        networkFieldError("统计标识", value.umString) != null ||
        networkFieldError("设备构建号", value.device) != null ||
        networkFieldError("设备型号", value.deviceInfo) != null ||
        networkFieldError("构建日期", value.buildTime) != null
    var expanded by rememberSaveable { mutableStateOf(hasInvalidIdentity) }
    LaunchedEffect(hasInvalidIdentity) { if (hasInvalidIdentity) expanded = true }
    NetworkPanel("设备身份", Icons.Outlined.Devices, action = {
        if (!hasInvalidIdentity) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起设备字段" else "编辑设备字段")
            }
        }
    }) {
        if (expanded || hasInvalidIdentity) {
            NetworkHint("修改后可能需要重新登录。")
            NetworkField(value.pseudoId, "SDK 标识", enabled, onRandomize = viewModel::randomizePseudoId) { id ->
                viewModel.editApp { it.copy(pseudoId = id) }
            }
            NetworkField(value.umString, "统计标识", enabled, onRandomize = viewModel::randomizeUmString) { id ->
                viewModel.editApp { it.copy(umString = id) }
            }
            NetworkField(value.device, "设备构建号", enabled,
                onReset = { viewModel.editApp { it.copy(device = defaults.device) } }) { device -> viewModel.editApp { it.copy(device = device) } }
            NetworkField(value.deviceInfo, "设备型号", enabled,
                onReset = { viewModel.editApp { it.copy(deviceInfo = defaults.deviceInfo) } }) { model -> viewModel.editApp { it.copy(deviceInfo = model) } }
            NetworkField(value.buildTime, "构建日期", enabled,
                onReset = { viewModel.editApp { it.copy(buildTime = defaults.buildTime) } }) { time -> viewModel.editApp { it.copy(buildTime = time) } }
        }
    }
}

@Composable
private fun WebAddressPanel(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    val draft = state.draft
    val enabled = !state.busy
    NetworkPanel("服务地址与版本", Icons.Outlined.Dns) {
        NetworkField(draft.apiHost, "API 域名", enabled,
            onReset = { viewModel.edit { it.copy(apiHost = NetworkDefaults.API_HOST) } }) { host ->
            viewModel.edit { it.copy(apiHost = host) }
        }
        NetworkField(draft.wwwHost, "网页域名", enabled,
            onReset = { viewModel.edit { it.copy(wwwHost = NetworkDefaults.WWW_HOST) } }) { host ->
            viewModel.edit { it.copy(wwwHost = host) }
        }
        NetworkField(draft.loginHost, "登录域名（留空跟随 API）", enabled,
            onReset = { viewModel.edit { it.copy(loginHost = "") } }) { host ->
            viewModel.edit { it.copy(loginHost = host) }
        }
        NetworkField(draft.version, "请求版本", enabled,
            onReset = { viewModel.edit { it.copy(version = NetworkDefaults.WEB_VERSION) } }) { version ->
            viewModel.edit { it.copy(version = version) }
        }
    }
}

@Composable
private fun WebRoutesPanel(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    val draft = state.draft
    val enabled = !state.busy
    var showNodes by rememberSaveable { mutableStateOf(false) }
    NetworkPanel("线路与节点", Icons.Outlined.Wifi) {
        Column(Modifier.selectableGroup()) {
            NetworkSelection.entries.forEach { mode ->
                val label = when (mode) {
                    NetworkSelection.DEFAULT -> "默认 API"
                    NetworkSelection.ROUTE -> "线路内轮询"
                    NetworkSelection.FIXED -> "固定节点"
                }
                NetworkChoice(label, draft.selection == mode, enabled && (mode == NetworkSelection.DEFAULT || draft.routes.isNotEmpty())) {
                    viewModel.edit {
                        it.copy(selection = mode, fixedHost = it.fixedHost.ifBlank { it.routes.flatten().firstOrNull().orEmpty() })
                    }
                    showNodes = mode != NetworkSelection.DEFAULT
                }
            }
        }
        if (draft.routes.isEmpty()) {
            NetworkHint("暂无线路")
        } else {
            val summary = when (draft.selection) {
                NetworkSelection.DEFAULT -> "查看 ${draft.routes.size} 条线路"
                NetworkSelection.ROUTE -> "线路 ${draft.routeIndex + 1}"
                NetworkSelection.FIXED -> draft.fixedHost
            }
            FieldDisclosure(showNodes, summary, "收起线路列表") { showNodes = !showNodes }
            if (showNodes) {
                Column(Modifier.selectableGroup()) {
                    draft.routes.forEachIndexed { index, nodes ->
                        if (draft.selection == NetworkSelection.ROUTE) {
                            NetworkChoice("线路 ${index + 1}", draft.routeIndex == index, enabled) {
                                viewModel.edit { it.copy(routeIndex = index) }
                            }
                        } else {
                            Text("线路 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        }
                        nodes.forEach { host ->
                            if (draft.selection == NetworkSelection.FIXED) {
                                NetworkChoice(host, draft.fixedHost == host, enabled) { viewModel.edit { it.copy(fixedHost = host) } }
                            } else {
                                NetworkHint(host)
                            }
                        }
                    }
                }
            }
        }
        if (draft.discoveredAt > 0) NetworkHint("上次获取：${formattedReadingDate(draft.discoveredAt)}")
        if (state.dirty) NetworkHint("保存后可获取线路")
        OutlinedButton(onClick = viewModel::discover, enabled = enabled && !state.dirty) { Text("获取线路") }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = draft.autoDiscover, enabled = enabled, role = Role.Switch,
                onValueChange = { checked -> viewModel.edit { it.copy(autoDiscover = checked) } }
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("启动时检查线路更新", Modifier.weight(1f))
            Switch(checked = draft.autoDiscover, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun FieldDisclosure(expanded: Boolean, label: String, collapseLabel: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) collapseLabel else label, Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
    }
}
