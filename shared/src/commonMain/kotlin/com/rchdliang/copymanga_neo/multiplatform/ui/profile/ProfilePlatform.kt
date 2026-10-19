package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.history.HistoryViewModel
import com.rchdliang.copymanga_neo.multiplatform.ui.library.LocalLibraryViewModel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** Native IO keeps the shared account/settings interface independent of Android Context and URI. */
interface ProfilePlatform {
    /** Null only when the host has a real native settings destination; otherwise explain manual management. */
    val notificationSettingsUnavailableReason: String?
        get() = "当前平台无法直接打开通知设置，请在系统或浏览器中手动管理"
    suspend fun readClipboardText(): Result<String?>
    /** Mark credentials as sensitive when supported. UserActionCancelled is a quiet dismissal, not success. */
    suspend fun copySensitiveText(label: String, text: String): Result<Unit>
    /** Null selection is cancellation; do not turn it into an error or perform a write. */
    suspend fun openDocument(mimeTypes: List<String>): Result<String?>
    suspend fun createDocument(name: String, mimeType: String): Result<String?>
    /** Read at most maxBytes. Hosts must enforce the bound while reading, not after allocating the whole file. */
    suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray>
    suspend fun writeDocument(reference: String, bytes: ByteArray): Result<Unit>
    /** Stream the repository's redacted export into the user-selected destination and close the stream. */
    suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit>
    /** Original policy: source <=10MiB, EXIF orientation applied, longest side <=1024px, JPEG quality90. */
    suspend fun pickAvatarJpeg(): Result<ByteArray?>
    suspend fun openNotificationSettings(): Result<Unit>
}

val LocalProfilePlatform = staticCompositionLocalOf<ProfilePlatform?> { null }

@Composable
internal fun requireProfilePlatform(): ProfilePlatform = requireNotNull(LocalProfilePlatform.current) {
    "Provide ProfilePlatform at the profile route before using native account/settings actions."
}

data class AppBuildInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val debug: Boolean
)

val LocalAppBuildInfo = staticCompositionLocalOf {
    com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
}

/** Real repository-backed models are supplied by the app; no placeholder data source is constructed. */
data class ProfileUiServices(
    val platform: ProfilePlatform,
    val appBuildInfo: AppBuildInfo,
    val settings: SettingsViewModel,
    val about: AboutViewModel,
    val backup: SettingsBackupViewModel,
    val network: NetworkSettingsViewModel,
    val logs: NetworkLogViewModel,
    val localLibrary: LocalLibraryViewModel,
    val history: HistoryViewModel,
    val avatar: ProfileAvatarViewModel
)

/** Portable fixed decimal formatting for transfer/storage UI, matching the original positive half-up rounding. */
internal fun fixedDecimal(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    require(decimals in 0..6)
    val scale = 10.0.pow(decimals).toLong()
    val rounded = floor(abs(value) * scale + 0.5).toLong()
    val prefix = if (value < 0 && rounded != 0L) "-" else ""
    return prefix + (rounded / scale).toString() +
        if (decimals == 0) "" else "." + (rounded % scale).toString().padStart(decimals, '0')
}

internal fun formatStorageBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
    val decimals = if (unit == 0 || value >= 100) 0 else if (value >= 10) 1 else 2
    return "${fixedDecimal(value, decimals)} ${units[unit]}"
}
