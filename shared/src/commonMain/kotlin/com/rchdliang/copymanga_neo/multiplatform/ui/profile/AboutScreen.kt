package com.rchdliang.copymanga_neo.multiplatform.ui.profile

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

/** 关于只显示应用信息，维护工具集中到数据管理。 */
@Composable
internal fun AboutScreen(onNavigateBack: () -> Unit) = AboutPage(onNavigateBack)

@Composable
internal fun AboutPage(onNavigateBack: () -> Unit = {}) {
    ProfileSettingsScaffold("关于", onNavigateBack) { AppIdentityCard() }
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
