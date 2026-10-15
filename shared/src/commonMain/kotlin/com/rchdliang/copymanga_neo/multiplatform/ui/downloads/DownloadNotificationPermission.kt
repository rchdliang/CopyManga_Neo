package com.rchdliang.copymanga_neo.multiplatform.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Native host persists its already-requested flag and never repeatedly prompts after a denial. */
interface DownloadNotificationPlatform {
    suspend fun requestDownloadNotificationPermissionOnce()
}

/** Request only for an active transfer and an interactive foreground window. */
@Composable
fun DownloadNotificationPermission(hasDownloads: Boolean, platform: DownloadNotificationPlatform) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(hasDownloads, lifecycle) {
        if (hasDownloads && lifecycle == Lifecycle.State.RESUMED) {
            platform.requestDownloadNotificationPermissionOnce()
        }
    }
}
