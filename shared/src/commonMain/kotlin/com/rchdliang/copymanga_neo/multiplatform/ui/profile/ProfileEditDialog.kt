package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.EditableProfile
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive

/** 头像选择保留在宿主模型中，所有平台复用同一资料表单。 */
@Composable
internal fun ProfileEditDialog(
    profile: EditableProfile?,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSave: (String, ByteArray?, Int?) -> Unit,
    onDismiss: () -> Unit,
    avatarViewModel: ProfileAvatarViewModel
) {
    val avatar by avatarViewModel.state.collectAsStateWithLifecycle()
    if (LocalRouteActive.current) ProfileEditorFrame(isSaving, onDismiss) { modifier ->
        ProfileEditorContent(
            profile, isLoading, isSaving, avatar.busy,
            avatar.bytes ?: profile?.avatar, avatar.bytes != null, avatar.error ?: error,
            onChooseAvatar = avatarViewModel::choose,
            onResetAvatar = avatarViewModel::clear,
            onRetry = onRetry,
            onSave = { nickname, gender -> onSave(nickname, avatar.bytes, gender) },
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}
