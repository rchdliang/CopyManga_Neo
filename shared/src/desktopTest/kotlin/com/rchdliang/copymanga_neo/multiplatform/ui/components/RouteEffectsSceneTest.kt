package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Synthetic composition only: no host window, accounts, filesystem or network. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class RouteEffectsSceneTest {
    @Test fun previewCannotStartEffectsOrFlushAndCommitDoesNotRestartVisualRestoration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        var active by mutableStateOf(false)
        var mounted by mutableStateOf(true)
        var starts = 0
        var stops = 0
        var flushes = 0
        var restorations = 0
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(width = 320, height = 240, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalRouteActive provides active) {
                    if (mounted) {
                        LaunchedEffect(Unit) { restorations++ }
                        ActiveRouteLaunchedEffect(Unit) {
                            starts++
                            try { awaitCancellation() } finally { stops++ }
                        }
                        ActiveRouteProgressFlushEffect(Unit) { flushes++ }
                    }
                }
            }
            scene = raster
            var time = 0L
            fun frame() { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() }
            repeat(2) { frame() }
            assertEquals(0, starts)
            mounted = false
            repeat(2) { frame() }
            assertEquals(0, flushes, "Canceled preview must not flush")
            mounted = true
            repeat(2) { frame() }
            val restored = restorations
            active = true
            repeat(2) { frame() }
            assertEquals(1, starts)
            assertEquals(restored, restorations, "Committing must not restart anchor restoration")
            owner.update(Lifecycle.State.STARTED, true)
            frame()
            assertEquals(1, flushes)
            active = false
            repeat(2) { frame() }
            assertEquals(1, stops)
            assertEquals(1, flushes, "Pause followed by departure is a single flush")
            active = true
            owner.update(Lifecycle.State.RESUMED, true)
            repeat(2) { frame() }
            assertEquals(2, starts)
            active = false
            repeat(2) { frame() }
            assertEquals(2, flushes, "A genuine departure flushes without requiring host pause")
            mounted = false
            repeat(2) { frame() }
            assertEquals(2, flushes)
        } finally {
            scene?.close()
            owner.destroy()
            Dispatchers.resetMain()
        }
    }
    @Test fun readerPreviewDoesNotReportAndActivationKeepsRestoredPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        var active by mutableStateOf(false)
        val pending = mutableStateOf<Int?>(2)
        val positions = mutableListOf<Int>()
        var completions = 0
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(width = 320, height = 480, coroutineContext = coroutineContext) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalRouteActive provides active,
                    androidx.compose.ui.platform.LocalInspectionMode provides true
                ) {
                    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme {
                        com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderSuccessContent(
                            images = listOf("synthetic-one", "synthetic-two", "synthetic-three"),
                            chapterName = "Synthetic chapter", readingDirection = "LEFT_TO_RIGHT",
                            pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                            onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false,
                            hasNextChapter = false, onRefresh = {}, onReadingDirectionChange = {},
                            onPageScaleChange = {}, onScreenOrientationChange = {},
                            pendingInitialPage = pending,
                            onPosition = { _, page, _, _ -> positions += page },
                            onCompleted = { completions++ }
                        )
                    }
                }
            }
            scene = raster
            var time = 0L
            fun frame() { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() }
            repeat(4) { frame() }
            assertTrue(positions.isEmpty())
            assertEquals(0, completions)
            active = true
            repeat(4) { frame() }
            assertTrue(positions.isNotEmpty())
            assertEquals(2, positions.last(), "Activation must not reset the restored initial page")
            val beforePause = positions.size
            owner.update(Lifecycle.State.STARTED, true)
            pending.value = 1 // A pending layout/navigation completion while the host is paused.
            repeat(4) { frame() }
            assertEquals(beforePause, positions.size, "No reporting after pause has flushed")
            owner.update(Lifecycle.State.RESUMED, true)
            repeat(4) { frame() }
            assertEquals(1, positions.last(), "Resume reports the preserved viewport without resetting it")
            val recorded = positions.size
            active = false
            repeat(4) { frame() }
            assertEquals(recorded, positions.size)
            assertEquals(0, completions)
        } finally {
            scene?.close()
            owner.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test fun inactiveNativeDialogDoesNotNeedAWindowLayerOrConsumeDismissal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var dismissed = 0
        var refreshed = 0
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(width = 320, height = 240, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalRouteActive provides false) {
                    com.rchdliang.copymanga_neo.multiplatform.ui.profile.OfficialAppDiagnosticsDialog(
                        "synthetic", false, { refreshed++ }, { dismissed++ }
                    )
                }
            }
            scene = raster
            runCurrent()
            raster.render(16_000_000).close()
            runCurrent()
            assertEquals(0, dismissed)
            assertEquals(0, refreshed)
        } finally { scene?.close(); Dispatchers.resetMain() }
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    @Test fun inactiveImageRequestPreservesDataAndKeysButForbidsNetworkReads() {
        val context = coil3.PlatformContext.INSTANCE
        val original = coil3.request.ImageRequest.Builder(context)
            .data("https://cdn.example/synthetic.jpg")
            .memoryCacheKey("synthetic-memory").diskCacheKey("synthetic-disk").build()
        val preview = routeImageModel(original, context, false) as coil3.request.ImageRequest
        assertEquals(original.data, preview.data)
        assertEquals(original.memoryCacheKey, preview.memoryCacheKey)
        assertEquals(original.diskCacheKey, preview.diskCacheKey)
        assertEquals(coil3.request.CachePolicy.READ_ONLY, preview.diskCachePolicy)
        assertEquals(coil3.request.CachePolicy.DISABLED, preview.networkCachePolicy)
        assertTrue(coil3.compose.AsyncImageModelEqualityDelegate.Default.equals(original, preview),
            "Coil's default ignores policy changes")
        assertFalse(RouteImageModelEquality.equals(original, preview),
            "The scoped delegate must retry an inactive cache miss after activation")
        assertSame(original, routeImageModel(original, context, true))
        assertNull(routeImageModel(null, context, false))
    }

    @Test fun policyChangeRestartsRealCoilPainterWithoutChangingCacheKeys() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = coil3.PlatformContext.INSTANCE
        val policies = mutableListOf<coil3.request.CachePolicy>()
        val loader = coil3.ImageLoader.Builder(context)
            .interceptorCoroutineContext(StandardTestDispatcher(testScheduler))
            .components {
            add(object : coil3.intercept.Interceptor {
                override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): coil3.request.ImageResult {
                    policies += chain.request.networkCachePolicy
                    return coil3.request.ErrorResult(null, chain.request, okio.IOException("Synthetic miss"))
                }
            })
        }.build()
        val original = coil3.request.ImageRequest.Builder(context).data("synthetic")
            .size(32, 32).memoryCacheKey("same-key").build()
        var active by mutableStateOf(false)
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(width = 32, height = 32, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalRouteActive provides active) {
                    RouteImageScope {
                        coil3.compose.AsyncImage(model = routeImageModel(original), imageLoader = loader,
                            contentDescription = null)
                    }
                }
            }
            scene = raster
            var time = 0L
            fun frame() { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() }
            repeat(4) { frame() }
            assertEquals(listOf(coil3.request.CachePolicy.DISABLED), policies)
            active = true
            repeat(4) { frame() }
            assertEquals(listOf(coil3.request.CachePolicy.DISABLED, coil3.request.CachePolicy.ENABLED), policies)
        } finally { scene?.close(); loader.shutdown(); Dispatchers.resetMain() }
    }

}
