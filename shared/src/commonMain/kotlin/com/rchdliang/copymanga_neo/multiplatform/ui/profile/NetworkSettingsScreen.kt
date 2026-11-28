package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UnsavedChangesDialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.verticalScrollWithMouse

@Composable
internal fun NetworkSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkSettingsViewModel,
    advanced: Boolean = false,
    onOpenAdvanced: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = !state.busy
    val toast = LocalToastHost.current
    val active = LocalRouteActive.current
    var routePresent by remember { mutableStateOf(active) }
    DisposableEffect(viewModel, active) { routePresent = active; onDispose { routePresent = false; if (active) viewModel.cancelWork() } }
    var discard by rememberSaveable { mutableStateOf(false) }
    var showPreload by rememberSaveable { mutableStateOf(false) }
    var rollback by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val goBack = {
        if (!state.busy) {
            if (!advanced && state.dirty) discard = true else onNavigateBack()
        }
    }
    BackHandler(enabled = state.busy || (!advanced && state.dirty)) { goBack() }
    ActiveRouteLaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (advanced) "高级连接配置" else "网络与连接") },
            navigationIcon = {
                IconButton(onClick = goBack, enabled = enabled) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        if (state.busy && !state.testing) LinearProgressIndicator(Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= NetworkWideWidth
            Column(
                Modifier.widthIn(max = if (wide) 1080.dp else 720.dp).fillMaxWidth()
                    .verticalScrollWithMouse(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (advanced) {
                    NetworkAdvancedSettings(state, viewModel, onRollback = { rollback = true }, onReset = { reset = true })
                } else {
                    AppliedConnection(state.saved, Modifier.padding(horizontal = if (wide) 24.dp else 16.dp), wide, state.dirty)
                    NetworkColumns(
                        primary = {
                            ConnectionChoices(state, viewModel)
                            ProfileListItem(
                                Icons.Outlined.Speed, "漫画详情预加载",
                                "并发 ${state.draft.themeFetchConcurrency} 部 · 间隔 ${state.draft.themeFetchIntervalMillis / 1000.0} 秒",
                                index = 0, count = 1, enabled = enabled, onClick = { showPreload = true }
                            )
                        },
                        secondary = {
                            NetworkDiagnostics(state, viewModel)
                            ProfileListItem(
                                Icons.Outlined.Tune, "高级连接配置", null,
                                index = 0, count = 1, enabled = enabled, onClick = onOpenAdvanced
                            )
                        }
                    )
                }
            }
        }
        if (state.dirty || state.saving || state.error != null) {
            NetworkSaveBar(state) { viewModel.save() }
        }
    }
    if (advanced && active) state.diagnosticReport?.let { report ->
        OfficialAppDiagnosticsDialog(
            report,
            state.busy,
            viewModel::inspectDevice,
            viewModel::closeDiagnosticReport
        )
    }
    if (discard && active) UnsavedChangesDialog(
        busy = state.busy,
        onContinue = { discard = false },
        onDiscard = { discard = false; viewModel.discard(); onNavigateBack() },
        onSave = { discard = false; viewModel.save { if (routePresent) onNavigateBack() } }
    ) {
        val changes = remember(state.saved, state.draft) { networkChangeSummary(state.saved, state.draft) }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                changes.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, Modifier.weight(1.4f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
    if (rollback && active) AlertDialog(
        onDismissRequest = { rollback = false }, title = { Text("载入上次配置？") },
        text = { Text("替换当前草稿，保存后生效。") },
        confirmButton = { TextButton(onClick = { rollback = false; viewModel.rollback() }, enabled = enabled) { Text("载入") } },
        dismissButton = { TextButton(onClick = { rollback = false }) { Text("取消") } }
    )
    if (showPreload && active) AlertDialog(
        onDismissRequest = { showPreload = false },
        title = { Text("漫画详情预加载") },
        text = {
            Column(Modifier.verticalScrollWithMouse(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeLoadingControls(state.draft, enabled,
                    onConcurrency = { count -> viewModel.edit { it.copy(themeFetchConcurrency = count) } },
                    onInterval = { interval -> viewModel.edit { it.copy(themeFetchIntervalMillis = interval) } }
                )
            }
        },
        confirmButton = { TextButton(onClick = { showPreload = false }) { Text("完成") } }
    )
    if (reset && active) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text("恢复默认网络设置？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                if (state.saved.officialApp != viewModel.officialAppDefaults) "保存后生效，可能需要重新登录 App 模式。" else "保存后生效。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = {
                reset = false; viewModel.reset()
            }) { Text("载入默认配置") }
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
private fun AppliedConnection(settings: NetworkSettings, modifier: Modifier, wide: Boolean, showMode: Boolean) {
    val target = when {
        settings.requestMode == RequestMode.OFFICIAL_APP -> settings.officialApp.apiHost
        settings.selection == NetworkSelection.FIXED -> settings.fixedHost
        settings.selection == NetworkSelection.ROUTE -> "线路 ${settings.routeIndex + 1}"
        else -> settings.apiHost
    }
    val proxy = when (settings.proxyMode) {
        ProxyMode.SYSTEM -> "跟随系统代理"
        ProxyMode.DIRECT -> "直连"
        ProxyMode.MANUAL -> "手动 HTTP 代理"
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                Icon(Icons.Outlined.Router, null, Modifier.padding(11.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("已应用配置", style = MaterialTheme.typography.labelMedium)
                Text(target, style = MaterialTheme.typography.titleLarge)
                if (showMode && !wide) Text("${settings.requestMode.profileLabel} · $proxy", style = MaterialTheme.typography.bodyMedium)
            }
            if (showMode && wide) {
                Text("${settings.requestMode.profileLabel} · $proxy", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ConnectionChoices(state: NetworkSettingsUiState, viewModel: NetworkSettingsViewModel) {
    val draft = state.draft
    val enabled = !state.busy
    val modes = LocalUiPlatform.current?.supportedProxyModes ?: ProxyMode.entries
    NetworkPanel("连接方式", Icons.Outlined.Wifi) {
        if (viewModel.canChooseRequestMode) {
            NetworkOptions("请求模式", listOf(RequestMode.OFFICIAL_APP, RequestMode.WEB), draft.requestMode, enabled,
                label = { it.profileLabel }) { mode -> viewModel.edit { it.copy(requestMode = mode) } }
            HorizontalDivider()
        }
        if (modes.size > 1 || draft.proxyMode !in modes) {
            NetworkOptions("代理", modes, draft.proxyMode, enabled, label = {
                when (it) { ProxyMode.SYSTEM -> "跟随系统"; ProxyMode.DIRECT -> "直连"; ProxyMode.MANUAL -> "手动" }
            }) { mode -> viewModel.edit { it.copy(proxyMode = mode) } }
            if (draft.proxyMode !in modes) NetworkHint("此平台不支持当前代理方式。")
        } else {
            Text("代理", style = MaterialTheme.typography.labelLarge)
            NetworkHint("由浏览器管理")
        }
        if (draft.proxyMode == ProxyMode.MANUAL && ProxyMode.MANUAL in modes) {
            NetworkField(draft.proxyAddress, "代理地址", enabled) { address -> viewModel.edit { it.copy(proxyAddress = address) } }
        }
    }
}

@Composable
private fun NetworkSaveBar(state: NetworkSettingsUiState, save: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= NetworkWideWidth
            Column(Modifier.widthIn(max = if (wide) 1080.dp else 720.dp).fillMaxWidth()
                .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { message ->
                    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (state.dirty || state.saving) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.saving) "正在保存更改" else "有未保存的更改",
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Button(onClick = save, enabled = !state.busy && state.dirty) {
                            Text(if (state.saving) "保存中…" else "保存")
                        }
                    }
                }
            }
        }
    }
}
