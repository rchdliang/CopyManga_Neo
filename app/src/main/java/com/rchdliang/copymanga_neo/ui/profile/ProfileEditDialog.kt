package com.rchdliang.copymanga_neo.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** 图片读取仍使用 Android 图片选择器，界面与 CMP 保持相同交互。 */
@Composable
internal fun ProfileEditDialog(
    profile: EditableProfile?,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSave: (String, ByteArray?, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var imageLoading by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                imageLoading = true
                imageError = null
                try {
                    avatarBytes = readAvatarJpeg(context, uri)
                    avatarUri = uri
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    imageError = failure.message ?: "无法读取头像图片"
                } finally {
                    imageLoading = false
                }
            }
        }
    ProfileEditorFrame(isSaving, onDismiss) { modifier ->
        ProfileEditorContent(
            profile, isLoading, isSaving, imageLoading,
            avatarUri ?: profile?.avatar, avatarBytes != null, imageError ?: error,
            onChooseAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onResetAvatar = { avatarBytes = null; avatarUri = null; imageError = null },
            onRetry = onRetry,
            onSave = { nickname, gender -> onSave(nickname, avatarBytes, gender) },
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
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
