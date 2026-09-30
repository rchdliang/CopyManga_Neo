package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import android.os.Build
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** 随机 SDK 标识只生成一次，发送请求时不会重新随机；不读取不可访问的硬件序列号。 */
@Singleton
class OfficialAppIdentity @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("official_app_identity", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private fun persistent(key: String, length: Int, alphabet: String): String {
        prefs.getString(key, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val value =
            buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
        if (!prefs.edit().putString(key, value).commit()) throw IOException("无法保存 App 身份标识")
        return value
    }

    private val pseudoId = persistent(
        "pseudo_id",
        16,
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    )
    private val umString = persistent("um_string", 32, "0123456789abcdef")
    private val startedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'")
        .withZone(ZoneId.systemDefault()).format(Instant.now())
    val loginDate: String get() = startedAt
    val defaults: OfficialAppSettings
        get() = OfficialAppSettings(
            pseudoId = pseudoId, umString = umString, device = Build.ID,
            deviceInfo = "${Build.MODEL}-${Build.DEVICE}",
            // 沿用 HAR 的稳定默认值，字段含义未确定；用户仍可手动覆盖。
            buildTime = "2025.05.22"
        )

    fun resolve(value: OfficialAppSettings): OfficialAppSettings {
        val base = defaults
        return value.copy(
            pseudoId = value.pseudoId.ifBlank { base.pseudoId },
            umString = value.umString.ifBlank { base.umString },
            device = value.device.ifBlank { base.device },
            deviceInfo = value.deviceInfo.ifBlank { base.deviceInfo },
            buildTime = value.buildTime.ifBlank { base.buildTime })
    }
}
