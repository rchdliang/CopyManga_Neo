package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 最小本地配置读取期间沿用首页结构；不再单独切换到全屏转圈页。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartupSurface(error: String? = null, onRetry: () -> Unit = {}) {
    MainNavigationShell(MainTab.HOME, {}, false, null, 0) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "首页",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) {
                    Text(
                        "重试",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LoadingIndicator(Modifier.fillMaxWidth())
                repeat(2) {
                    Box(
                        Modifier.size(100.dp, 24.dp).background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) {
                            Box(
                                Modifier.weight(1f).height(180.dp).background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(12.dp)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
