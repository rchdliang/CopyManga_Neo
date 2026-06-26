package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 我的页面内子页面
 */
private enum class ProfilePage {
    MAIN,
    HISTORY,
    SETTINGS
}

/**
 * 我的页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentPage by remember { mutableStateOf(ProfilePage.MAIN) }
    val toastHost = LocalToastHost.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    when (currentPage) {
        ProfilePage.MAIN -> ProfileMainPage(
            uiState = uiState,
            viewModel = viewModel,
            onNavigateToHistory = { currentPage = ProfilePage.HISTORY },
            onNavigateToSettings = { currentPage = ProfilePage.SETTINGS }
        )

        ProfilePage.HISTORY -> HistoryPage(
            onNavigateBack = { currentPage = ProfilePage.MAIN }
        )

        ProfilePage.SETTINGS -> SettingsPage(
            onNavigateBack = { currentPage = ProfilePage.MAIN }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileMainPage(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    if (uiState.isLoggedIn) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    AccountCard(
                        avatar = uiState.avatar,
                        username = uiState.username,
                        nickname = uiState.nickname,
                        dayDownloads = uiState.dayDownloads,
                        dayDownloadsRefresh = uiState.dayDownloadsRefresh,
                        ticket = uiState.ticket,
                        onLogout = { viewModel.logout() },
                        onLoginClick = { viewModel.showLoginDialog() }
                    )
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                item {
                    ProfileMenuItem(
                        icon = Icons.Filled.History,
                        label = "浏览记录",
                        onClick = onNavigateToHistory
                    )
                }
                item {
                    ProfileMenuItem(
                        icon = Icons.Filled.Settings,
                        label = "设置",
                        onClick = onNavigateToSettings
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                AccountCard(
                    avatar = null,
                    username = null,
                    nickname = null,
                    dayDownloads = null,
                    dayDownloadsRefresh = null,
                    ticket = null,
                    onLogout = {},
                    onLoginClick = { viewModel.showLoginDialog() }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Filled.Settings,
                    label = "设置",
                    onClick = onNavigateToSettings
                )
            }
        }
    }

    if (uiState.showLoginDialog) {
        LoginDialog(
            isLoading = uiState.isLoginLoading,
            error = uiState.loginError,
            savedUsername = uiState.savedUsername,
            savedPasswordProvider = { viewModel.getSavedPasswordSync() },
            rememberPassword = uiState.rememberPassword,
            autoLogin = uiState.autoLogin,
            onLogin = { username, password, remember, auto ->
                viewModel.login(username, password, remember, auto)
            },
            onDismiss = { viewModel.hideLoginDialog() }
        )
    }
}

@Composable
private fun AccountCard(
    avatar: String?,
    username: String?,
    nickname: String?,
    dayDownloads: Int?,
    dayDownloadsRefresh: String?,
    ticket: Float?,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit
) {
    val isLoggedIn = avatar != null || username != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        if (isLoggedIn) {
            AsyncImage(
                model = avatar,
                contentDescription = "用户头像",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                placeholder = rememberVectorPainter(Icons.Filled.AccountCircle),
                error = rememberVectorPainter(Icons.Filled.AccountCircle)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = nickname ?: username ?: "未知用户",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (username != null && nickname != null && nickname != username) {
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (dayDownloads != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "今日剩余下载: $dayDownloads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ticket != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "点券: $ticket",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (dayDownloadsRefresh != null) {
                Text(
                    text = "下次刷新: $dayDownloadsRefresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录")
            }
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "未登录",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "登录后可使用书架收藏等功能",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text("登录")
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 浏览记录页面
 */
@Composable
private fun HistoryPage(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无浏览记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "您阅览过的漫画将显示在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onNavigateBack) {
            Text("返回")
        }
    }
}

/**
 * 设置页面
 */
@Composable
private fun SettingsPage(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "更多设置选项即将推出",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onNavigateBack) {
            Text("返回")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginDialog(
    isLoading: Boolean,
    error: String?,
    savedUsername: String?,
    savedPasswordProvider: () -> String?,
    rememberPassword: Boolean,
    autoLogin: Boolean,
    onLogin: (String, String, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val usernameState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    var showPassword by remember { mutableStateOf(false) }
    var remember by remember { mutableStateOf(rememberPassword) }
    var auto by remember { mutableStateOf(autoLogin) }

    // 预填充已保存的凭据（用 LaunchedEffect 避免在组合阶段产生副作用）
    LaunchedEffect(Unit) {
        if (savedUsername != null && usernameState.text.isEmpty()) {
            usernameState.edit { replace(0, length, savedUsername) }
        }
        val savedPassword = savedPasswordProvider()
        if (savedPassword != null && passwordState.text.isEmpty()) {
            passwordState.edit { replace(0, length, savedPassword) }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("登录 CopyManga") },
        text = {
            Column {
                OutlinedTextField(
                    state = usernameState,
                    label = { Text("账号") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username },
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedSecureTextField(
                    state = passwordState,
                    label = { Text("密码") },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    textObfuscationMode = if (showPassword) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { remember = !remember },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = remember,
                        onCheckedChange = { remember = it }
                    )
                    Text(
                        text = "记住密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { auto = !auto },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = auto,
                        onCheckedChange = { auto = it }
                    )
                    Text(
                        text = "自动登录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onLogin(
                        usernameState.text.toString(),
                        passwordState.text.toString(),
                        remember,
                        auto
                    )
                },
                enabled = !isLoading && usernameState.text.toString()
                    .isNotBlank() && passwordState.text.toString().isNotBlank()
            ) {
                Text("登录")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}

private object ButtonDefaults {
    val IconSpacing = 8.dp
}

// region Previews

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 已登录")
@Composable
private fun ProfileLoggedInPreview() {
    CopyManga_NeoTheme {
        AccountCard(
            avatar = null,
            username = "user123",
            nickname = "旅行者",
            dayDownloads = 5,
            dayDownloadsRefresh = "2025-03-16 00:00:00",
            ticket = 100.5f,
            onLogout = {},
            onLoginClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 未登录")
@Composable
private fun ProfileNotLoggedInPreview() {
    CopyManga_NeoTheme {
        AccountCard(
            avatar = null,
            username = null,
            nickname = null,
            dayDownloads = null,
            dayDownloadsRefresh = null,
            ticket = null,
            onLogout = {},
            onLoginClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 浏览记录")
@Composable
private fun HistoryPagePreview() {
    CopyManga_NeoTheme {
        HistoryPage(onNavigateBack = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 设置")
@Composable
private fun SettingsPagePreview() {
    CopyManga_NeoTheme {
        SettingsPage(onNavigateBack = {})
    }
}

// endregion
