package com.rchdliang.copymanga_neo.multiplatform.ui.components

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.ViewModel
import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import kotlinx.coroutines.CancellationException
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository
import kotlinx.coroutines.launch
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

class ShareViewModel constructor(private val repository: ShareLinkRepository) :
    ViewModel() {
    suspend fun link(target: ShareTarget): Result<String> =
        repository.baseUrl().mapCatching { base ->
            URLBuilder(base).apply {
                appendPathSegments(target.pathSegments, encodeSlash = true)
                target.query.forEach { (name, value) -> parameters.append(name, value) }
            }.buildString()
        }
}

/** Provided by the host with its real share-link repository. Never resolved during a preview. */
val LocalShareViewModel = staticCompositionLocalOf<ShareViewModel?> { null }

/** 点击时获取链接，避免预览或页面打开时产生额外网络请求。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAction(target: ShareTarget) {
    val preview = LocalInspectionMode.current
    val viewModel = if (preview) null else requireNotNull(LocalShareViewModel.current) {
        "Provide ShareViewModel with a real ShareLinkRepository at the application root."
    }
    val platform = if (preview) null else requireNotNull(LocalUiPlatform.current) {
        "Provide UiPlatform for native sharing."
    }
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
                                requireNotNull(platform).shareText(target.title, "${target.title}\n$url")
                                    .onFailure {
                                        shareFailureMessage(it)?.let(toast::showToast)
                                    }
                            },
                            onFailure = { shareFailureMessage(it, "无法生成分享链接")?.let(toast::showToast) }
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

/** Dismissal has no success/error feedback; coroutine cancellation still propagates. */
internal fun shareFailureMessage(error: Throwable, fallback: String = "没有可用的分享应用"): String? =
    when (error) {
        is CancellationException -> throw error
        is UserActionCancelled -> null
        else -> error.message ?: fallback
    }
