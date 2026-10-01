package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBackupCodecTest {
    private val value = SettingsBackup(
        AppSettings(AppThemeMode.DARK, true),
        ReaderPreferences("TOP_TO_BOTTOM", "FIT_WIDTH", "PORTRAIT"),
        HomeRankPreferences("female", "month")
    )

    @Test
    fun preservesAllSupportedPreferences() {
        assertEquals(value, SettingsBackupCodec.decode(SettingsBackupCodec.encode(value)))
    }

    @Test
    fun rejectsUnrelatedFileAndFutureVersion() {
        val encoded = SettingsBackupCodec.encode(value)
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(encoded.replace("copymanga-neo-settings", "other-app"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(encoded.replace("\"version\":1", "\"version\":2"))
        }
    }

    @Test
    fun rejectsInvalidEnumsInsteadOfResettingPreferences() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(
                SettingsBackupCodec.encode(value).replace("FIT_WIDTH", "BROKEN")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(
                SettingsBackupCodec.encode(value).replace("female", "unknown")
            )
        }
    }

    @Test
    fun rejectsMissingOrWronglyTypedFields() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(
                SettingsBackupCodec.encode(value)
                    .replace("\"wifiOnlyDownloads\":true", "\"wifiOnlyDownloads\":\"true\"")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(
                SettingsBackupCodec.encode(value)
                    .replace("\"pageScale\":\"FIT_WIDTH\",", "")
            )
        }
    }

    @Test
    fun rejectsOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(" ".repeat(SettingsBackupCodec.MAX_BYTES + 1))
        }
    }
}
