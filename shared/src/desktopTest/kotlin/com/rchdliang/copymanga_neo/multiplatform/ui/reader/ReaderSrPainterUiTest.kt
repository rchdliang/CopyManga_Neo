package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.io.File
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderSrPainterUiTest {
    private fun png():ByteArray=ByteArrayOutputStream().use { out ->
        val image=BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB)
        for(y in 0..31) for(x in 0..31) image.setRGB(x,y,0xff0000ff.toInt())
        ImageIO.write(image,"png",out);out.toByteArray()
    }
    @Test fun outputIsNotAppliedUntilDecodedAndActuallyDrawn()=checkPainter(false)
    @Test fun corruptEnhancedImageKeepsOriginalAndReportsFailure()=checkPainter(true)
    private fun checkPainter(corrupt:Boolean)=runSkikoComposeUiTest(size=Size(400f,300f)) {
        val released=CompletableDeferred<Unit>()
        var drawImage by mutableStateOf(false)
        lateinit var session:ReaderSrSession
        val bytes=png().let { if(corrupt) it.copyOf(33) else it }
        setContent {
            val scope=rememberCoroutineScope()
            session=remember { ReaderSrSession(scope,object:ReaderSrPlatform {
                override val budgetBytes=96L*1024*1024;override val workingBytes=0L
                override val unavailableReason:String?=null;override val detail=MutableStateFlow("test")
                override suspend fun source(page:SrPage)=SrSource(page.id,"a".repeat(64),16,16)
                override suspend fun upscale(request:SrRequest):SrArtifact {released.await();return SrArtifact(bytes,32,32,SrExecutionMode.GPU)}
            }) }
            DisposableEffect(session) {onDispose {session.close()}}
            LaunchedEffect(session) {session.select(SrPage("A","A"));session.settings.value=SrUserSettings(enabled=true)}
            val image=readerSrPainter(session,"A",0,remember {ColorPainter(Color.Red)})
            val state=readerSrPresentation(session,"A",true)
            MaterialTheme { Surface { Column {
                if(drawImage) Image(image.painter,null,Modifier.size(100.dp).testTag("actual-image").readerSrDrawn(image))
                Text(state.shortLabel() ?: "off")
            } } }
        }
        waitUntil(timeoutMillis = 10000) { session.frame.value?.status == SrStatus.Processing }
        runOnIdle {drawImage=true}
        val original=onNodeWithTag("actual-image").captureToImage().toPixelMap()[50,50]
        assertTrue(original.red>0.9f && original.blue<0.1f)
        if(!corrupt) saveActual("processing-original")
        runOnIdle {drawImage=false;released.complete(Unit)}
        waitUntil(timeoutMillis = 10000) {session.frame.value?.status is SrStatus.Ready}
        // Decoding may already have completed but no output image has been drawn.
        onNodeWithText("AI 已应用").assertDoesNotExist()
        onNodeWithText("AI 未应用 · 载入中").assertExists()
        runOnIdle {drawImage=true}
        waitUntil(timeoutMillis = 10000) {session.presentationReceipt.value?.image == if(corrupt) SrRenderedImage.DECODE_FAILED else SrRenderedImage.ENHANCED}
        onNodeWithText(if(corrupt) "AI 未应用 · 处理失败" else "AI 已应用").assertExists()
        saveActual(if(corrupt) "decode-failed-original" else "decoded-and-drawn-enhanced")
        val pixels=onNodeWithTag("actual-image").captureToImage().toPixelMap()[50,50]
        if(corrupt) assertTrue(pixels.red>0.9f && pixels.blue<0.1f) else assertTrue(pixels.blue>0.9f && pixels.red<0.1f)
        runOnIdle {session.settings.value=session.settings.value.copy(enabled=false)}
        onNodeWithText("AI 已应用").assertDoesNotExist()
        val disabled=onNodeWithTag("actual-image").captureToImage().toPixelMap()[50,50]
        assertTrue(disabled.red>0.9f && disabled.blue<0.1f)
    }
    private fun ComposeUiTest.saveActual(name:String) {
        val pixels=onRoot().captureToImage().toPixelMap()
        val image=BufferedImage(pixels.width,pixels.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        val file=File("build/reports/reader-inline-design/actual-painter-$name.png")
        file.parentFile.mkdirs();ImageIO.write(image,"png",file)
    }

}
