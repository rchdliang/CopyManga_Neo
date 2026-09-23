package com.rchdliang.copymanga_neo.ui.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 第一次发起下载且界面可交互时请求通知权限；拒绝后不反复打扰。 */
@Composable
internal fun DownloadNotificationPermission(hasDownloads: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(hasDownloads, lifecycle) {
        if (!hasDownloads || lifecycle != Lifecycle.State.RESUMED) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return@LaunchedEffect
        val preferences =
            context.getSharedPreferences("download_notifications", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("permission_requested", false)) {
            preferences.edit { putBoolean("permission_requested", true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
