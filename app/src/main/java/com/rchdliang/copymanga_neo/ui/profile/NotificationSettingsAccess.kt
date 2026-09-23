package com.rchdliang.copymanga_neo.ui.profile

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications

internal enum class NotificationSettingsTarget { APP, PROGRESS, COMPLETED, LIVE }

internal data class NotificationSettingsStatus(
    val appEnabled: Boolean = false,
    val progressEnabled: Boolean = false,
    val completionEnabled: Boolean = false,
    val liveSupported: Boolean = false,
    val liveEnabled: Boolean = false
)

internal fun readNotificationSettings(context: Context): NotificationSettingsStatus {
    DownloadNotifications(context).createChannel()
    val manager = context.getSystemService(NotificationManager::class.java)
    val enabled = manager.areNotificationsEnabled()
    fun channelEnabled(id: String) =
        enabled && manager.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
    return NotificationSettingsStatus(
        appEnabled = enabled,
        progressEnabled = channelEnabled(DownloadNotifications.CHANNEL),
        completionEnabled = channelEnabled(DownloadNotifications.COMPLETED_CHANNEL),
        liveSupported = Build.VERSION.SDK_INT >= 36,
        liveEnabled = Build.VERSION.SDK_INT >= 36 && channelEnabled(DownloadNotifications.CHANNEL) && manager.canPostPromotedNotifications()
    )
}

/** 系统掌管通知权限与渠道声音；厂商未提供专用页面时退回应用通知页。 */
internal fun openNotificationSettings(
    context: Context,
    target: NotificationSettingsTarget
): Boolean {
    val action = when (target) {
        NotificationSettingsTarget.PROGRESS, NotificationSettingsTarget.COMPLETED -> Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
        NotificationSettingsTarget.LIVE -> if (Build.VERSION.SDK_INT >= 36) Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
        NotificationSettingsTarget.APP -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
    }
    val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    when (target) {
        NotificationSettingsTarget.PROGRESS -> intent.putExtra(
            Settings.EXTRA_CHANNEL_ID,
            DownloadNotifications.CHANNEL
        )

        NotificationSettingsTarget.COMPLETED -> intent.putExtra(
            Settings.EXTRA_CHANNEL_ID,
            DownloadNotifications.COMPLETED_CHANNEL
        )

        else -> Unit
    }
    val fallbacks = listOf(
        intent,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
            Settings.EXTRA_APP_PACKAGE,
            context.packageName
        ),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
    for (candidate in fallbacks) {
        try {
            context.startActivity(candidate)
            return true
        } catch (_: ActivityNotFoundException) {
            // 继续尝试更通用的系统设置入口。
        } catch (_: SecurityException) {
            // 厂商可能限制专用入口。
        }
    }
    return false
}
