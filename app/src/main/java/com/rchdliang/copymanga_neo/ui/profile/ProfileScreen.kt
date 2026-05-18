package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/**
 * 账号页面
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoggedIn) {
            // 已登录状态
            LoggedInView(
                username = uiState.username,
                nickname = uiState.nickname,
                avatar = uiState.avatar,
                dayDownloads = uiState.dayDownloads,
                dayDownloadsRefresh = uiState.dayDownloadsRefresh,
                ticket = uiState.ticket,
                onLogout = { viewModel.logout() }
            )
        } else {
            // 未登录状态
            NotLoggedInView(
                onLoginClick = { viewModel.showLoginDialog() }
            )
        }
    }

    // 登录对话框
    if (uiState.showLoginDialog) {
        LoginDialog(
            isLoading = uiState.isLoginLoading,
            error = uiState.loginError,
            onLogin = { username, password -> viewModel.login(username, password) },
            onDismiss = { viewModel.hideLoginDialog() }
        )
    }
}

@Composable
private fun LoggedInView(
    avatar: String?,
    username: String?,
    nickname: String?,
    dayDownloads: Int?,
    dayDownloadsRefresh: String?,
    ticket: Float?,
    onLogout: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        AsyncImage(
            model = avatar,
            contentDescription = "用户头像",
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            placeholder = rememberVectorPainter(Icons.Filled.AccountCircle),
            error = rememberVectorPainter(Icons.Filled.AccountCircle)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = nickname ?: username ?: "未知用户",
            style = MaterialTheme.typography.headlineSmall
        )
        if (username != null && nickname != null && nickname != username) {
            Text(
                text = "@$username",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 显示下载次数
        if (dayDownloads != null) {
            Text(
                text = "今日剩余下载: $dayDownloads",
                style = MaterialTheme.typography.bodyMedium
            )
            if (dayDownloadsRefresh != null) {
                Text(
                    text = "下次刷新: $dayDownloadsRefresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (ticket != null) {
            Text(
                text = "点券: $ticket",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出登录")
        }
    }
}

@Composable
private fun NotLoggedInView(
    onLoginClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = "账号",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "未登录",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "登录后可使用书架收藏等功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginDialog(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val usernameState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    var showPassword by remember { mutableStateOf(false) }

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
                onClick = { onLogin(usernameState.text.toString(), passwordState.text.toString()) },
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