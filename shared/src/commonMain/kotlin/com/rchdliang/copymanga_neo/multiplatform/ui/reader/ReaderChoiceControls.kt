package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

internal enum class ReaderChoicePresentation { SEGMENTED, MENU }

/** 同一设置容器内完成选择；文字无法完整容纳时才使用锚定菜单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ReaderInlineChoice(
    icon: ImageVector, label: String, selected: T, choices: List<T>, title: (T) -> String,
    modifier: Modifier = Modifier, contained: Boolean = true,
    presentation: ReaderChoicePresentation = ReaderChoicePresentation.SEGMENTED, onSelect: (T) -> Unit
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (contained) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f)) {
        BoxWithConstraints(Modifier.padding(if (contained) 16.dp else 0.dp)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widest = choices.maxOfOrNull { measurer.measure(title(it), style).size.width } ?: 0
            val fits = presentation == ReaderChoicePresentation.SEGMENTED && choices.size <= 3 &&
                with(density) { (widest.toDp() + 48.dp) * choices.size <= maxWidth }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fits) ReaderChoiceMenu(icon, label, selected, choices, title, onSelect)
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                if (fits) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("reader-segments-$label")) {
                    choices.forEachIndexed { index, option ->
                        SegmentedButton(selected = selected == option, onClick = { onSelect(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("reader-option-$label-${title(option)}")) {
                            Text(title(option), style = style, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ReaderChoiceMenu(icon: ImageVector, label: String, selected: T, choices: List<T>, title: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = true }.testTag("reader-menu-$label"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(title(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.onPreviewKeyEvent {
            // Consume Escape in this popup before a containing reader dialog can handle it.
            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { expanded = false; true } else false
        }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(title(option)) }, onClick = { onSelect(option); expanded = false },
                    trailingIcon = { if (option == selected) Icon(Icons.Outlined.Check, null) },
                    modifier = Modifier.testTag("reader-option-$label-${title(option)}"))
            }
        }
    }
}
