package com.rchdliang.copymanga_neo.multiplatform.restore

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** 统一串行执行、分块取消和有界缓存，Android/桌面只负责位图与打包资源适配。 */
internal abstract class JvmVulkanRestoreEngine : ReaderRestoreEngine {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "reader-vulkan").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)
    private val epoch = AtomicLong(0)
    private var handle = 0L
    @Volatile private var failure: String? = null
    override val unavailableReason: String? get() = failure
    private val loaded = LinkedHashSet<Int>()
    private val cache = LinkedHashMap<Key, ReaderRestoreOutput>(4, .75f, true)
    private var pixelSource: Image? = null
    private var pixelGeneration = 0
    private var input: ByteBuffer? = null
    private var output: ByteBuffer? = null

    protected abstract fun loadLibrary()
    protected abstract fun readModel(name: String): ByteArray
    protected abstract fun sourcePixels(source: Image): ByteBuffer
    protected abstract fun resultImage(pixels: ByteBuffer, width: Int, height: Int): ImageBitmap
    protected open fun generation(source: Image): Int = 0
    protected abstract fun logFailure(error: Throwable)

    private class Key(val source: Image, val generation: Int, val selection: ReaderRestoreSelection, val page: String) {
        override fun equals(other: Any?) = other is Key && source === other.source && generation == other.generation &&
            selection == other.selection && page == other.page
        override fun hashCode() = 31 * (31 * (31 * System.identityHashCode(source) + generation) + selection.hashCode()) + page.hashCode()
    }

    override suspend fun restore(source: Image, selection: ReaderRestoreSelection, pageKey: String): ReaderRestoreOutput {
        if (closed.get()) throw CancellationException("Reader closed")
        val requestEpoch = epoch.get()
        return withContext(dispatcher) {
            val jobContext = currentCoroutineContext()
            fun checkCurrent() {
                jobContext.ensureActive()
                if (closed.get() || requestEpoch != epoch.get()) throw CancellationException("Reader changed")
            }
            checkCurrent()
            failure?.let { throw ReaderRestoreUnavailable(it) }
            if (selection.mode == ReaderRestoreMode.OFF) throw ReaderRestoreUnavailable("修复已关闭")
            if (!source.shareable) throw ReaderRestoreUnavailable("动态图片暂用原图")
            val width = source.width
            val height = source.height
            if (!readerRestoreSizeAllowed(width, height)) throw ReaderRestoreUnavailable("图片超出处理预算，显示原图")
            val key = Key(source, generation(source), selection, pageKey)
            cache[key]?.let { return@withContext it.copy(cacheHit = true) }
            val started = System.nanoTime()
            try {
                if (handle == 0L) {
                    try {
                        loadLibrary()
                        handle = VulkanRestoreBridge.create(false)
                        check(handle != 0L)
                    } catch (error: Exception) {
                        logFailure(error)
                        failure = "当前设备的 Vulkan 修复不可用，显示原图"
                        throw ReaderRestoreUnavailable(failure!!)
                    }
                }
                checkCurrent()
                if (selection.modelId !in loaded) {
                    val model = readModel(selection.pack)
                    require(model.size <= 16 * 1024 * 1024)
                    VulkanRestoreBridge.loadModel(handle, selection.modelId, model)
                }
                loaded.remove(selection.modelId)
                loaded.add(selection.modelId)
                while (loaded.size > 2) loaded.remove(loaded.first())
                checkCurrent()
                if (pixelSource !== source || pixelGeneration != key.generation || input == null) {
                    input = sourcePixels(source)
                    pixelSource = source
                    pixelGeneration = key.generation
                }
                val bytes = width * height * 4
                if (output?.capacity() != bytes) output = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                val src = checkNotNull(input)
                val dst = checkNotNull(output)
                require(src.isDirect && src.capacity() >= bytes)
                for (y in 0 until height step 256) for (x in 0 until width step 256) {
                    checkCurrent()
                    VulkanRestoreBridge.renderTile(handle, selection.modelId, src, width, height, dst, x, y)
                }
                checkCurrent()
                if (generation(source) != key.generation) throw CancellationException("Image changed")
                dst.rewind()
                val image = resultImage(dst, width, height)
                checkCurrent()
                val result = ReaderRestoreOutput(image, (System.nanoTime() - started) / 1_000_000, false)
                cache[key] = result
                while (cache.size > 2 || cache.values.sumOf { it.image.width.toLong() * it.image.height * 4 } > 32L * 1024 * 1024) {
                    cache.remove(cache.keys.first())
                }
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: ReaderRestoreUnavailable) {
                throw unavailable
            } catch (error: LinkageError) {
                logFailure(error)
                failure = "Vulkan 运行库不可用，显示原图"
                throw ReaderRestoreUnavailable(failure!!)
            } catch (error: OutOfMemoryError) {
                release()
                throw ReaderRestoreUnavailable("内存不足，显示原图")
            } catch (error: Exception) {
                logFailure(error)
                release()
                throw ReaderRestoreUnavailable("当前档位处理失败，显示原图")
            }
        }
    }

    private fun release() {
        cache.clear()
        loaded.clear()
        pixelSource = null
        input = null
        output = null
        val old = handle
        handle = 0
        if (old != 0L) runCatching { VulkanRestoreBridge.destroy(old) }
    }

    override fun clear() {
        epoch.incrementAndGet()
        if (!closed.get()) executor.execute { release() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        epoch.incrementAndGet()
        executor.execute {
            try { release() } finally { dispatcher.close() }
        }
    }
}
