package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import com.rchdliang.copymanga_neo.domain.model.AppReleaseAsset
import com.rchdliang.copymanga_neo.domain.model.AppReleaseTarget
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 从真实弹窗和 Preview 内容生成图片，同时检查按钮行为。 */
@OptIn(ExperimentalTestApi::class)
class AppUpdateDialogPreviewTest {
    @Test fun lightPreviewOpensTheReleasePage() = renderPreview(false)
    @Test fun darkPreviewCanBeDismissed() = renderPreview(true)

    @Test fun androidGithubButtonOpensTheMatchingApkDirectly() = runSkikoComposeUiTest {
        val name = "CopyMangaNeo_android_1.2.3-1a2b3c4+234_arm64-v8a.apk"
        val download = "https://github.com/rchdliang/CopyManga_Neo/releases/download/v1.2.3/$name"
        val release = appUpdatePreviewRelease.copy(assets = listOf(AppReleaseAsset(name, download)))
        var opened: String? = null
        val uri = object : UriHandler { override fun openUri(uri: String) { opened = uri } }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uri) {
                CopyManga_NeoTheme { AppUpdateDialog(release, {}, AppReleaseTarget.Android(listOf("arm64-v8a"))) }
            }
        }
        onNodeWithText("GitHub").performClick()
        assertEquals(download, opened)
    }

    @Test fun windowsButtonsChooseZipAndExeWithoutOpeningTheReleasePage() = runSkikoComposeUiTest {
        val zip = AppReleaseAsset("CopyMangaNeo_windows_1.2.3+234_x64_portable.zip", "https://example.invalid/app.zip")
        val exe = AppReleaseAsset("CopyMangaNeo_windows_1.2.3+234_x64_setup.exe", "https://example.invalid/app.exe")
        val release = appUpdatePreviewRelease.copy(assets = listOf(zip, exe))
        val opened = mutableListOf<String>()
        val uri = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uri) {
                CopyManga_NeoTheme { AppUpdateDialog(release, {}, AppReleaseTarget.Windows("x64")) }
            }
        }
        onNodeWithText("ZIP").performClick()
        onNodeWithText("EXE").performClick()
        assertEquals(listOf(zip.downloadUrl, exe.downloadUrl), opened)
    }

    @Test fun rejectedDownloadLinkFallsBackToTheReleasePage() = runSkikoComposeUiTest {
        val name = "CopyMangaNeo_android_1.2.3-1a2b3c4+234_arm64-v8a.apk"
        val download = "https://example.invalid/app.apk"
        val release = appUpdatePreviewRelease.copy(assets = listOf(AppReleaseAsset(name, download)))
        val opened = mutableListOf<String>()
        val uri = object : UriHandler {
            override fun openUri(uri: String) { opened += uri; if (uri == download) error("unsupported handler") }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uri) {
                CopyManga_NeoTheme { AppUpdateDialog(release, {}, AppReleaseTarget.Android(listOf("arm64-v8a"))) }
            }
        }
        onNodeWithText("GitHub").performClick()
        assertEquals(listOf(download, release.pageUrl), opened)
    }

    private fun renderPreview(darkTheme: Boolean) = runSkikoComposeUiTest(size = Size(824f, 1280f)) {
        var dismissed = 0
        var openedUrl: String? = null
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedUrl = uri }
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f), LocalUriHandler provides uriHandler) {
                AppUpdateDialogPreviewContent(darkTheme) { dismissed++ }
            }
        }
        onNodeWithText("发现新版本").assertIsDisplayed()
        onNodeWithText("1.2.3").assertIsDisplayed()
        onNodeWithText("更新说明").assertDoesNotExist()
        onNodeWithText(appUpdatePreviewRelease.releaseNotes).assertIsDisplayed()
        onNodeWithText("取消").assertIsDisplayed()
        onNodeWithText("GitHub").assertIsDisplayed()
        val pixels = onAllNodes(isRoot())[1].captureToImage().toPixelMap()
        val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            bitmap.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File("build/reports/update-preview/update-${if (darkTheme) "dark" else "light"}.png")
        file.parentFile.mkdirs()
        ImageIO.write(bitmap, "png", file)
        onNodeWithText(if (darkTheme) "取消" else "GitHub").performClick()
        assertEquals(1, dismissed)
        assertEquals(if (darkTheme) null else appUpdatePreviewRelease.pageUrl, openedUrl)
    }
}
