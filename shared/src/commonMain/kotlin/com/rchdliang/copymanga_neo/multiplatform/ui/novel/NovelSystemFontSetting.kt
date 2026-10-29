package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import kotlinx.coroutines.CancellationException

@Composable
internal fun NovelSystemFontSetting(
    preferences: NovelReaderPreferences,
    onPreferences: (NovelReaderPreferences) -> Unit
) {
    val platform = LocalNovelReaderPlatform.current
    var open by rememberSaveable { mutableStateOf(false) }
    var fonts by remember(platform) { mutableStateOf<List<NovelSystemFont>?>(null) }
    var failed by remember(platform) { mutableStateOf(false) }
    LaunchedEffect(platform) {
        if (platform?.supportsSystemFonts != true) return@LaunchedEffect
        try {
            fonts = platform.systemFonts()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }
    val selected = fonts?.firstOrNull { it.id == preferences.systemFont }
    if (platform?.supportsSystemFonts == true) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "选择系统字体")
        }
    }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("系统字体") },
        text = {
            when {
                failed -> Text("无法读取系统字体，请关闭后重新打开阅读器。")
                fonts == null -> Text("正在读取系统字体…")
                fonts.orEmpty().isEmpty() -> Text("没有可用的系统字体")
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(fonts.orEmpty(), key = { it.id }) { font ->
                        val isSelected = preferences.systemFont == font.id
                        val textStyle = novelTextStyle(preferences.copy(systemFont = font.id))
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = isSelected, role = Role.Button,
                                onClick = {
                                    onPreferences(preferences.copy(font = "SYSTEM", systemFont = font.id))
                                    open = false
                                }
                            )
                        ) {
                            Text(
                                font.name,
                                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = textStyle.fontFamily,
                                fontWeight = textStyle.fontWeight
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } }
    )
}
