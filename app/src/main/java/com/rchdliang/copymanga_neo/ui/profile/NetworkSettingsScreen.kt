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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
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
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.network_title), color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = { IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.network_back)) } },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NetworkCard(stringResource(R.string.network_endpoints)) {
                NetworkHint(stringResource(R.string.network_endpoint_hint))
                NetworkField(draft.apiHost, R.string.network_api_host, enabled) { value -> viewModel.edit { it.copy(apiHost = value) } }
                NetworkField(draft.wwwHost, R.string.network_www_host, enabled) { value -> viewModel.edit { it.copy(wwwHost = value) } }
                NetworkField(draft.loginHost, R.string.network_login_host, enabled) { value -> viewModel.edit { it.copy(loginHost = value) } }
                NetworkField(draft.version, R.string.network_version, enabled) { value -> viewModel.edit { it.copy(version = value) } }
            }
            NetworkCard(stringResource(R.string.network_routes)) {
                Column(Modifier.selectableGroup()) {
                    NetworkSelection.entries.forEach { mode ->
                        val label = when (mode) {
                            NetworkSelection.DEFAULT -> R.string.network_default
                            NetworkSelection.ROUTE -> R.string.network_route
                            NetworkSelection.FIXED -> R.string.network_fixed
                        }
                        NetworkChoice(stringResource(label), draft.selection == mode,
                            enabled && (mode == NetworkSelection.DEFAULT || draft.routes.isNotEmpty())) {
                            viewModel.edit { it.copy(selection = mode, fixedHost = it.fixedHost.ifBlank { it.routes.flatten().firstOrNull().orEmpty() }) }
                        }
                    }
                }
                if (draft.routes.isEmpty()) NetworkHint(stringResource(R.string.network_no_routes))
                Column(Modifier.selectableGroup()) {
                    draft.routes.forEachIndexed { index, nodes ->
                        if (draft.selection == NetworkSelection.ROUTE) {
                            NetworkChoice(stringResource(R.string.network_route_name, index + 1), draft.routeIndex == index, enabled) {
                                viewModel.edit { it.copy(routeIndex = index) }
                            }
                        } else NetworkHint(stringResource(R.string.network_route_name, index + 1))
                        nodes.forEach { host ->
                            if (draft.selection == NetworkSelection.FIXED) {
                                NetworkChoice(host, draft.fixedHost == host, enabled) { viewModel.edit { it.copy(fixedHost = host) } }
                            } else NetworkHint(host)
                        }
                    }
                }
                if (draft.discoveredAt > 0) NetworkHint(stringResource(R.string.network_discovered_at, DateFormat.getDateTimeInstance().format(Date(draft.discoveredAt))))
                NetworkHint(stringResource(R.string.network_discovery_hint))
                OutlinedButton(onClick = viewModel::discover, enabled = enabled && !state.dirty) {
                    Text(stringResource(R.string.network_discover), color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.network_auto_discover), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(draft.autoDiscover, { value -> viewModel.edit { it.copy(autoDiscover = value) } }, enabled = enabled)
                }
                NetworkHint(stringResource(R.string.network_auto_hint))
            }
            NetworkCard(stringResource(R.string.network_proxy)) {
                Column(Modifier.selectableGroup()) {
                    ProxyMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ProxyMode.SYSTEM -> R.string.network_proxy_system
                            ProxyMode.MANUAL -> R.string.network_proxy_manual
                            ProxyMode.DIRECT -> R.string.network_proxy_direct
                        }
                        NetworkChoice(stringResource(label), draft.proxyMode == mode, enabled) { viewModel.edit { it.copy(proxyMode = mode) } }
                    }
                }
                if (draft.proxyMode == ProxyMode.MANUAL) NetworkField(draft.proxyAddress, R.string.network_proxy_address, enabled) {
                    value -> viewModel.edit { it.copy(proxyAddress = value) }
                }
                NetworkHint(stringResource(R.string.network_proxy_hint))
            }
            NetworkCard(stringResource(R.string.network_test)) {
                NetworkHint(stringResource(R.string.network_probe_hint))
                OutlinedButton(onClick = viewModel::test, enabled = enabled) {
                    Text(stringResource(R.string.network_test), color = MaterialTheme.colorScheme.primary)
                }
                state.probes.forEach { probe ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(probe.host, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Text(probe.latencyMillis?.let { stringResource(R.string.network_latency, it) } ?: stringResource(R.string.network_timeout),
                            color = if (probe.latencyMillis == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
                if (state.probes.isNotEmpty() && draft.routes.isNotEmpty()) TextButton(onClick = viewModel::chooseFastest, enabled = enabled) {
                    Text(stringResource(R.string.network_fastest), color = MaterialTheme.colorScheme.primary)
                }
            }
            NetworkHint(stringResource(if (state.dirty) R.string.network_pending else R.string.network_saved))
            NetworkHint(stringResource(R.string.network_apply_hint))
            Button(onClick = viewModel::save, enabled = enabled && state.dirty, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.network_save), color = MaterialTheme.colorScheme.onPrimary)
            }
            OutlinedButton(onClick = viewModel::rollback, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.network_rollback), color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { reset = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.network_reset), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(stringResource(R.string.network_discard_title), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = { discard = false; viewModel.discard(); onNavigateBack() }) {
            Text(stringResource(R.string.network_discard), color = MaterialTheme.colorScheme.primary)
        } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.network_cancel), color = MaterialTheme.colorScheme.primary) } })
    if (reset) AlertDialog(onDismissRequest = { reset = false },
        title = { Text(stringResource(R.string.network_reset_title), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(stringResource(R.string.network_reset_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = { reset = false; viewModel.reset() }) { Text(stringResource(R.string.network_reset), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.network_cancel), color = MaterialTheme.colorScheme.primary) } })
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
private fun NetworkField(value: String, label: Int, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        label = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant) })
}

@Composable
private fun NetworkChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}
