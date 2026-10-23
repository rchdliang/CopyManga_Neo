package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import coil3.ImageLoader
import com.rchdliang.copymanga_neo.data.image.portable.readCoilReaderImageCache
import com.rchdliang.copymanga_neo.domain.reading.ReaderSrPlatform
import com.rchdliang.copymanga_neo.multiplatform.sr.*
import java.io.File
import java.net.URI

@Composable
internal actual fun rememberReaderSrPlatform(loader: ImageLoader): ReaderSrPlatform = remember(loader) {
    val os = System.getProperty("os.name").lowercase()
    val arch = when (System.getProperty("os.arch").lowercase()) { "amd64", "x86_64" -> "x64"; "aarch64", "arm64" -> "arm64"; else -> "unsupported" }
    val platform = (if (os.startsWith("windows")) "windows" else if (os.startsWith("linux")) "linux" else "unsupported") + "-" + arch
    val cache = File(System.getProperty("user.home"), ".cache/copymanga-neo")
    NativeReaderSrBackend(
        SrBundle(platform, cache, { ReaderSrPlatform::class.java.classLoader.getResourceAsStream(it) }),
        readSource = { source ->
            if (source.startsWith("http://") || source.startsWith("https://")) {
                readCoilReaderImageCache(loader.diskCache, source, NativeReaderSrBackend.MAX_INPUT_BYTES)
                    ?: error("原图缓存尚不可用，继续显示原图")
            } else {
                val file = if (source.startsWith("file:")) File(URI(source)) else File(source)
                require(file.isFile && file.length() <= NativeReaderSrBackend.MAX_INPUT_BYTES) { "本地原图不可用或过大" }
                file.inputStream().use { it.readLimited(NativeReaderSrBackend.MAX_INPUT_BYTES) }
            }
        }, codec = DesktopSrCodec(), workerFactory = { DesktopSrWorker(it) })
}
