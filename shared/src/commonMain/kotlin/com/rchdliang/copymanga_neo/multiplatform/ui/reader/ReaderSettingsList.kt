package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfileListItem

@Composable
internal fun ReaderSettingsLink(label: String, value: String? = null, index: Int = 0, count: Int = 1,
    enabled: Boolean = true, onClick: () -> Unit) {
    val icon = when(label) {
        "翻页设置" -> Icons.Outlined.Swipe
        "画质增强" -> Icons.Outlined.AutoAwesome
        "图片加载" -> Icons.Outlined.CloudDownload
        "本章配页" -> Icons.Outlined.ViewWeek
        "操作说明" -> Icons.Outlined.HelpOutline
        else -> Icons.Outlined.Settings
    }
    ProfileListItem(icon, label, null, index, count, onClick, enabled = enabled,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
}

@Composable
internal fun <T> ReaderSingleChoice(label: String, selected: T, choices: List<T>, title: (T) -> String,
    onSelect: (T) -> Unit) {
    val navigate = LocalReaderSettingsNavigation.current
    ReaderSettingsLink(label, title(selected)) {
        navigate(ReaderSettingsPage(label) { ReaderChoiceOptions(selected, choices, title) { onSelect(it); navigate(null) } })
    }
}

@Composable
internal fun <T> ReaderChoiceOptions(selected: T, choices: List<T>, title: (T) -> String, onSelect: (T) -> Unit) {
    choices.forEach { option ->
        val checked = selected == option
        Surface(color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().selectable(checked, role = Role.RadioButton, onClick = { onSelect(option) })
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title(option), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge)
                RadioButton(checked, onClick = null)
            }
        }
    }
}

@Composable
internal fun ReaderSettingsOverview(qualityAvailable: Boolean, display: () -> Unit, turns: () -> Unit,
    quality: () -> Unit, loading: () -> Unit, pairing: @Composable () -> Unit, help: @Composable () -> Unit) {
    val count = if (qualityAvailable) 4 else 3
    ReaderSettingsLink("显示设置", index = 0, count = count, onClick = display)
    ReaderSettingsLink("翻页设置", index = 1, count = count, onClick = turns)
    if (qualityAvailable) ReaderSettingsLink("画质增强", index = 2, count = count, onClick = quality)
    ReaderSettingsLink("图片加载", index = count - 1, count = count, onClick = loading)
    Spacer(Modifier.height(17.dp))
    pairing(); help()
}

@Composable
internal fun ReaderDisplaySettings(direction: ReadingDirection, scale: PageScale, orientation: ScreenOrientationSetting,
    directionChange: (ReadingDirection) -> Unit, scaleChange: (PageScale) -> Unit,
    orientationChange: (ScreenOrientationSetting) -> Unit, reset: () -> Unit, layout: @Composable () -> Unit) {
    layout()
    ReaderSingleChoice("阅读方向", direction, ReadingDirection.entries, { it.label }, directionChange)
    ReaderSingleChoice("屏幕方向", orientation, ScreenOrientationSetting.entries, { it.label }, orientationChange)
    ReaderSingleChoice("页面缩放", scale, PageScale.entries, { it.label }, scaleChange)
    TextButton(onClick = reset) { Text("重置显示设置", color = MaterialTheme.colorScheme.primary) }
}

@Composable
internal fun ReaderQualitySection(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
