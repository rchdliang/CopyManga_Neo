package com.rchdliang.copymanga_neo.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** 所有通知选项交给系统管理，厂商未提供通知页时退回应用详情。 */
internal fun openNotificationSettings(context: Context): Boolean {
    val intents = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        )
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // 继续尝试应用详情入口。
        } catch (_: SecurityException) {
            // 厂商可能限制专用入口。
        }
    }
    return false
}
