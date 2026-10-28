package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.rchdliang.copymanga_neo.domain.model.AppReleaseChannel
import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppReleaseSelectionTest {
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, notes: String? = null) =
        JsonObject().apply {
            addProperty("tag_name", tag); addProperty("draft", draft); addProperty("prerelease", prerelease)
            if (notes != null) addProperty("body", notes)
        }
    private fun page(vararg releases: JsonObject) = JsonArray().apply { releases.forEach { add(it) } }

    @Test fun cmpStableReleaseWinsOverAndroidAndPreservesNotes() = runTest {
        val notes = "## 更新说明\n- 修复阅读\n\n**感谢使用**"
        val result = selectAppRelease { page(release("android-v99.0"), release("v0.10.29", notes = notes)) }
        assertEquals(AppReleaseChannel.CMP, result.channel)
        assertEquals("0.10.29", result.version)
        assertEquals(notes, result.releaseNotes)
        assertTrue(result.pageUrl.endsWith("/releases/tag/v0.10.29"))
    }

    @Test fun cmpMayAppearOnALaterPageAfterANativeRelease() = runTest {
        val requested = mutableListOf<Int>()
        val result = selectAppRelease { index ->
            requested += index
            if (index == 1) JsonArray().apply {
                add(release("android-v99.0"))
                repeat(19) { add(release("v98.$it", draft = true)) }
            } else page(release("v0.10.29"))
        }
        assertEquals(listOf(1, 2), requested)
        assertEquals(AppReleaseChannel.CMP, result.channel)
    }

    @Test fun legacyTagsDraftsAndPrereleasesDoNotTriggerMigration() = runTest {
        val excluded = listOf("v0.5.21", "v0.6.3", "v0.6.7.2", "v0.6.29", "v0.9.14", "v0.9.24", "v0.9.30", "v0.10.3", "v0.10.19", "v0.10.20", "v0.10.25")
        val releases = JsonArray().apply {
            excluded.forEach { add(release(it)) }
            add(release("v99.0", draft = true)); add(release("v98.0", prerelease = true))
            add(release("v0.10.29-beta")); add(release("unrelated"))
            add(release("android-v0.10.29", notes = "旧版更新")); add(release("android-v0.10.28"))
        }
        val result = selectAppRelease { releases }
        assertEquals(AppReleaseChannel.ANDROID, result.channel)
        assertEquals("0.10.29", result.version)
        assertEquals("旧版更新", result.releaseNotes)
    }

    @Test fun missingNullAndEmptyBodiesAreAccepted() = runTest {
        val missing = release("v0.10.29")
        val nullBody = release("v0.10.29").apply { add("body", JsonNull.INSTANCE) }
        val empty = release("v0.10.29", notes = "")
        for (item in listOf(missing, nullBody, empty)) {
            assertEquals("", selectAppRelease { page(item) }.releaseNotes)
        }
    }

    @Test fun noStableReleaseReportsUnavailable() = runTest {
        val result = runCatching { selectAppRelease { page(release("v99.0", prerelease = true)) } }
        assertTrue(result.exceptionOrNull() is PublicReleaseUnavailableException)
    }

    @Test fun cancellationIsNotTreatedAsNoUpdate() = runTest {
        val result = runCatching { selectAppRelease { throw CancellationException("cancelled") } }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
