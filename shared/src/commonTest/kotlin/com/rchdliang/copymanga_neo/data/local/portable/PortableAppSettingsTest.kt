package com.rchdliang.copymanga_neo.data.local.portable

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PortableAppSettingsTest {
    private class Documents : DocumentStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    @Test fun freshAndMissingLayoutPreferencesMatchAndroidDefaults() = runTest {
        for (stored in listOf(null, "{}")) {
            val documents = Documents()
            if (stored != null) documents.values["app-settings.json"] = stored
            val settings = PortableAppSettings(documents)
            settings.initialize().getOrThrow()
            assertFalse(settings.getSearchCompactLayout(keywordSearch = true).getOrThrow())
            assertFalse(settings.getSearchCompactLayout(keywordSearch = false).getOrThrow())
        }
    }

    @Test fun explicitLayoutChoicesRemainIndependentAcrossRestart() = runTest {
        for (keywordSearch in listOf(true, false)) {
            val documents = Documents()
            val settings = PortableAppSettings(documents)
            settings.setSearchCompactLayout(keywordSearch, true).getOrThrow()
            val restored = PortableAppSettings(documents)
            restored.initialize().getOrThrow()
            assertEquals(true, restored.getSearchCompactLayout(keywordSearch).getOrThrow())
            assertEquals(false, restored.getSearchCompactLayout(!keywordSearch).getOrThrow())
            restored.setSearchCompactLayout(keywordSearch, false).getOrThrow()
            assertFalse(PortableAppSettings(documents).getSearchCompactLayout(keywordSearch).getOrThrow())
        }
    }

    @Test fun resetRestoresBothAndroidLayoutDefaultsDurably() = runTest {
        val documents = Documents()
        val settings = PortableAppSettings(documents)
        settings.setSearchCompactLayout(keywordSearch = true, compact = true).getOrThrow()
        settings.setSearchCompactLayout(keywordSearch = false, compact = true).getOrThrow()
        settings.resetDefaults().getOrThrow()
        val restored = PortableAppSettings(documents)
        restored.initialize().getOrThrow()
        assertFalse(restored.getSearchCompactLayout(keywordSearch = true).getOrThrow())
        assertFalse(restored.getSearchCompactLayout(keywordSearch = false).getOrThrow())
    }
}
