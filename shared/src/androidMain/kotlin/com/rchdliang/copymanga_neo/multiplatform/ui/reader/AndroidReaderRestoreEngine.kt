package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.Image
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
internal actual fun rememberReaderRestoreEngine(): ReaderRestoreEngine {
    val context = LocalContext.current.applicationContext
    val engine = remember(context) { AndroidReaderRestoreEngine(context) }
    DisposableEffect(engine) { onDispose { engine.close() } }
    return engine
}

/** 每个阅读会话复用一个串行 EGL 后端；只缓存两份结果，不编码图片或修改磁盘缓存。 */
internal class AndroidReaderRestoreEngine(private val context: Context) : ReaderRestoreEngine {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "reader-restore").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)
    private val epoch = AtomicLong(0)
    private var gpu: ReaderRestoreGl? = null
    @Volatile private var failure: String? = null
    override val unavailableReason: String? get() = failure
    private val cache = LinkedHashMap<Key, ReaderRestoreOutput>(4, .75f, true)

    private class Key(val bitmap: Bitmap, val generation: Int, val mode: ReaderRestoreMode, val page: String) {
        override fun equals(other: Any?) = other is Key && bitmap === other.bitmap &&
            generation == other.generation && mode == other.mode && page == other.page
        override fun hashCode() = 31 * (31 * (31 * System.identityHashCode(bitmap) + generation) + mode.hashCode()) + page.hashCode()
    }

    override suspend fun restore(source: Image, mode: ReaderRestoreMode, pageKey: String): ReaderRestoreOutput {
        val requestEpoch = epoch.get()
        if (closed.get()) throw CancellationException("Reader restore closed")
        return withContext(dispatcher) {
            val callContext = currentCoroutineContext()
            fun checkCurrent() {
                callContext.ensureActive()
                if (closed.get() || epoch.get() != requestEpoch) throw CancellationException("Reader restore invalidated")
            }
            checkCurrent()
            failure?.let { throw ReaderRestoreUnavailable(it) }
            if (mode == ReaderRestoreMode.OFF) throw ReaderRestoreUnavailable("修复已关闭")
            val bitmap = (source as? BitmapImage)?.bitmap
                ?: throw ReaderRestoreUnavailable("此图片类型暂不支持修复")
            if (!source.shareable || bitmap.isRecycled) throw ReaderRestoreUnavailable("此图片类型暂不支持修复")
            if (!readerRestoreSizeAllowed(bitmap.width, bitmap.height)) {
                throw ReaderRestoreUnavailable("图片较大或为长图，暂用原图")
            }
            if (bitmap.colorSpace?.isSrgb == false) throw ReaderRestoreUnavailable("广色域图片暂用原图")
            val key = Key(bitmap, bitmap.generationId, mode, pageKey)
            cache[key]?.let { return@withContext it.copy(cacheHit = true) }
            val start = System.nanoTime()
            // 独立不可变副本避免修改 Coil 位图，也能把硬件位图转换为可上传的 RGBA8。
            val input = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw ReaderRestoreUnavailable("图片无法读取，显示原图")
            } catch (_: OutOfMemoryError) {
                throw ReaderRestoreUnavailable("内存不足，显示原图")
            }
            try {
                checkCurrent()
                val backend = gpu ?: ReaderRestoreGl(context.assets).also { gpu = it }
                val restored = backend.restore(input, mode, ::checkCurrent)
                checkCurrent()
                if (bitmap.generationId != key.generation) throw CancellationException("Source image changed")
                val result = ReaderRestoreOutput(restored.asImageBitmap(), (System.nanoTime() - start) / 1_000_000, false)
                cache[key] = result
                while (cache.size > 2) cache.remove(cache.keys.first())
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: ReaderRestoreUnavailable) {
                throw unavailable
            } catch (_: OutOfMemoryError) {
                cache.clear()
                runCatching { gpu?.close() }
                gpu = null
                throw ReaderRestoreUnavailable("内存不足，显示原图")
            } catch (error: Exception) {
                Log.w("ReaderRestore", "GPU restoration failed; keeping original image", error)
                runCatching { gpu?.close() }
                gpu = null
                failure = "当前 GPU 修复不可用，显示原图"
                throw ReaderRestoreUnavailable(failure!!)
            } finally {
                input.recycle()
            }
        }
    }

    override fun clear() {
        epoch.incrementAndGet()
        if (closed.get()) return
        executor.execute {
            cache.clear()
            runCatching { gpu?.close() }
            gpu = null
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        epoch.incrementAndGet()
        executor.execute {
            try {
                cache.clear()
                gpu?.close()
                gpu = null
            } finally {
                dispatcher.close()
            }
        }
    }
}
