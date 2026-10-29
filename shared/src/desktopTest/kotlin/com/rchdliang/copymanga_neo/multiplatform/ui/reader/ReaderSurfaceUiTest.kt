package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

/** 真实生产组件的软件渲染；测试图不包含用户漫画或设备资料。 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ReaderSurfaceUiTest {
    @Test fun splitTestImageMeetsAtSpineInBothDirectionsAndPreservesOriginalBorders() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val directory = Files.createTempDirectory("reader-seam-test").toFile()
        try {
            for ((rtl, bordered, width, height) in listOf(
                listOf(0,0,1200,600), listOf(1,0,1200,600), listOf(0,1,1200,600), listOf(0,0,960,700))) {
                val full = BufferedImage(1200,1200,BufferedImage.TYPE_INT_RGB)
                full.createGraphics().apply {
                    color=java.awt.Color(36,146,216);fillRect(0,0,1200,1200)
                    color=java.awt.Color(240,28,40);fillRect(0,580,1200,40)
                    dispose()
                }
                val pair=(0..1).map { side ->
                    val page=BufferedImage(600,1200,BufferedImage.TYPE_INT_RGB)
                    page.createGraphics().apply {
                        drawImage(full,0,0,600,1200,side*600,0,(side+1)*600,1200,null)
                        if(bordered==1) { color=java.awt.Color.WHITE;stroke=java.awt.BasicStroke(32f);drawRect(0,0,600,1200) }
                        dispose()
                    }
                    File(directory,"$rtl-$bordered-$width-$side.png").also { ImageIO.write(page,"png",it) }
                }.let { if(rtl==1) it.reversed() else it }
                val entries=(listOf(pair[0])+pair).mapIndexed { i,f -> ReaderPageEntry("c:$i","c","Fixture",f.path,i,3) }
                val loader=ImageLoader.Builder(PlatformContext.INSTANCE).coroutineContext(dispatcher).build()
                var zoom:ReaderZoomState?=null
                val ready=mutableSetOf<String>()
                val scene=ImageComposeScene(width,height,coroutineContext=coroutineContext) {
                    val pager=rememberPagerState(initialPage=1) { 2 }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ReaderSpreadContent(entries,readerSpreads(entries,true),pager,rtl==1,ContentScale.Fit,
                            loader,ReaderToolsSettings(),{},{},{},{},{},{ready+=it},{_,_,_->},{zoom=it},{})
                    }
                }
                try {
                    var time=0L
                    fun frames() { repeat(8) { runCurrent();time+=16_000_000;scene.render(time).close();runCurrent() } }
                    frames()
                    assertTrue("c:1" in ready && "c:2" in ready)
                    fun image():BufferedImage=scene.render(time+16_000_000).use { frame ->
                        frame.encodeToData()!!.use { ImageIO.read(ByteArrayInputStream(it.bytes)) }
                    }
                    val initial=image()
                    for(x in listOf(width/2-2,width/2+2)) {
                        val pixel=java.awt.Color(initial.getRGB(x,height/2))
                        if(bordered==1) assertTrue(pixel.red>240 && pixel.green>240 && pixel.blue>240)
                        else assertTrue(pixel.red>200 && pixel.green<65 && pixel.blue<80,"Spine has no added gap: $pixel")
                    }
                    assertEquals(0,initial.getRGB(0,height/2) and 0xffffff,"Extra space stays outside the pages")
                    val name="spread-spine-${if(rtl==1) "rtl" else "ltr"}-${if(bordered==1) "borders" else "continuous"}-$width"
                    val output=File("build/reports/reader-settings-ui/$name.png");output.parentFile.mkdirs()
                    ImageIO.write(initial,"png",output)
                    zoom!!.toggleZoom(Offset(width/2f,height/2f),5f);zoom.pan(Offset(50f,20f));frames()
                    assertTrue(zoom.zoomed);assertTrue(zoom.offset!=Offset.Zero)
                    zoom.reset();frames()
                    assertEquals(1f,zoom.scale);assertEquals(Offset.Zero,zoom.offset)
                    assertEquals(initial.getRGB(width/2,height/2),image().getRGB(width/2,height/2))
                } finally { scene.close();loader.shutdown() }
            }
        } finally { Dispatchers.resetMain();directory.deleteRecursively() }
    }

    @Test fun outlinedOverlayHasNoScrimOnBlackWhiteAndColorBackgrounds() = runSkikoComposeUiTest(size=Size(960f,600f)) {
        var background by mutableStateOf(0)
        var expanded by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(if(background==0) Color.White else Color.Black)) {
                    if(background==2) Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color(0xffc84286))
                        drawRect(Color(0xff239dc0),topLeft=Offset(size.width/2,0f),size=Size(size.width/2,size.height))
                    }
                    if(expanded) ReaderBottomBar(24,2f,{},{},{},{},{},{},{},true,null,false,{},Modifier.align(Alignment.BottomCenter))
                    else {
                        ReaderStatusOverlay("09:41", ReaderBatteryStatus(85), Modifier.align(Alignment.TopCenter), pages = "2–3 / 24")
                        ReaderRestingOverlay("连续测试图 · Chapter 12", Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        for(index in 0..2) {
            runOnIdle { background=index;expanded=false }
            onNodeWithText("2–3 / 24").assertExists()
            val pixels=onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            val expected=when(index) { 0->Color.White;1->Color.Black;else->Color(0xffc84286) }
            assertEquals(expected,pixels[10,pixels.height-2],"No full-width bottom scrim")
            saveRoot("reader-overlay-${listOf("white","black","color")[index]}")
        }
        runOnIdle { expanded=true }
        onNodeWithContentDescription("目录").assertExists()
        saveRoot("reader-expanded-toolbar-background")
    }

    private fun ComposeUiTest.saveRoot(name:String) {
        val pixels=onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        val image=BufferedImage(pixels.width,pixels.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        val output=File("build/reports/reader-settings-ui/$name.png");output.parentFile.mkdirs();ImageIO.write(image,"png",output)
    }
}
