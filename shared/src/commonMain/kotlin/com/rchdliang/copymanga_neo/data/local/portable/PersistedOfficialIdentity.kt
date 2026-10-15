package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.PortableOfficialIdentity
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Synthetic identifiers persist once; no hardware identifiers are read and no signature key is saved. */
class PersistedOfficialIdentity private constructor(
    private val defaults: OfficialAppSettings,
    override val tapString: String
) : PortableOfficialIdentity {
    override val loginDate: String = localNow().let { value ->
        "${value.date} ${two(value.hour)}:${two(value.minute)}:${two(value.second)}.${(value.nanosecond / 1_000_000).toString().padStart(3, '0')}Z"
    }
    override fun dateHeader(): String = localNow().date.toString().replace('-', '.')
    override fun resolve(settings: OfficialAppSettings) = settings.copy(
        pseudoId = settings.pseudoId.ifBlank { defaults.pseudoId }, umString = settings.umString.ifBlank { defaults.umString },
        device = settings.device.ifBlank { defaults.device }, deviceInfo = settings.deviceInfo.ifBlank { defaults.deviceInfo },
        buildTime = settings.buildTime.ifBlank { defaults.buildTime })
    companion object {
        private fun localNow() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        private fun two(value: Int) = value.toString().padStart(2, '0')
        suspend fun create(storage: DocumentStorage, device: String, deviceInfo: String, tapString: String = ""): PersistedOfficialIdentity {
            val tap = tapString.trim()
            require(tap.isEmpty() || tap.matches(Regex("[0-9a-fA-F]{32}"))) { "COPYMANGA_TAP_STRING 必须是 32 位十六进制字符" }
            val document = DocumentStore(storage, "official-identity.json", OfficialAppSettings.serializer(), OfficialAppSettings())
            document.load().getOrThrow()
            document.update { old -> old.copy(pseudoId = old.pseudoId.ifBlank { Uuid.random().toHexString().take(16) },
                umString = old.umString.ifBlank { Uuid.random().toHexString() }, device = old.device.ifBlank { device },
                deviceInfo = old.deviceInfo.ifBlank { deviceInfo }, buildTime = old.buildTime.ifBlank { "2025.05.22" }) }.getOrThrow()
            return PersistedOfficialIdentity(document.state.value, tap)
        }
    }
}
