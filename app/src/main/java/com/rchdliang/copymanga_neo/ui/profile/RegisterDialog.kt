package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.RegistrationPolicy

/** 错误标在对应字段；密码只保留在当前弹窗，不保存到可恢复的状态。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RegisterDialog(
    isLoading: Boolean,
    error: String?,
    onRegister: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onClearError: () -> Unit
) {
    val account = rememberTextFieldState()
    val password = remember { TextFieldState() }
    val confirmation = remember { TextFieldState() }
    var submitted by remember { mutableStateOf(false) }
    val accountText = account.text.toString().trim()
    val passwordText = password.text.toString()
    val confirmationText = confirmation.text.toString()
    val cloudAccountError = error?.takeIf(RegistrationPolicy::isUsernameUnavailable)
    val accountError = RegistrationPolicy.usernameError(accountText)
        .takeIf { submitted || account.text.isNotEmpty() } ?: cloudAccountError
    val passwordError = RegistrationPolicy.passwordError(passwordText)
        .takeIf { submitted || passwordText.isNotEmpty() }
    val confirmationError = RegistrationPolicy.confirmationError(passwordText, confirmationText)
        .takeIf { submitted || confirmationText.isNotEmpty() }

    // 编辑后清除上次云端错误；新输入仍由本地校验实时提示。
    LaunchedEffect(accountText, passwordText, confirmationText) { onClearError() }
    fun submit() {
        submitted = true
        if (!isLoading && RegistrationPolicy.validationError(accountText, passwordText) == null &&
            RegistrationPolicy.confirmationError(passwordText, confirmationText) == null &&
            cloudAccountError == null
        ) onRegister(accountText, passwordText, confirmationText)
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("创建账号", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    state = account,
                    label = {
                        Text(
                            "账号", color = if (accountError != null)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text(
                            accountError ?: "6–36 位，支持字母、数字和 @ . + - _",
                            color = if (accountError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = accountError != null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username }
                )
                RegistrationPasswordField(
                    password, "密码", !isLoading, passwordError,
                    "6–36 位", ImeAction.Next
                )
                RegistrationPasswordField(
                    confirmation, "确认密码", !isLoading, confirmationError,
                    "再次输入密码", ImeAction.Done, ::submit
                )
                error?.takeUnless { cloudAccountError != null }?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            it, modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) LoadingIndicator()
                    Text(
                        if (isLoading) "正在注册并登录" else "注册并登录",
                        color = if (isLoading) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLogin, enabled = !isLoading) {
                    Text("已有账号", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun RegistrationPasswordField(
    state: TextFieldState,
    label: String,
    enabled: Boolean,
    error: String?,
    hint: String,
    imeAction: ImeAction,
    onSubmit: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        label = {
            Text(
                label, color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingText = {
            Text(
                error ?: hint, color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        isError = error != null,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        onKeyboardAction = if (onSubmit == null) null else { _ -> onSubmit() },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.NewPassword },
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "隐藏$label" else "显示$label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
