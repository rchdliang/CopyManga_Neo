package com.rchdliang.copymanga_neo.data.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** 离线读取当前设备属性；不使用历史样本，也不构造或发送请求。 */
class OfficialAppDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun report(): String {
        val secureId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "（不可用）"
        } catch (_: SecurityException) {
            "（无权限）"
        }
        return buildString {
            appendLine("实机属性检查（离线）")
            appendLine("生成时间：${Instant.now()}")
            appendLine("未发送网络请求；以下是当前设备的系统属性。")
            appendLine()
            appendLine("Build.ID = ${Build.ID}")
            appendLine("Settings.Secure.ANDROID_ID = $secureId")
            appendLine("Build.MODEL = ${Build.MODEL}")
            appendLine("Build.DEVICE = ${Build.DEVICE}")
            appendLine("Build.MANUFACTURER = ${Build.MANUFACTURER}")
            appendLine("Build.VERSION.RELEASE = ${Build.VERSION.RELEASE}")
            appendLine("Build.VERSION.SDK_INT = ${Build.VERSION.SDK_INT}")
            appendLine("Build.TIME（毫秒） = ${Build.TIME}")
            appendLine("Build.TIME（UTC） = ${Instant.ofEpochMilli(Build.TIME.coerceAtLeast(0))}")
            appendLine("当前时区 = ${ZoneId.systemDefault()}")
        }
    }
}
