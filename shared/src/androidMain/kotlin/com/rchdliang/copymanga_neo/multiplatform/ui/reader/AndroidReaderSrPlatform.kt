package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import com.rchdliang.copymanga_neo.data.image.portable.readCoilReaderImageCache
import com.rchdliang.copymanga_neo.domain.reading.ReaderSrPlatform
import com.rchdliang.copymanga_neo.multiplatform.sr.*
import java.io.File
import java.io.IOException

@Composable
internal actual fun rememberReaderSrPlatform(loader: ImageLoader): ReaderSrPlatform {
    val context = LocalPlatformContext.current.applicationContext
    return remember(context, loader) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        val platform = when (abi) { "arm64-v8a" -> "android-arm64"; "x86_64" -> "android-x64"; else -> "unsupported" }
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val budget = minOf(192L*1024*1024, manager.memoryClass.toLong()*1024*1024/2, memory.availMem/8).coerceAtLeast(1024*1024)
        NativeReaderSrBackend(SrBundle(platform, context.cacheDir, { name ->
            try { context.assets.open(name) } catch (_: IOException) { null }
        }, File(context.applicationInfo.nativeLibraryDir, "libcopymanga_sr.so"),
            modelDirectory = readerSrModelDirectory(context.filesDir)), readSource = { source ->
            if (source.startsWith("http://") || source.startsWith("https://")) {
                readCoilReaderImageCache(loader.diskCache, source, NativeReaderSrBackend.MAX_INPUT_BYTES)
                    ?: error("原图缓存尚不可用，继续显示原图")
            } else {
                val uri = Uri.parse(source)
                val input = if (uri.scheme == "content" || uri.scheme == "file") context.contentResolver.openInputStream(uri)
                    else File(source).inputStream()
                requireNotNull(input).use { it.readLimited(NativeReaderSrBackend.MAX_INPUT_BYTES) }
            }
        }, codec = AndroidSrCodec(), workerFactory = { AndroidSrWorker(context) }, budgetBytes = budget)
    }
}
