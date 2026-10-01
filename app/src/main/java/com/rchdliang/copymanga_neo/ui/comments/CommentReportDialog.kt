package com.rchdliang.copymanga_neo.ui.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.CommentText

/** 举报类型由服务端配置提供，提交失败保留选择与描述，不自动重试。 */
@Composable
internal fun CommentReportDialog(
    state: CommentsUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var selected by remember(state.reportTarget?.id) { mutableStateOf<Int?>(null) }
    var reason by remember(state.reportTarget?.id) { mutableStateOf("") }
    val reasonLength = CommentText.length(reason)
    val reasonTooLong = reasonLength > CommentText.MAX_LENGTH
    val canSubmit = state.reportConfig?.types?.any { it.type == selected } == true &&
            !reasonTooLong && !state.reportLoading && !state.reporting
    AlertDialog(
        onDismissRequest = { if (!state.reporting) onDismiss() },
        title = { Text("举报评论", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    state.reportTarget?.content.orEmpty(),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                if (state.reportLoading) CircularProgressIndicator(Modifier.size(24.dp))
                else {
                    state.reportConfig?.types.orEmpty().forEach { type ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.reporting) { selected = type.type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == type.type, onClick = null,
                                enabled = !state.reporting
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(type.name, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it },
                        enabled = !state.reporting, modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        isError = reasonTooLong,
                        supportingText = {
                            Text(
                                "$reasonLength / 200",
                                color = if (reasonTooLong) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        label = {
                            Text(
                                "举报描述（选填）",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    state.reportConfig?.tip?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                state.reportError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    if (state.reportConfig == null && !state.reportLoading) TextButton(onClick = onRetry) {
                        Text("重试", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onSubmit(it, reason) } },
                enabled = canSubmit
            ) {
                Text(
                    if (state.reporting) "提交中…" else "提交举报",
                    color = if (canSubmit)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.reporting) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
