package com.rchdliang.copymanga_neo.ui.profile

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.history.HistoryScreen
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.drop

/**
 * 我的页面内子页面
 */
private enum class ProfilePage {
    MAIN,
    HISTORY,
    SETTINGS,
    BLACKLIST,
    NETWORK,
    ABOUT
}

/**
 * 我的页面
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit = {},
    onHistoryCleared: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentPage by rememberSaveable { mutableStateOf(ProfilePage.MAIN) }
    val toastHost = LocalToastHost.current
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }
    LaunchedEffect(viewModel, clipboardManager) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.CopyLoginCredential -> {
                    runCatching {
                        // getter 可空、setter 非空，Kotlin 将 primaryClip 映射为只读属性。
                        @Suppress("UsePropertyAccessSyntax")
                        clipboardManager.setPrimaryClip(createSensitiveCredentialClip(event.credential))
                    }.fold(
                        onSuccess = { toastHost.showToast("登录凭据已复制到剪切板") },
                        onFailure = { toastHost.showToast("无法写入剪切板") }
                    )
                }
            }
        }
    }

    BackHandler(enabled = currentPage != ProfilePage.MAIN) {
        currentPage = if (currentPage in setOf(
                ProfilePage.NETWORK,
                ProfilePage.ABOUT
            )
        ) ProfilePage.SETTINGS else ProfilePage.MAIN
    }

    when (currentPage) {
        ProfilePage.MAIN -> ProfileMainPage(
            uiState = uiState,
            onRefresh = viewModel::refresh,
            onLogout = viewModel::logout,
            onEditProfile = viewModel::showProfileEditor,
            onRetryProfile = viewModel::showProfileEditor,
            onSaveProfile = viewModel::saveProfile,
            onDismissProfile = viewModel::hideProfileEditor,
            onLoginClick = viewModel::showLoginDialog,
            savedPasswordProvider = viewModel::getSavedPasswordSync,
            onLogin = viewModel::login,
            onRegisterClick = viewModel::showRegisterDialog,
            onRegister = viewModel::register,
            onDismissRegister = viewModel::hideRegisterDialog,
            onClearRegisterError = viewModel::clearRegisterError,
            onDismissLogin = viewModel::hideLoginDialog,
            onClearLoginError = viewModel::clearLoginError,
            onExportCredential = viewModel::exportLoginCredential,
            onImportCredential = {
                val credential = runCatching {
                    clipboardManager.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                }.getOrNull()
                viewModel.importLoginCredential(credential.orEmpty())
            },
            onNavigateToHistory = { currentPage = ProfilePage.HISTORY },
            onNavigateToSettings = { currentPage = ProfilePage.SETTINGS },
            onNavigateToBlacklist = { currentPage = ProfilePage.BLACKLIST },
            onBlacklistEnabledChange = viewModel::setBlacklistEnabled
        )

        ProfilePage.HISTORY -> HistoryScreen(
            onNavigateBack = { currentPage = ProfilePage.MAIN },
            onComicClick = onComicClick,
            onHistoryCleared = onHistoryCleared,
            onLoginClick = {
                currentPage = ProfilePage.MAIN
                viewModel.showLoginDialog()
            }
        )

        ProfilePage.SETTINGS -> SettingsScreen(
            onNavigateBack = { currentPage = ProfilePage.MAIN },
            onNavigateToNetwork = { currentPage = ProfilePage.NETWORK },
            onNavigateToAbout = { currentPage = ProfilePage.ABOUT }
        )

        ProfilePage.ABOUT -> AboutScreen(onNavigateBack = { currentPage = ProfilePage.SETTINGS })

        ProfilePage.NETWORK -> NetworkSettingsScreen(onNavigateBack = {
            currentPage = ProfilePage.SETTINGS
        })

        ProfilePage.BLACKLIST -> ThemeBlacklistPage(
            uiState = uiState,
            onRemove = viewModel::removeBlockedTheme,
            onNavigateBack = { currentPage = ProfilePage.MAIN }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileMainPage(
    uiState: ProfileUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    savedPasswordProvider: () -> String?,
    onLogin: (String, String, Boolean, Boolean) -> Unit,
    onDismissLogin: () -> Unit,
    onExportCredential: () -> Unit,
    onImportCredential: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBlacklist: () -> Unit = {},
    onBlacklistEnabledChange: (Boolean) -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onRegister: (String, String, String) -> Unit = { _, _, _ -> },
    onDismissRegister: () -> Unit = {},
    onClearRegisterError: () -> Unit = {},
    onClearLoginError: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onRetryProfile: () -> Unit = {},
    onSaveProfile: (String, ByteArray?) -> Unit = { _, _ -> },
    onDismissProfile: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var showExportCredentialDialog by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoggedIn) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
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
                        onEditProfile = onEditProfile,
                        onLogout = onLogout,
                        onLoginClick = onLoginClick
                    )
                }
                item {
                    ProfileMenuGroup(
                        uiState = uiState,
                        onNavigateToHistory = onNavigateToHistory,
                        onCredentialClick = { showExportCredentialDialog = true },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToBlacklist = onNavigateToBlacklist,
                        onBlacklistEnabledChange = onBlacklistEnabledChange
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
                    onLogout = {},
                    onLoginClick = onLoginClick
                )
            }
            item {
                ProfileMenuGroup(
                    uiState = uiState,
                    onNavigateToHistory = onNavigateToHistory,
                    onCredentialClick = onImportCredential,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToBlacklist = onNavigateToBlacklist,
                    onBlacklistEnabledChange = onBlacklistEnabledChange
                )
            }
        }
    }

    if (uiState.showLoginDialog) {
        LoginDialog(
            isLoading = uiState.isLoginLoading,
            error = uiState.loginError,
            savedUsername = uiState.registeredUsername ?: uiState.savedUsername,
            savedPasswordProvider = if (uiState.registeredUsername == null) savedPasswordProvider else ({ null }),
            rememberPassword = uiState.rememberPassword,
            autoLogin = uiState.autoLogin,
            onLogin = { username, password, remember, auto ->
                onLogin(username, password, remember, auto)
            },
            onDismiss = onDismissLogin,
            onRegister = onRegisterClick,
            onClearError = onClearLoginError
        )
    }

    if (uiState.showRegisterDialog) {
        RegisterDialog(
            isLoading = uiState.isRegisterLoading,
            error = uiState.registerError,
            onRegister = onRegister,
            onDismiss = onDismissRegister,
            onLogin = onLoginClick,
            onClearError = onClearRegisterError
        )
    }

    if (uiState.showProfileEditor) ProfileEditDialog(
        profile = uiState.editableProfile,
        isLoading = uiState.isProfileLoading,
        isSaving = uiState.isProfileSaving,
        error = uiState.profileEditError,
        onRetry = onRetryProfile,
        onSave = onSaveProfile,
        onDismiss = onDismissProfile
    )

    if (showExportCredentialDialog) {
        AlertDialog(
            onDismissRequest = { showExportCredentialDialog = false },
            title = {
                Text(
                    text = "导出登录凭据？",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "登录凭据等同于账号登录权限。复制后请妥善保管，不要发送给不信任的人。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportCredentialDialog = false
                        onExportCredential()
                    }
                ) {
                    Text(
                        text = "复制",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportCredentialDialog = false }) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@Composable
private fun AccountCard(
    avatar: String?,
    username: String?,
    nickname: String?,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    onEditProfile: () -> Unit = {}
) {
    val isLoggedIn = avatar != null || username != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        if (isLoggedIn) {
            PreviewAwareAsyncImage(
                model = avatar,
                previewDrawable = R.drawable.preview_avatar,
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


            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text("编辑昵称与头像", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.primary)
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

/** 登录前后的菜单共用分组样式，黑名单不依赖账号。 */
@Composable
private fun ProfileMenuGroup(
    uiState: ProfileUiState,
    onNavigateToHistory: () -> Unit,
    onCredentialClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBlacklist: () -> Unit,
    onBlacklistEnabledChange: (Boolean) -> Unit
) {
    val entries = buildList {
        if (uiState.isLoggedIn) {
            add(
                ProfileMenuEntry(
                    Icons.Filled.History,
                    "浏览记录",
                    "查看最近阅读的漫画",
                    onClick = onNavigateToHistory
                )
            )
        }
        add(
            ProfileMenuEntry(
                icon = if (uiState.isLoggedIn) Icons.Outlined.Key else Icons.Outlined.ContentPaste,
                label = if (uiState.isLoggedIn) "导出登录凭据" else "导入登录凭据",
                supportingText = if (uiState.isCredentialLoading) "正在处理，请稍候"
                else if (uiState.isLoggedIn) "复制当前账号的登录凭据" else "从剪切板导入并验证账号",
                enabled = !uiState.isCredentialLoading,
                onClick = onCredentialClick
            )
        )
        add(
            ProfileMenuEntry(
                Icons.Outlined.Block,
                "标签黑名单（实验性功能）",
                when {
                    uiState.isBlacklistLoading -> "正在读取标签"
                    uiState.blacklistError != null -> "读取失败，点击查看"
                    !uiState.isBlacklistEnabled -> "已停用，保留 ${uiState.blockedThemes.size} 个标签"
                    else -> "已屏蔽 ${uiState.blockedThemes.size} 个标签"
                },
                onClick = onNavigateToBlacklist,
                trailingContent = {
                    Switch(
                        checked = uiState.isBlacklistEnabled,
                        onCheckedChange = onBlacklistEnabledChange,
                        enabled = !uiState.isBlacklistLoading && !uiState.isBlacklistSaving &&
                                uiState.blacklistError == null,
                        modifier = Modifier.semantics { contentDescription = "启用标签黑名单" }
                    )
                }
            )
        )
        add(
            ProfileMenuEntry(
                Icons.Filled.Settings,
                "设置",
                "应用偏好设置",
                onClick = onNavigateToSettings
            )
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)
    ) {
        entries.forEachIndexed { index, entry ->
            ProfileListItem(
                icon = entry.icon,
                label = entry.label,
                supportingText = entry.supportingText,
                index = index,
                count = entries.size,
                enabled = entry.enabled,
                onClick = entry.onClick,
                trailingContent = entry.trailingContent
            )
        }
    }
}

private data class ProfileMenuEntry(
    val icon: ImageVector,
    val label: String,
    val supportingText: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val trailingContent: (@Composable () -> Unit)? = null
)

/** 创建标记为敏感内容的剪切板数据，避免系统在剪切板预览中显示 token。 */
private fun createSensitiveCredentialClip(credential: String): ClipData {
    return ClipData.newPlainText("CopyManga Neo 登录凭据", credential).apply {
        description.extras = PersistableBundle().apply {
            val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(sensitiveKey, true)
        }
    }
}

/** 登录表单只校验必填项，保留已有账号的兼容性。 */
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
    onDismiss: () -> Unit,
    onRegister: () -> Unit = {},
    onClearError: () -> Unit = {}
) {
    val usernameState = rememberTextFieldState()
    val passwordState = remember { TextFieldState() }
    var showPassword by remember { mutableStateOf(false) }
    var rememberPasswordChecked by remember { mutableStateOf(rememberPassword || autoLogin) }
    var auto by remember { mutableStateOf(autoLogin) }
    var submitted by remember { mutableStateOf(false) }
    val username = usernameState.text.toString().trim()
    val password = passwordState.text.toString()
    val usernameError = if (submitted && username.isBlank()) "请输入账号" else null
    val passwordError = if (submitted && password.isBlank()) "请输入密码" else null

    LaunchedEffect(Unit) {
        if (savedUsername != null && usernameState.text.isEmpty()) {
            usernameState.edit { replace(0, length, savedUsername) }
        }
        val savedPassword = savedPasswordProvider()
        if (savedPassword != null && passwordState.text.isEmpty()) {
            passwordState.edit { replace(0, length, savedPassword) }
        }
        // 预填后再监听编辑，保留注册成功但登录失败的初始提示。
        snapshotFlow { usernameState.text.toString() to passwordState.text.toString() }
            .drop(1).collect { onClearError() }
    }
    fun submit() {
        submitted = true
        if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
            onLogin(username, password, rememberPasswordChecked, auto)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                Icons.AutoMirrored.Outlined.Login, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("登录账号", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "登录 CopyManga，同步书架和阅读记录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    state = usernameState,
                    label = {
                        Text(
                            "账号", color = if (usernameError != null)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = if (usernameError == null) null else {
                        { Text(usernameError, color = MaterialTheme.colorScheme.error) }
                    },
                    isError = usernameError != null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username },
                    enabled = !isLoading
                )
                OutlinedSecureTextField(
                    state = passwordState,
                    label = {
                        Text(
                            "密码", color = if (passwordError != null)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = if (passwordError == null) null else {
                        { Text(passwordError, color = MaterialTheme.colorScheme.error) }
                    },
                    isError = passwordError != null,
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            enabled = !isLoading
                        ) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    textObfuscationMode = if (showPassword) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = { submit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                    enabled = !isLoading
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        LoginPreferenceRow(
                            "记住密码", "下次登录时填入账号和密码",
                            rememberPasswordChecked, !isLoading
                        ) {
                            rememberPasswordChecked = it
                            if (!it) auto = false
                        }
                        LoginPreferenceRow(
                            "自动登录", "启动或会话失效时尝试登录",
                            auto, !isLoading
                        ) {
                            auto = it
                            if (it) rememberPasswordChecked = true
                        }
                    }
                }
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            it, Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) LoadingIndicator(Modifier.size(24.dp))
                    Text(
                        if (isLoading) "正在登录" else "登录",
                        color = if (isLoading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRegister, enabled = !isLoading) {
                    Text("注册账号", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

/** 整行可切换，Checkbox 不单独消费点击，避免重复触发及重复无障碍节点。 */
@Composable
private fun LoginPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked, enabled = enabled, role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private object ButtonDefaults {
    val IconSpacing = 8.dp
}

// region Previews

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 已登录")
@Composable
private fun ProfileLoggedInPreview() {
    CopyManga_NeoTheme {
        ProfileMainPage(
            uiState = ProfileUiState(
                isLoggedIn = true,
                avatar = "https://example.com/avatar.png",
                username = "user123",
                nickname = "旅行者",
                dayDownloads = 5,
                dayDownloadsRefresh = "2026-08-21 00:00",
                ticket = 100.5f
            ),
            onRefresh = {},
            onLogout = {},
            onLoginClick = {},
            savedPasswordProvider = { null },
            onLogin = { _, _, _, _ -> },
            onDismissLogin = {},
            onExportCredential = {},
            onImportCredential = {},
            onNavigateToHistory = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 未登录")
@Composable
private fun ProfileNotLoggedInPreview() {
    CopyManga_NeoTheme {
        ProfileMainPage(
            uiState = ProfileUiState(isLoggedIn = false),
            onRefresh = {},
            onLogout = {},
            onLoginClick = {},
            savedPasswordProvider = { null },
            onLogin = { _, _, _, _ -> },
            onDismissLogin = {},
            onExportCredential = {},
            onImportCredential = {},
            onNavigateToHistory = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "我的 - 登录对话框")
@Composable
private fun LoginDialogPreview() {
    CopyManga_NeoTheme {
        ProfileMainPage(
            uiState = ProfileUiState(
                isLoggedIn = false,
                showLoginDialog = true,
                savedUsername = "preview_user",
                rememberPassword = true,
                loginError = "示例：账号或密码错误"
            ),
            onRefresh = {},
            onLogout = {},
            onLoginClick = {},
            savedPasswordProvider = { "preview_password" },
            onLogin = { _, _, _, _ -> },
            onDismissLogin = {},
            onExportCredential = {},
            onImportCredential = {},
            onNavigateToHistory = {},
            onNavigateToSettings = {}
        )
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
