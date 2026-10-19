package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import com.rchdliang.copymanga_neo.domain.model.ProfileGender
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage

/** 手机、平板与桌面共用资料表单；图片选择和编码由外层提供。 */
@Composable
internal fun ProfileEditorFrame(
    saving: Boolean,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !saving,
            dismissOnClickOutside = !saving
        )
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            content(Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight))
        }
    }
}

@Composable
internal fun ProfileEditorContent(
    profile: EditableProfile?,
    isLoading: Boolean,
    isSaving: Boolean,
    imageLoading: Boolean,
    avatarModel: Any?,
    avatarChanged: Boolean,
    error: String?,
    onChooseAvatar: () -> Unit,
    onResetAvatar: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String, Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) = key(profile) {
    val nickname = rememberTextFieldState(initialText = profile?.nickname.orEmpty())
    var gender by rememberSaveable {
        mutableStateOf(profile?.gender?.takeIf { current -> ProfileGender.entries.any { it.value == current } })
    }
    val name = nickname.text.toString().trim()
    val invalidName = profile != null && name.isEmpty()
    val changed =
        profile != null && (name != profile.nickname || avatarChanged || (gender != null && gender != profile.gender))
    val canSave =
        profile != null && !isLoading && !isSaving && !imageLoading && !invalidName && changed

    BoxWithConstraints(modifier) {
        val compact = maxHeight < 480.dp
        val sidePadding = if (compact) 16.dp else 24.dp
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = sidePadding,
                            top = if (compact) 4.dp else 12.dp,
                            end = 12.dp,
                            bottom = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "编辑资料",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(
                            Icons.Default.Close,
                            "关闭编辑资料",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = sidePadding, vertical = if (compact) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)
                ) {
                    if (isLoading) Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    if (profile != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(if (compact) 8.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(if (compact) 56.dp else 96.dp)
                                    .semantics { contentDescription = "更换头像" }
                                    .clickable(
                                        enabled = !isSaving && !imageLoading,
                                        role = Role.Button,
                                        onClick = onChooseAvatar
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle, null, Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                if (avatarModel != null) PreviewAwareAsyncImage(
                                    model = avatarModel,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                                Surface(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(if (compact) 24.dp else 32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (imageLoading) CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        else Icon(
                                            Icons.Default.PhotoCamera, null, Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                if (!compact) Text(
                                    "头像", color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = onChooseAvatar,
                                        enabled = !isSaving && !imageLoading
                                    ) {
                                        Text("更换头像", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (avatarChanged) TextButton(
                                        onClick = onResetAvatar,
                                        enabled = !isSaving && !imageLoading
                                    ) {
                                        Text(
                                            "还原头像",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            state = nickname,
                            label = {
                                Text(
                                    "昵称",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            enabled = !isSaving,
                            isError = invalidName,
                            supportingText = if (invalidName) {
                                { Text("请输入昵称", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "性别",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProfileGender.entries.forEach { choice ->
                                    val selected = gender == choice.value
                                    FilterChip(
                                        selected = selected,
                                        onClick = { gender = choice.value },
                                        enabled = !isSaving,
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { role = Role.RadioButton },
                                        label = {
                                            Box(
                                                Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    choice.label,
                                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (error != null) Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier
                            .fillMaxWidth()
                            .padding(16.dp)) {
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (profile == null && !isLoading) TextButton(onClick = onRetry) {
                                Text("重新加载", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { onSave(name, gender?.takeIf { it != profile?.gender }) },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding, vertical = if (compact) 12.dp else 16.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        if (isSaving) "保存中…" else "保存修改",
                        color = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
