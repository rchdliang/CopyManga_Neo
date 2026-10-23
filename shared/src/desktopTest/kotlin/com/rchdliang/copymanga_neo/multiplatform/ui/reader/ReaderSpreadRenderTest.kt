package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.request.*
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

/** Software-rendered production components with synthetic local pages, not device screenshots. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReaderSpreadRenderTest {
    @Test fun spreadUsesEachLeafDecodeBoundsRetainsDiskIdentityAndRenders() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val directory = Files.createTempDirectory("spread-fixture")
        val paths = (0..2).map { number ->
            directory.resolve("$number.png").also { path ->
                val bitmap = BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB)
                val graphics = bitmap.createGraphics()
                graphics.color = java.awt.Color(242 - number * 10, 235, 218)
                graphics.fillRect(0, 0, 900, 1200)
                graphics.color = java.awt.Color(35, 48, 68)
                graphics.stroke = java.awt.BasicStroke(8f)
                graphics.drawRect(45, 45, 810, 500); graphics.drawRect(45, 580, 810, 575)
                graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 68)
                graphics.drawString("PAGE ${number + 1}", 190, 300)
                graphics.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 30)
                graphics.drawString("Synthetic reader test fixture", 180, 900)
                graphics.dispose(); ImageIO.write(bitmap, "png", path.toFile())
            }
        }
        val loads = mutableListOf<SuccessResult>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).coroutineContext(dispatcher).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult = chain.proceed().also {
                    if (it is SuccessResult) loads += it
                }
            })
        }.build()
        val entries = paths.mapIndexed { index, path -> ReaderPageEntry("c:$index", "c", "Fixture", path.toString(), index, 3) }
        var scene: ImageComposeScene? = null
        var zoom: ReaderZoomState? = null
        val ready = mutableSetOf<String>()
        try {
            val raster = ImageComposeScene(1200, 800, coroutineContext = coroutineContext) {
                val pager = rememberPagerState(initialPage = 1) { 2 }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ReaderSpreadContent(entries, readerSpreads(entries, true), pager, true, ContentScale.Fit,
                        loader, ReaderToolsSettings(), {}, {}, {}, {}, {}, { ready += it }, { _, _, _ -> },
                        { zoom = it }, {}, windowsReaderImageQuality)
                }
            }
            scene = raster
            var time = 0L
            fun frames() { repeat(6) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() } }
            frames()
            assertTrue("c:1" in ready && "c:2" in ready)
            val initial = loads.filter { it.request.data.toString().endsWith("1.png") }.last()
            assertEquals(600, initial.image.width)
            val steadyLoads = loads.size
            frames(); frames()
            assertEquals(steadyLoads, loads.size, "Steady display must not repeatedly decode")
            val artifact = Path.of("build/reports/reader-spread-runtime.png")
            Files.createDirectories(artifact.parent)
            raster.render(time + 16_000_000).use { frame -> frame.encodeToData()!!.use { Files.write(artifact, it.bytes) } }
            zoom!!.toggleZoom(Offset(750f, 300f), 5f)
            frames(); advanceTimeBy(150); frames()
            val upgraded = loads.filter { it.request.data.toString().endsWith("1.png") }.last()
            assertEquals(900, upgraded.image.width)
            assertEquals(initial.request.data, upgraded.request.data)
            assertTrue(loads.all { it.memoryCacheKey?.extras?.get("reader_sampling") == "windows-cubic-v1" })
        } finally {
            scene?.close(); loader.shutdown(); Dispatchers.resetMain()
            paths.forEach(Files::deleteIfExists); Files.deleteIfExists(directory)
        }
    }

    @Test fun leavingSpreadCancelsBothLeafRequestsWithoutReportingReady() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var mounted by mutableStateOf(true)
        var started = 0
        var cancelled = 0
        var ready = 0
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).coroutineContext(dispatcher).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    started++
                    try { awaitCancellation() } finally { cancelled++ }
                }
            })
        }.build()
        val entries = (0..2).map { ReaderPageEntry("c:$it", "c", "Fixture", "fixture-$it", it, 3) }
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(1200, 800, coroutineContext = coroutineContext) {
                if (mounted) {
                    val pager = rememberPagerState(initialPage = 1) { 2 }
                    ReaderSpreadContent(entries, readerSpreads(entries, true), pager, false, ContentScale.Fit,
                        loader, ReaderToolsSettings(), {}, {}, {}, {}, {}, { ready++ }, { _, _, _ -> }, {}, {})
                }
            }
            scene = raster
            var time = 0L
            fun frames() { repeat(5) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() } }
            frames(); assertTrue(started >= 2)
            mounted = false; frames()
            assertEquals(started, cancelled)
            assertEquals(0, ready)
        } finally { scene?.close(); loader.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun renderCompactSettingsSurface() = runTest {
        val raster = ImageComposeScene(480, 440, coroutineContext = coroutineContext) {
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(24.dp)) {
                        ReaderSpreadOptions(ReaderToolsSettings(pageLayout = "AUTO"), {}, "c", 2, 20, true, false)
                    }
                }
            }
        }
        try {
            runCurrent()
            val artifact = Path.of("build/reports/reader-spread-settings-runtime.png")
            Files.createDirectories(artifact.parent)
            raster.render(16_000_000).use { frame -> frame.encodeToData()!!.use { Files.write(artifact, it.bytes) } }
        } finally { raster.close() }
    }
}
