package com.rchdliang.copymanga_neo.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var imageLoading by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val nickname = rememberTextFieldState()
    LaunchedEffect(profile) {
        profile?.let { nickname.edit { replace(0, length, it.nickname) } }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                imageLoading = true
                imageError = null
                try {
                    val bytes = readAvatarJpeg(context, uri)
                    avatarBytes = bytes
                    avatarUri = uri
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    imageError = e.message ?: "无法读取头像图片"
                } finally {
                    imageLoading = false
                }
            }
        }
    val busy = isLoading || isSaving || imageLoading
    val nicknameText = nickname.text.toString().trim()
    val nicknameError = if (profile != null && nicknameText.isEmpty()) "请输入昵称" else null
    val changed = profile != null && (nicknameText != profile.nickname || avatarBytes != null)
    AlertDialog(
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
                        model = avatarUri ?: it.avatar,
                        contentDescription = "头像预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                    )
                    TextButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !busy) {
                        Text("更换头像", color = MaterialTheme.colorScheme.primary)
                    }
                    if (avatarBytes != null) TextButton(onClick = {
                        avatarBytes = null
                        avatarUri = null
                        imageError = null
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

/** 只解码缩小后的图片，避免高分辨率照片直接占用过量内存。 */
private suspend fun readAvatarJpeg(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (output.size() + count > 10 * 1024 * 1024) error("请选择小于 10 MB 的图片")
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        } ?: error("无法打开所选图片")
        // ImageDecoder 同时处理照片方向，重新编码后不会因丢失 EXIF 而旋转头像。
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(source))) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1024) decoder.setTargetSize(
                    (info.size.width * 1024L / longest).toInt().coerceAtLeast(1),
                    (info.size.height * 1024L / longest).toInt().coerceAtLeast(1)
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "图片转换失败" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
