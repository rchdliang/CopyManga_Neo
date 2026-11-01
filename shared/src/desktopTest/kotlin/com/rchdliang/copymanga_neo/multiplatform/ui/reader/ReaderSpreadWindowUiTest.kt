package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.jetbrains.skia.Bitmap
import kotlin.test.*

/** 裁剪旧章节时逐帧检查封面，不能只验证滚动恢复后的最终页码。 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReaderSpreadWindowUiTest {
    @Test fun doubleLayoutKeepsChapterCoverWhileTrimmingOldChapter() = keepsCover("DOUBLE")
    @Test fun autoLayoutKeepsChapterCoverWhileTrimmingOldChapter() = keepsCover("AUTO")

    private fun keepsCover(mode: String) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        fun chapter(id: Int) = (0..4).map {
            ReaderPageEntry("$id:image:$it", "$id", "Chapter $id", "fixture-$id-$it", it, 5)
        }
        var pages by mutableStateOf(chapter(1) + chapter(2) + chapter(3))
        var pager: PagerState? = null
        var zoom: ReaderZoomState? = null
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(1100, 700, coroutineContext = coroutineContext) {
                val spreads = readerSpreads(pages, readerUsesSpreads(mode, 1100f, 700f))
                val state = rememberPagerState(initialPage = spreads.spreadIndex(10)) { spreads.size }
                SideEffect { pager = state }
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ReaderSpreadContent(pages, spreads, state, true, ContentScale.Fit,
                            loader, ReaderToolsSettings(pageLayout = mode), {}, {}, {}, {}, {}, {},
                            { _, _, _ -> }, { zoom = it }, {})
                    }
                }
            }
            scene = raster
            var time = 0L
            fun frame(): Int {
                runCurrent()
                time += 16_000_000
                val color = raster.render(time).use { image ->
                    Bitmap.makeFromImage(image).use { it.getColor(550, 350) }
                }
                runCurrent()
                return color
            }
            repeat(8) { frame() }
            assertEquals(6, pager!!.currentPage)
            val coverColor = frame()
            assertNotEquals(0xff000000.toInt(), coverColor)
            val coverZoom = zoom

            // 新章节第一页落定后移除最早章节，旧索引 6 暂时超出新分组的 0..5。
            pages = pages.filter { it.chapterUuid != "1" }
            repeat(8) { index ->
                assertEquals(coverColor, frame(), "Chapter cover disappeared in frame $index ($mode)")
            }
            assertEquals(3, pager.currentPage)
            assertSame(coverZoom, zoom, "The existing chapter viewport must survive window trimming")
        } finally {
            scene?.close()
            loader.shutdown()
            Dispatchers.resetMain()
        }
    }
}
