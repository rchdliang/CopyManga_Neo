package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.preview_avatar
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.history.HistoryScreen
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.drop

/**
 * 我的页面内子页面
 */
private enum class ProfilePage {
    MAIN,
    HISTORY,
    READING,
    DOWNLOADS,
    BLACKLIST,
    NETWORK,
    ABOUT,
    DATA,
    LOCAL_LIBRARY
}

/**
 * 我的页面
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    services: ProfileUiServices,
    onNavigateToDownloads: () -> Unit,
    onComicClick: (String) -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    onReadComic: (String, String, String, String, Int, Double) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToNovel: (String?, com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord?) -> Unit = { _, _ -> }
) {
    CompositionLocalProvider(
        LocalProfilePlatform provides services.platform,
        LocalAppBuildInfo provides services.appBuildInfo
    ) {
        ProfileScreenContent(
            viewModel, services, onNavigateToDownloads, onComicClick,
            onHistoryCleared, onReadComic, onNavigateToNovel
        )
    }
}

@Composable
private fun ProfileScreenContent(
    viewModel: ProfileViewModel,
    services: ProfileUiServices,
    onNavigateToDownloads: () -> Unit,
    onComicClick: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onReadComic: (String, String, String, String, Int, Double) -> Unit,
    onNavigateToNovel: (String?, com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord?) -> Unit
) {
    ActiveRouteLaunchedEffect(viewModel) { viewModel.start() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentPage by rememberSaveable { mutableStateOf(ProfilePage.MAIN) }
    val toastHost = LocalToastHost.current
    val platform = requireProfilePlatform()
    val scope = rememberCoroutineScope()

    ActiveRouteLaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }
    ActiveRouteLaunchedEffect(viewModel, platform) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.CopyLoginCredential -> {
                    credentialCopyFeedback(
                        platform.copySensitiveText(
                            "CopyManga Neo 登录凭据",
                            event.credential
                        )
                    )
                        ?.let { toastHost.showToast(it) }
                }
            }
        }
    }

    val pageStateHolder = rememberSaveableStateHolder()
    val openSettings: (SettingsDestination) -> Unit = {
        currentPage = when (it) {
            SettingsDestination.OVERVIEW -> ProfilePage.MAIN
            SettingsDestination.READING -> ProfilePage.READING
            SettingsDestination.DOWNLOADS -> ProfilePage.DOWNLOADS
        }
    }
    BackHandler(enabled = currentPage != ProfilePage.MAIN) {
        currentPage =
            if (currentPage == ProfilePage.HISTORY) ProfilePage.LOCAL_LIBRARY else ProfilePage.MAIN
    }
    pageStateHolder.SaveableStateProvider(currentPage.name) {
        when (currentPage) {
            ProfilePage.MAIN -> SettingsScreen(
                viewModel = services.settings, onManageDownloads = onNavigateToDownloads,
                onNavigateBack = { currentPage = ProfilePage.MAIN },
                onNavigateToData = { currentPage = ProfilePage.DATA },
                onNavigateToAbout = { currentPage = ProfilePage.ABOUT },
                embedded = true, onOpenDestination = openSettings,
                overviewLayout = { appPreferences ->
                    ProfileMainPage(
                        uiState = uiState,
                        onRefresh = viewModel::refresh,
                        onLogout = viewModel::logout,
                        avatarViewModel = services.avatar,
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
                        onExportCredential = { viewModel.requestCredentialExport() },
                        onImportCredential = {
                            scope.launch {
                                platform.readClipboardText().fold(
                                    onSuccess = { viewModel.importLoginCredential(it.orEmpty()) },
                                    onFailure = { toastHost.showToast("无法读取剪切板") }
                                )
                            }
                        },
                        onNavigateToHistory = { currentPage = ProfilePage.LOCAL_LIBRARY },
                        settingsContent = appPreferences,
                        onNavigateToNetwork = { currentPage = ProfilePage.NETWORK },
                        onNavigateToBlacklist = { currentPage = ProfilePage.BLACKLIST },
                        onBlacklistEnabledChange = viewModel::setBlacklistEnabled
                    )

                }
            )

            ProfilePage.LOCAL_LIBRARY -> com.rchdliang.copymanga_neo.multiplatform.ui.library.LocalLibraryScreen(
                onBack = { currentPage = ProfilePage.MAIN },
                onCloud = { currentPage = ProfilePage.HISTORY },
                onComic = onReadComic,
                onNovel = { onNavigateToNovel(null, it) },
                viewModel = services.localLibrary
            )

            ProfilePage.HISTORY -> HistoryScreen(
                viewModel = services.history,
                onNavigateBack = { currentPage = ProfilePage.LOCAL_LIBRARY },
                onComicClick = onComicClick,
                onHistoryCleared = onHistoryCleared,
                onLoginClick = {
                    currentPage = ProfilePage.MAIN
                    viewModel.showLoginDialog()
                }
            )

            ProfilePage.READING, ProfilePage.DOWNLOADS -> SettingsScreen(
                initialDestination = if (currentPage == ProfilePage.READING) SettingsDestination.READING else SettingsDestination.DOWNLOADS,
                onOpenDestination = openSettings,
                viewModel = services.settings,
                onManageDownloads = onNavigateToDownloads,
                onNavigateBack = { currentPage = ProfilePage.MAIN },
                onNavigateToData = { currentPage = ProfilePage.DATA },
                onNavigateToAbout = { currentPage = ProfilePage.ABOUT }
            )

            ProfilePage.ABOUT -> AboutScreen(onNavigateBack = { currentPage = ProfilePage.MAIN })

            ProfilePage.DATA -> DataSettingsScreen(
                settings = services.settings,
                backup = services.backup,
                logs = services.logs,
                onNavigateBack = {
                    currentPage = ProfilePage.MAIN
                })

            ProfilePage.NETWORK -> NetworkSettingsScreen(
                viewModel = services.network,
                onNavigateBack = {
                    currentPage = ProfilePage.MAIN
                })

            ProfilePage.BLACKLIST -> ThemeBlacklistPage(
                uiState = uiState,
                onRemove = viewModel::removeBlockedTheme,
                onNavigateBack = { currentPage = ProfilePage.MAIN }
            )
        }
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
    settingsContent: @Composable () -> Unit = {},
    onNavigateToNetwork: () -> Unit = {},
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
    onDismissProfile: () -> Unit = {},
    avatarViewModel: ProfileAvatarViewModel? = null
) {
    val pullToRefreshState = rememberPullToRefreshState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        PullToRefreshBox(
            isRefreshing = uiState.isLoggedIn && uiState.isRefreshing,
            onRefresh = { if (uiState.isLoggedIn) onRefresh() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                if (uiState.isLoggedIn) PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState, isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "我的",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    val personal: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)) {
                            AccountCard(
                                uiState, onLogout, onLoginClick, onEditProfile,
                                onExport = onExportCredential, onImport = onImportCredential
                            )
                            ProfileMenuGroup(
                                uiState,
                                onNavigateToHistory = onNavigateToHistory,
                                onNavigateToNetwork,
                                onNavigateToBlacklist,
                                onBlacklistEnabledChange
                            )
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(Modifier.weight(0.9f)) { personal() }
                            Box(Modifier.weight(1.1f)) { settingsContent() }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            personal()
                            settingsContent()
                        }
                    }
                }
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

    ActiveRouteLaunchedEffect(uiState.showProfileEditor) {
        if (!uiState.showProfileEditor) avatarViewModel?.clear()
    }
    if (uiState.showProfileEditor) ProfileEditDialog(
        avatarViewModel = requireNotNull(avatarViewModel) { "Provide the retained avatar model before editing a profile." },
        profile = uiState.editableProfile,
        isLoading = uiState.isProfileLoading,
        isSaving = uiState.isProfileSaving,
        error = uiState.profileEditError,
        onRetry = onRetryProfile,
        onSave = onSaveProfile,
        onDismiss = onDismissProfile
    )


}

/** 账号资料可直接编辑，其余账号操作收进菜单。 */
@Composable
private fun AccountCard(
    uiState: ProfileUiState,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    onEditProfile: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val enabled =
        !uiState.isCredentialLoading && !uiState.isLoginLoading && !uiState.isProfileSaving
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoggedIn) {
                    PreviewAwareAsyncImage(
                        model = uiState.avatar, previewDrawable = Res.drawable.preview_avatar,
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = "编辑资料",
                                onClick = onEditProfile
                            ),
                        placeholder = rememberVectorPainter(Icons.Filled.AccountCircle),
                        error = rememberVectorPainter(Icons.Filled.AccountCircle)
                    )
                } else {
                    Icon(
                        Icons.Filled.AccountCircle,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = if (uiState.isLoggedIn) "编辑资料" else "登录",
                            onClick = if (uiState.isLoggedIn) onEditProfile else onLoginClick
                        )
                        .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (uiState.isLoggedIn) uiState.nickname?.takeIf { it.isNotBlank() }
                            ?: uiState.username?.takeIf { it.isNotBlank() }
                            ?: "未知用户" else "登录账号",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uiState.isLoggedIn && !uiState.username.isNullOrBlank() &&
                        !uiState.nickname.isNullOrBlank() && uiState.nickname != uiState.username
                    ) {
                        Text(
                            "@${uiState.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${uiState.requestMode.profileLabel} · ${if (uiState.isLoggedIn) "已登录" else "未登录"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled) {
                        Icon(
                            Icons.Filled.MoreVert,
                            "账号操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (LocalRouteActive.current) DropdownMenu(
                        menu,
                        onDismissRequest = { menu = false }) {
                        if (uiState.isLoggedIn) DropdownMenuItem(
                            text = {
                                Text(
                                    "导出登录凭据",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Key, null) },
                            onClick = { menu = false; onExport() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "导入登录凭据",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                            onClick = { menu = false; onImport() }
                        )
                        if (uiState.isLoggedIn) DropdownMenuItem(
                            text = {
                                Text(
                                    "退出当前模式",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menu = false; onLogout() }
                        )
                    }
                }
            }
            if (!uiState.isLoggedIn) {
                Text(
                    "登录后同步书架与阅读记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onLoginClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "登录",
                        color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 常用功能与应用配置分组，黑名单始终可用且仅影响首页。 */
@Composable
private fun ProfileMenuGroup(
    uiState: ProfileUiState,
    onNavigateToHistory: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToBlacklist: () -> Unit,
    onBlacklistEnabledChange: (Boolean) -> Unit
) {
    val common = listOf(
        ProfileMenuEntry(
            Icons.Filled.History,
            "阅读记录与书签",
            "本地与云端记录 · 书签 · 轻小说",
            onClick = onNavigateToHistory
        ),
        ProfileMenuEntry(
            Icons.Outlined.Block, "标签黑名单",
            when {
                uiState.isBlacklistLoading -> "正在读取标签"
                uiState.blacklistError != null -> "读取失败，点击查看"
                !uiState.isBlacklistEnabled -> "已停用 · ${uiState.blockedThemes.size} 个标签"
                else -> "首页 · ${uiState.blockedThemes.size} 个标签"
            },
            onClick = onNavigateToBlacklist,
            trailingContent = {
                Switch(
                    checked = uiState.isBlacklistEnabled,
                    onCheckedChange = onBlacklistEnabledChange,
                    enabled = !uiState.isBlacklistLoading && !uiState.isBlacklistSaving && uiState.blacklistError == null,
                    modifier = Modifier.semantics { contentDescription = "启用标签黑名单" }
                )
            })
    )
    val app = listOf(
        ProfileMenuEntry(
            Icons.Outlined.Wifi, "请求模式与网络", "当前：${uiState.requestMode.profileLabel}",
            onClick = onNavigateToNetwork
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)) {
        listOf("阅读与内容" to common, "账号与连接" to app).forEach { (title, entries) ->
            ProfileSectionTitle(title)
            entries.forEachIndexed { index, entry ->
                ProfileListItem(
                    entry.icon, entry.label, entry.supportingText, index, entries.size,
                    entry.onClick, enabled = entry.enabled, trailingContent = entry.trailingContent
                )
            }
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

/** 登录表单只校验必填项，保留已有账号的兼容性。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LoginDialog(
    isLoading: Boolean,
    error: String?,
    savedUsername: String?,
    savedPasswordProvider: () -> String?,
    rememberPassword: Boolean,
    autoLogin: Boolean,
    onLogin: (String, String, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRegister: () -> Unit = {},
    onClearError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val usernameState = rememberTextFieldState()
    val passwordState = remember { TextFieldState() }
    var showPassword by remember { mutableStateOf(false) }
    var rememberPasswordChecked by remember { mutableStateOf(rememberPassword || autoLogin) }
    var auto by remember { mutableStateOf(autoLogin) }
    var submitted by remember { mutableStateOf(false) }
    // Subscribe to validation transitions, not every keystroke in the dialog layout.
    val usernameError = remember {
        derivedStateOf { if (submitted && usernameState.text.isBlank()) "请输入账号" else null }
    }.value
    val passwordError = remember {
        derivedStateOf { if (submitted && passwordState.text.isBlank()) "请输入密码" else null }
    }.value
    val formScrollState = rememberScrollState()
    val inheritedRelocationSpec = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val cursorLineHeight = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toPx() }
    val relocationSpec = remember(density, cursorLineHeight) {
        LoginFormBringIntoViewSpec(
            cursorMaxHeight = cursorLineHeight * 1.5f,
            contextHeight = with(density) { 56.dp.toPx() } * density.fontScale.coerceAtLeast(1f)
        )
    }

    ActiveRouteLaunchedEffect(Unit) {
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
        val username = usernameState.text.toString().trim()
        val password = passwordState.text.toString()
        submitted = true
        if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
            onLogin(username, password, rememberPasswordChecked, auto)
        }
    }

    if (LocalRouteActive.current) AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!isLoading) onDismiss() },
        text = {
            CompositionLocalProvider(LocalBringIntoViewSpec provides relocationSpec) {
                Column(
                    Modifier.verticalScroll(formScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedRelocationSpec) {
                        // In a short window with the IME open, fixed icon/title slots can leave
                        // less than one text field of usable height. Scroll them with the form;
                        // the action buttons remain fixed and cursor relocation stays enabled.
                        Icon(
                            Icons.AutoMirrored.Outlined.Login, contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "登录账号",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "登录当前模式。登录另一模式后，原会话会失效。",
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
                                    "记住密码", "",
                                    rememberPasswordChecked, !isLoading
                                ) {
                                    rememberPasswordChecked = it
                                    if (!it) auto = false
                                }
                                LoginPreferenceRow(
                                    "自动登录", "",
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

/**
 * A single relocation owner: Compose handles focus, cursor and IME viewport changes.
 * Small relocation targets get surrounding room for the field decoration. Larger targets
 * keep Compose's normal nearest-edge behavior. This policy is scoped to the vertical form;
 * child text fields retain their platform policy for horizontal cursor scrolling.
 * No extra coroutine may start/cancel another scroll while the Android IME is animating.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class LoginFormBringIntoViewSpec(
    private val cursorMaxHeight: Float,
    private val contextHeight: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        if (size > cursorMaxHeight || size >= containerSize) {
            return super.calculateScrollDistance(offset, size, containerSize)
        }
        val visibleContext = contextHeight.coerceAtMost(containerSize).coerceAtLeast(size)
        val padding = (visibleContext - size) / 2f
        return super.calculateScrollDistance(offset - padding, visibleContext, containerSize)
    }
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
            if (description.isNotBlank()) Text(
                description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// region Previews

@Preview(showBackground = true, name = "我的 - 已登录")
@Preview(showBackground = true, widthDp = 1200, heightDp = 800, name = "我的 - 平板")
@Composable
private fun ProfileLoggedInPreview() {
    CopyManga_NeoTheme {
        ProfileMainPage(
            uiState = ProfileUiState(
                isLoggedIn = true,
                avatar = "https://example.com/avatar.png",
                username = "user123",
                nickname = "旅行者",
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
            settingsContent = {}
        )
    }
}

@Preview(showBackground = true, name = "我的 - 未登录")
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
            settingsContent = {}
        )
    }
}

@Preview(showBackground = true, name = "我的 - 登录对话框")
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
            settingsContent = {}
        )
    }
}

@Preview(showBackground = true, name = "我的 - 设置")
@Composable
private fun SettingsPagePreview() {
    CopyManga_NeoTheme {
        SettingsPage(onNavigateBack = {})
    }
}

@Preview(showBackground = true, name = "设置 - 阅读")
@Composable
private fun ReaderSettingsPreview() {
    CopyManga_NeoTheme {
        SettingsPage(
            onNavigateBack = {},
            initialDestination = SettingsDestination.READING
        )
    }
}

@Preview(showBackground = true, name = "设置 - 下载 · 深色")
@Composable
private fun DownloadSettingsPreview() {
    CopyManga_NeoTheme(darkTheme = true) {
        SettingsPage(onNavigateBack = {}, initialDestination = SettingsDestination.DOWNLOADS)
    }
}

// endregion
