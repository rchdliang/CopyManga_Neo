package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ThemeBlacklistPage(
    uiState: ProfileUiState,
    onRemove: (Theme) -> Unit,
    onNavigateBack: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<Theme?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("标签黑名单", color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            // 父级主页面已处理状态栏边距。
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        Text(
            "仅隐藏首页中包含这些标签的漫画，书架和搜索结果不受影响。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            uiState.isBlacklistLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }

            uiState.blacklistError != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "黑名单读取失败，请重启应用后重试。",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }

            uiState.blockedThemes.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "尚未屏蔽任何标签",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "在漫画详情页长按标签即可加入黑名单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                uiState.blockedThemes.forEach { theme ->
                    key(theme.pathWord?.takeIf { it.isNotBlank() }?.let { "path:$it" }
                        ?: "name:${theme.name}") {
                        // 不使用 weight 或等宽网格；标签按内容宽度排列，放不下时换行。
                        InputChip(
                            selected = false,
                            enabled = !uiState.isBlacklistSaving,
                            onClick = { pendingRemoval = theme },
                            label = {
                                Text(
                                    theme.name,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "移除${theme.name}")
                            }
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { theme ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("取消屏蔽标签", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "是否将「${theme.name}」移出黑名单？",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isBlacklistSaving,
                    onClick = {
                        onRemove(theme)
                        pendingRemoval = null
                    }
                ) { Text("移除", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, name = "标签黑名单 - 手机")
@Preview(showBackground = true, widthDp = 840, heightDp = 700, name = "标签黑名单 - 平板")
@Composable
private fun ThemeBlacklistPreview() {
    CopyManga_NeoTheme {
        ThemeBlacklistPage(
            uiState = ProfileUiState(
                isBlacklistLoading = false,
                blockedThemes = listOf(
                    Theme("校园", "xiaoyuan"),
                    Theme("恐怖", "kongbu"),
                    Theme("科幻", "kehuan")
                )
            ),
            onRemove = {},
            onNavigateBack = {}
        )
    }
}
