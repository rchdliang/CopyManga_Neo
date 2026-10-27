package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.BitmapImage
import coil3.Image
import com.rchdliang.copymanga_neo.multiplatform.restore.JvmVulkanRestoreEngine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

@Composable
internal actual fun rememberReaderRestoreEngine(): ReaderRestoreEngine {
    val engine = remember {
        val os = System.getProperty("os.name").lowercase()
        if (os.startsWith("windows") || os.startsWith("linux")) DesktopReaderRestoreEngine()
        else UnsupportedReaderRestoreEngine("当前桌面平台暂不支持 Vulkan 修复")
    }
    DisposableEffect(engine) { onDispose { engine.close() } }
    return engine
}

internal class DesktopReaderRestoreEngine : JvmVulkanRestoreEngine() {
    override fun loadLibrary() = DesktopVulkanLibrary.load()
    override fun readModel(name: String) = DesktopVulkanLibrary.resource("reader-restore/$name")
    override fun generation(source: Image): Int = (source as? BitmapImage)?.bitmap?.generationId ?: 0
    override fun sourcePixels(source: Image): ByteBuffer {
        val image = (source as? BitmapImage)?.bitmap ?: throw ReaderRestoreUnavailable("此图片类型暂不支持修复")
        val info = ImageInfo(source.width, source.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB)
        val bytes = checkNotNull(image.readPixels(info, source.width * 4))
        for (i in 3 until bytes.size step 4) if ((bytes[i].toInt() and 255) != 255) {
            throw ReaderRestoreUnavailable("透明图片暂用原图")
        }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
    }
    override fun resultImage(pixels: ByteBuffer, width: Int, height: Int): ImageBitmap {
        val bytes = ByteArray(width * height * 4)
        pixels.get(bytes)
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE, ColorSpace.sRGB)
        return org.jetbrains.skia.Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
    }
    override fun logFailure(error: Throwable) { System.err.println("Reader Vulkan: ${error.javaClass.simpleName}: ${error.message}") }
}

internal object DesktopVulkanLibrary {
    private var loaded = false
    fun resource(path: String): ByteArray = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
        "Missing bundled Vulkan resource: $path"
    }.use { it.readBytes() }.also { require(it.size <= 16 * 1024 * 1024) }

    @Synchronized fun load() {
        if (loaded) return
        val os = System.getProperty("os.name").lowercase()
        val arch = when (System.getProperty("os.arch").lowercase()) { "amd64", "x86_64" -> "x64"; "aarch64", "arm64" -> "arm64"; else -> error("Unsupported CPU architecture") }
        val platform = when { os.startsWith("windows") -> "windows-$arch"; os.startsWith("linux") -> "linux-$arch"; else -> error("Vulkan desktop backend supports Windows and Linux") }
        val name = System.mapLibraryName("copymanga_restore")
        val bytes = resource("reader-restore/$platform/$name")
        fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val hash = digest(bytes)
        val directory = File(System.getProperty("user.home"), ".cache/copymanga-neo/reader-restore-runtime/$hash")
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, name)
        RandomAccessFile(File(directory, ".lock"), "rw").channel.use { channel ->
            channel.lock().use {
                if (!target.isFile || target.length() != bytes.size.toLong() || digest(target.readBytes()) != hash) {
                    val temporary = File.createTempFile("restore-", ".tmp", directory)
                    try {
                        temporary.writeBytes(bytes)
                        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } finally { temporary.delete() }
                }
            }
        }
        System.load(target.absolutePath)
        loaded = true
    }
}
