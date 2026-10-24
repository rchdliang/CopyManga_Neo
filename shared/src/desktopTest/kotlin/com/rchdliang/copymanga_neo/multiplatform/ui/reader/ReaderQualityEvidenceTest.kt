package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.rchdliang.copymanga_neo.domain.reading.*
import com.rchdliang.copymanga_neo.multiplatform.sr.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

/** Small synthetic fixtures only. This does not establish tablet full-page throughput. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReaderQualityEvidenceTest {
    @Test fun sameSizeQualityAndBoundedPhaseMeasurements() = runTest {
        val resources = javaClass.classLoader
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assumeTrue(resources.getResource("reader-sr/windows-x64/manifest.properties") != null)
        val output = File("build/reports/reader-quality-evidence").apply { mkdirs() }
        val cache = File("build/sr-quality-measurement").apply { mkdirs() }
        val samples = listOf("small-text", "screentone", "thin-lines", "color-page")
        val originals = samples.associateWith { resources.getResourceAsStream("sr/$it-source.png")!!.use { it.readBytes() } }
        val totals = mutableListOf<String>()
        val results = mutableMapOf<String,ByteArray>()
        withContext(Dispatchers.IO) {
            withTimeout(120_000) {
                val bundle = SrBundle("windows-x64",cache,{resources.getResourceAsStream(it)})
                bundle.models.download() // Explicit integration consent, never called by production UI composition.
                assertEquals(SrModelState.Ready,bundle.models.state.value)
                val phases = linkedMapOf<String,Long>()
                fun <T> measure(name:String,block:()->T):T {val start=System.nanoTime();return try {block()} finally {phases[name]=(phases[name]?:0)+(System.nanoTime()-start)}}
                val codec=DesktopSrCodec()
                val backend=NativeReaderSrBackend(bundle,{name -> measure("sourceRead"){originals.getValue(name)}},
                    object:SrRasterCodec {
                        override fun dimensions(bytes:ByteArray)=measure("metadata"){codec.dimensions(bytes)}
                        override fun decode(bytes:ByteArray)=measure("decodeRgba"){codec.decode(bytes)}
                        override fun encode(raster:SrRaster)=measure("encodePng"){codec.encode(raster)}
                    }, { binary ->
                        val worker=DesktopSrWorker(binary)
                        object:SrWorker {
                            override suspend fun probe(models:File,gpu:Boolean):Int {val start=System.nanoTime();return try {worker.probe(models,gpu)} finally {phases[if(gpu)"gpuProbe" else "cpuProbe"]=System.nanoTime()-start}}
                            override suspend fun run(models:File,gpu:Int,input:File,output:File) {val start=System.nanoTime();try {worker.run(models,gpu,input,output)} finally {phases["workerProcessAndNative"]=System.nanoTime()-start}}
                        }
                    })
                suspend fun compute(name:String,mode:SrExecutionMode,pass:String):SrArtifact {
                    phases.clear();val start=System.nanoTime()
                    val source=backend.source(SrPage("fixture-$name",name))
                    val artifact=withTimeout(25_000){backend.upscale(SrRequest(source,ReaderSrModel.model,SrParameters(mode=mode,cpuFallback=false)))}
                    assertEquals(mode,artifact.backend);assertFalse(artifact.cpuFallback)
                    val elapsed=System.nanoTime()-start
                    val measured=phases.values.sum()
                    val row="{\"sample\":\"$name\",\"backend\":\"${artifact.backend}\",\"pass\":\"$pass\",\"input\":\"${source.width}x${source.height}\",\"tile\":32,\"totalMs\":${elapsed/1e6},\"otherPrepareHashFileIoMs\":${(elapsed-measured)/1e6},\"phasesMs\":{${phases.entries.joinToString(","){ "\"${it.key}\":${it.value/1e6}" }}}"
                    val record = row + "}"
                    kotlinx.serialization.json.Json.parseToJsonElement(record)
                    totals+=record;println("SR_PHASE $record")
                    return artifact
                }
                compute("small-text",SrExecutionMode.CPU,"first-compute")
                compute("small-text",SrExecutionMode.CPU,"second-compute-no-result-cache")
                // GPU failure is recorded, never silently replaced with CPU in this comparison.
                for(name in samples) {
                    try { results[name]=compute(name,SrExecutionMode.GPU,"compute-no-result-cache").png }
                    catch(cancelled:CancellationException){throw cancelled}
                    catch(error:Exception){totals+="{\"sample\":\"$name\",\"backend\":\"GPU\",\"result\":\"unavailable-or-failed\"}";break}
                }
            }
        }
        File(output,"phase-timings.json").writeText(totals.joinToString(",\n","[\n","\n]"))
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for(name in samples) {
                val variants=listOf("original" to (originals.getValue(name) to 0f),"sharpen-020" to (originals.getValue(name) to .20f))+
                    listOfNotNull(results[name]?.let { "ai-gpu" to (it to 0f) })
                for((variant,input) in variants) {
                    // All three use the same 384x256 Compose image, Fit scale and actual sharpening modifier.
                    Image.makeFromEncoded(input.first).use { source ->
                        val bitmap=source.toComposeImageBitmap()
                        val scene=ImageComposeScene(384,256,coroutineContext=coroutineContext) {
                            ComposeImage(bitmap,null,Modifier.fillMaxSize().readerSharpen(input.second))
                        }
                        try {
                            repeat(3){runCurrent();scene.render((it+1)*16_000_000L).close();runCurrent()}
                            scene.render(64_000_000).use { image -> image.encodeToData()!!.use {File(output,"$name-$variant.png").writeBytes(it.bytes)} }
                        } finally {scene.close()}
                    }
                }
            }
        } finally {Dispatchers.resetMain()}
        assertTrue(File(output,"small-text-original.png").isFile)
    }
}
