package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.repository.ProjectLinks
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.ic_launcher_foreground
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 版本检查、项目主页与开源致谢集中在关于页面。 */
@Composable
internal fun AboutScreen(
    onNavigateBack: () -> Unit,
    viewModel: AboutViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    LaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    val current = LocalAppBuildInfo.current.versionName
    AboutPage(
        onNavigateBack,
        state,
        { viewModel.checkForUpdate(current) },
        viewModel::dismissUpdate
    )
}

@Composable
internal fun AboutPage(
    onNavigateBack: () -> Unit = {},
    state: AboutUiState = AboutUiState(),
    onCheckUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    var libraries by rememberSaveable { mutableStateOf(false) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val toast = LocalToastHost.current
    fun open(url: String) {
        runCatching { uri.openUri(url) }.onFailure { toast.showToast("无法打开浏览器") }
    }
    BackHandler(enabled = libraries) { libraries = false }
    if (libraries) {
        ThirdPartyLibrariesPage { libraries = false }
        return
    }
    ProfileSettingsScaffold("关于", onNavigateBack) {
        AppIdentityCard()
        ProfileSectionTitle("应用与开源")
        ProfileListItem(
            Icons.Outlined.Refresh, "检查更新",
            if (state.checking) "正在检查最新版本…" else "获取最新发布版本",
            0, 3, enabled = !state.checking, onClick = onCheckUpdate
        )
        ProfileListItem(
            Icons.Outlined.OpenInNew, "GitHub", "项目源码、发布与反馈", 1, 3,
            onClick = { open(ProjectLinks.GITHUB) })
        ProfileListItem(
            Icons.Outlined.Code, "第三方库", "使用的库、许可证与开源致谢", 2, 3,
            onClick = { libraries = true })
    }
    state.update?.let { release ->
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("发现新版本", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "最新版本：${release.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { open(release.pageUrl); onDismissUpdate() }) {
                    Text("查看发布页", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) {
                    Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun AppIdentityCard() {
    val info = LocalAppBuildInfo.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                // 预览与实机共用前景资源，保留图标自身的留白与主题底色。
                Icon(
                    painterResource(Res.drawable.ic_launcher_foreground),
                    contentDescription = "应用图标",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(112.dp)
                )
            }
            Text(
                info.appName, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )
            Text(
                "拷贝漫画第三方客户端", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "版本 ${info.versionName} (${info.versionCode})",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                if (info.debug) "调试版本" else "正式版本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                info.applicationId, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "关于 · 手机", showBackground = true)
@Composable
private fun AboutPhonePreview() {
    CopyManga_NeoTheme { AboutPage() }
}

@Preview(name = "关于 · 深色平板", widthDp = 1100, heightDp = 850, showBackground = true)
@Composable
private fun AboutTabletPreview() {
    CopyManga_NeoTheme(darkTheme = true) { AboutPage() }
}

private data class ThirdPartyLibrary(val name: String, val license: String, val url: String)

/** 直接使用的库及阅读器参考项目；版本由构建依赖统一管理。 */
private val thirdPartyLibraries = listOf(
    ThirdPartyLibrary("Kotlin", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    ThirdPartyLibrary(
        "AndroidX · Compose、Material 3、Lifecycle、Navigation",
        "Apache-2.0",
        "https://github.com/androidx/androidx"
    ),
    ThirdPartyLibrary(
        "Kotlin Coroutines",
        "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    ThirdPartyLibrary(
        "Kotlin Immutable Collections",
        "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.collections.immutable"
    ),
    ThirdPartyLibrary("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    ThirdPartyLibrary("Okio", "Apache-2.0", "https://github.com/square/okio"),
    ThirdPartyLibrary("OpenCC · 简化词典", "Apache-2.0", "https://github.com/BYVoid/OpenCC"),
    ThirdPartyLibrary("kixyubook · 阅读器参考实现", "MIT", "https://github.com/kkyu9527/kixyubook"),
    ThirdPartyLibrary(
        "Compose Multiplatform",
        "Apache-2.0",
        "https://github.com/JetBrains/compose-multiplatform"
    ),
    ThirdPartyLibrary("Ktor", "Apache-2.0", "https://github.com/ktorio/ktor"),
    ThirdPartyLibrary(
        "Kotlin Serialization",
        "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.serialization"
    ),
    ThirdPartyLibrary(
        "Kotlin DateTime",
        "Apache-2.0",
        "https://github.com/Kotlin/kotlinx-datetime"
    ),
    ThirdPartyLibrary(
        "JNA · 桌面平台",
        "Apache-2.0 / LGPL-2.1",
        "https://github.com/java-native-access/jna"
    ),
    ThirdPartyLibrary(
        "Skiko / Skia · 桌面渲染",
        "Apache-2.0 / BSD-3-Clause",
        "https://github.com/JetBrains/skiko"
    ),
)

@Composable
private fun ThirdPartyLibrariesPage(onBack: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val toast = LocalToastHost.current
    ProfileSettingsScaffold("第三方库与开源项目", onBack) {
        thirdPartyLibraries.forEachIndexed { index, library ->
            ProfileListItem(
                Icons.Outlined.Code, library.name, library.license, index, thirdPartyLibraries.size,
                onClick = { runCatching { uri.openUri(library.url) }.onFailure { toast.showToast("无法打开浏览器") } }
            )
        }
    }
}
