package com.rchdliang.copymanga_neo.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(private val repository: ShareLinkRepository) :
    ViewModel() {
    suspend fun link(target: ShareTarget): Result<String> =
        repository.baseUrl().mapCatching { base ->
            Uri.parse(base).buildUpon().apply {
                target.pathSegments.forEach { appendPath(it) }
                target.query.forEach { (name, value) -> appendQueryParameter(name, value) }
            }.build().toString()
        }
}

/** 点击时获取链接，避免预览或页面打开时产生额外网络请求。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAction(target: ShareTarget) {
    val preview = LocalInspectionMode.current
    val viewModel = if (preview) null else hiltViewModel<ShareViewModel>()
    val context = LocalContext.current
    val toast = LocalToastHost.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    "分享",
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            enabled = !sharing && !preview,
            onClick = {
                sharing = true
                scope.launch {
                    try {
                        viewModel?.link(target)?.fold(
                            onSuccess = { url ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, target.title)
                                    putExtra(Intent.EXTRA_TEXT, "${target.title}\n$url")
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "分享"))
                                } catch (_: ActivityNotFoundException) {
                                    toast.showToast("没有可用的分享应用")
                                }
                            },
                            onFailure = { toast.showToast(it.message ?: "无法生成分享链接") }
                        )
                    } finally {
                        sharing = false
                    }
                }
            }
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "分享",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
