package com.rchdliang.copymanga_neo.ui.reader

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost

@Composable
fun ReaderSettingsDialog(
    readingDirection: ReadingDirection,
    pageScale: PageScale,
    screenOrientation: ScreenOrientationSetting,
    onReadingDirectionChange: (ReadingDirection) -> Unit,
    onPageScaleChange: (PageScale) -> Unit,
    onScreenOrientationChange: (ScreenOrientationSetting) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                PreferenceSection("屏幕方向") {
                    ScreenOrientationSetting.entries.forEach { option ->
                        FilterChip(
                            onClick = { onScreenOrientationChange(option) },
                            label = { Text(option.label) },
                            selected = screenOrientation == option
                        )
                    }
                }
                PreferenceSection("阅读方向") {
                    ReadingDirection.entries.forEach { option ->
                        FilterChip(
                            onClick = { onReadingDirectionChange(option) },
                            label = { Text(option.label) },
                            selected = readingDirection == option
                        )
                    }
                }
                PreferenceSection("页面缩放") {
                    PageScale.entries.forEach { option ->
                        FilterChip(
                            onClick = { onPageScaleChange(option) },
                            label = { Text(option.label) },
                            selected = pageScale == option,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } }
    )
}

@Composable
private fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(12.dp))
}


@Composable
fun ReaderImageActionDialog(
    title: String,
    currentImageUrl: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val toastHost = LocalToastHost.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                ActionRow(icon = Icons.Default.Refresh, label = "刷新", onClick = onRefresh)
                ActionRow(icon = Icons.Default.Share, label = "分享") {
                    onDismiss()
                    currentImageUrl?.let { url ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享图片"))
                    }
                }
                ActionRow(icon = Icons.Default.Save, label = "保存") {
                    onDismiss()
                    toastHost.showToast("功能开发中")
                }
                ActionRow(icon = Icons.Default.SaveAlt, label = "保存到") {
                    onDismiss()
                    toastHost.showToast("功能开发中")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
