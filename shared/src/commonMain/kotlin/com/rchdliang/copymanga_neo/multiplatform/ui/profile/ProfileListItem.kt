@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 目前 Material 3 未提供对应公开 token 的列表尺寸。 */
internal object ProfileListDefaults {
    val MinHeight = 72.dp
    val Gap = 3.dp
}

/** 我的页面与黑名单管理共用的 Expressive 分组列表项。 */
@Composable
internal fun ProfileListItem(
    icon: ImageVector,
    label: String,
    supportingText: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.small
    val shape = inner.copy(
        topStart = if (index == 0) outer.topStart else inner.topStart,
        topEnd = if (index == 0) outer.topEnd else inner.topEnd,
        bottomStart = if (index == count - 1) outer.bottomStart else inner.bottomStart,
        bottomEnd = if (index == count - 1) outer.bottomEnd else inner.bottomEnd
    )
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ProfileListDefaults.MinHeight),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize())
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Material 图标使用其默认的 24dp 固有尺寸。
                    Icon(icon, contentDescription = null)
                }
            }
        },
        supportingContent = {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = trailingContent ?: {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalContentColor.current
        )
    }
}

@Preview(name = "组件 - 列表项 (浅色)", showBackground = true, widthDp = 360)
@Composable
private fun ProfileListItemPreview() {
    CopyManga_NeoTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)
            ) {
                ProfileListItem(
                    icon = Icons.Outlined.Settings,
                    label = "设置",
                    supportingText = "网络与外观配置",
                    index = 0,
                    count = 3,
                    onClick = {}
                )
                ProfileListItem(
                    icon = Icons.Outlined.Palette,
                    label = "外观主题",
                    supportingText = "切换深色模式与动态色彩",
                    index = 1,
                    count = 3,
                    onClick = {}
                )
                ProfileListItem(
                    icon = Icons.Outlined.Info,
                    label = "关于",
                    supportingText = "版本信息与软件设置备份",
                    index = 2,
                    count = 3,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "组件 - 列表项 (深色)", showBackground = true, widthDp = 360)
@Composable
private fun ProfileListItemDarkPreview() {
    CopyManga_NeoTheme(darkTheme = true) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)
            ) {
                ProfileListItem(
                    icon = Icons.Outlined.Settings,
                    label = "设置",
                    supportingText = "网络与外观配置",
                    index = 0,
                    count = 3,
                    onClick = {}
                )
                ProfileListItem(
                    icon = Icons.Outlined.Palette,
                    label = "外观主题",
                    supportingText = "切换深色模式与动态色彩",
                    index = 1,
                    count = 3,
                    onClick = {}
                )
                ProfileListItem(
                    icon = Icons.Outlined.Info,
                    label = "关于",
                    supportingText = "版本信息与软件设置备份",
                    index = 2,
                    count = 3,
                    onClick = {}
                )
            }
        }
    }
}
