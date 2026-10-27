package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.Image
import com.rchdliang.copymanga_neo.multiplatform.restore.JvmVulkanRestoreEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
internal actual fun rememberReaderRestoreEngine(): ReaderRestoreEngine {
    val context = LocalContext.current.applicationContext
    val engine = remember(context) { AndroidReaderRestoreEngine(context) }
    DisposableEffect(engine) { onDispose { engine.close() } }
    return engine
}

internal class AndroidReaderRestoreEngine(private val context: Context) : JvmVulkanRestoreEngine() {
    override fun loadLibrary() = AndroidVulkanLibrary.load()
    override fun readModel(name: String): ByteArray = context.assets.open("reader-restore/$name").use { it.readBytes() }
    override fun generation(source: Image): Int = (source as? BitmapImage)?.bitmap?.generationId ?: 0
    override fun sourcePixels(source: Image): ByteBuffer {
        val bitmap = (source as? BitmapImage)?.bitmap ?: throw ReaderRestoreUnavailable("此图片类型暂不支持修复")
        if (bitmap.isRecycled || bitmap.colorSpace?.isSrgb == false) throw ReaderRestoreUnavailable("此图片格式暂用原图")
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: throw ReaderRestoreUnavailable("无法读取图片")
        try {
            if (copy.rowBytes != copy.width * 4) throw ReaderRestoreUnavailable("此位图格式暂用原图")
            val pixels = ByteBuffer.allocateDirect(copy.width * copy.height * 4).order(ByteOrder.nativeOrder())
            copy.copyPixelsToBuffer(pixels)
            for (i in 3 until pixels.capacity() step 4) if ((pixels.get(i).toInt() and 255) != 255) {
                throw ReaderRestoreUnavailable("透明图片暂用原图")
            }
            pixels.rewind()
            return pixels
        } finally { copy.recycle() }
    }
    override fun resultImage(pixels: ByteBuffer, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(pixels)
            setHasAlpha(false)
        }.asImageBitmap()
    override fun logFailure(error: Throwable) { Log.w("ReaderVulkan", "Restoration failed", error) }
}
private object AndroidVulkanLibrary {
    private var loaded = false
    @Synchronized fun load() {
        if (!loaded) { System.loadLibrary("copymanga_restore"); loaded = true }
    }
}
