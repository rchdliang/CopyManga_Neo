package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.runtime.LaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage

/** 上传前限制文件大小、缩小图片并重新编码，不把 URI 或图片存入可恢复的账号状态。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileEditDialog(
    profile: EditableProfile?,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSave: (String, ByteArray?) -> Unit,
    onDismiss: () -> Unit,
    avatarViewModel: ProfileAvatarViewModel
) {
    val avatar by avatarViewModel.state.collectAsStateWithLifecycle()
    val avatarBytes = avatar.bytes
    val imageLoading = avatar.busy
    val imageError = avatar.error
    val nickname = rememberTextFieldState()
    LaunchedEffect(profile) {
        profile?.let { nickname.edit { replace(0, length, it.nickname) } }
    }
    val busy = isLoading || isSaving || imageLoading
    val nicknameText = nickname.text.toString().trim()
    val nicknameError = if (profile != null && nicknameText.isEmpty()) "请输入昵称" else null
    val changed = profile != null && (nicknameText != profile.nickname || avatarBytes != null)
    if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                Icons.Default.AccountCircle,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("编辑资料", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) LoadingIndicator()
                profile?.let {
                    PreviewAwareAsyncImage(
                        model = avatarBytes ?: it.avatar,
                        contentDescription = "头像预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                    )
                    TextButton(onClick = {
                        avatarViewModel.choose()
                    }, enabled = !busy) {
                        Text("更换头像", color = MaterialTheme.colorScheme.primary)
                    }
                    if (avatarBytes != null) TextButton(onClick = {
                        avatarViewModel.clear()
                    }, enabled = !busy) {
                        Text("取消选用新头像", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        state = nickname,
                        label = {
                            Text(
                                "昵称",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = nicknameError != null,
                        supportingText = if (nicknameError == null) null else {
                            { Text(nicknameError, color = MaterialTheme.colorScheme.error) }
                        },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "头像修改受服务器周期限制，保存成功后才会更新账号资料。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                (imageError ?: error)?.let {
                    Text(
                        it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (profile == null && !isLoading) TextButton(onClick = onRetry) {
                        Text("重新加载", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nicknameText, avatarBytes) },
                enabled = !busy && changed && nicknameError == null
            ) {
                Text(
                    if (isSaving) "正在保存" else if (imageLoading) "正在处理图片" else "保存",
                    color = if (busy || !changed) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
