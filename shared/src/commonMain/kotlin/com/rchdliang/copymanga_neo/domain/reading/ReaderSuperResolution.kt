package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.ByteString.Companion.encodeUtf8

/** Original-byte identity. Width/height are orientation-normalized; orientation records source EXIF. */
data class SrSource(
    val scope: String,
    val contentSha256: String,
    val width: Int,
    val height: Int,
    val orientation: Int = 1,
    /** Location is used only by the adapter; the stable real-page identity remains scope. */
    val location: String = scope,
) {
    init {
        require(scope.isNotBlank() && contentSha256.isSha256())
        require(width > 0 && height > 0 && orientation in 1..8)
    }
}

/** Digest covers both model weights and graph; runtimeVersion includes preprocessing implementation. */
data class SrModel(val id: String, val version: String, val digest: String, val runtimeVersion: String) {
    init { require(id.isNotBlank() && version.isNotBlank() && digest.isSha256() && runtimeVersion.isNotBlank()) }
}

enum class SrExecutionMode { AUTO, CPU, GPU }

data class SrParameters(val noise: Int = -1, val tile: Int = 32, val tta: Boolean = false,
    val mode: SrExecutionMode = SrExecutionMode.AUTO, val cpuFallback: Boolean = true) {
    init { require(noise in -1..3 && tile in 32..512) }
}

/** This prototype intentionally supports only 2x. Tone mapping/ICC policy is versioned, never implicit. */
data class SrRequest(val source: SrSource, val model: SrModel, val parameters: SrParameters = SrParameters()) {
    val cacheKey: String get() {
        val fields = listOf("reader-sr-v1", source.scope, source.contentSha256.lowercase(),
            source.width.toString(), source.height.toString(), source.orientation.toString(),
            model.id, model.version, model.digest.lowercase(), model.runtimeVersion,
            "2", parameters.noise.toString(), parameters.tile.toString(), parameters.tta.toString(),
            parameters.mode.name, parameters.cpuFallback.toString(), "srgb-rgba8")
        return fields.joinToString("") { "${it.length}:$it" }.encodeUtf8().sha256().hex()
    }
}

private fun String.isSha256() = length == 64 && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

/** Owns immutable encoded output; never replaces or deletes the source. No full-chapter bitmap cache. */
data class SrArtifact(val png: ByteArray, val width: Int, val height: Int)

interface ReaderSrBackend {
    /** Static configuration failure. Adapters additionally validate models and self-test before inference. */
    val unavailableReason: String?
    /** Conservative model/tile working-set allowance, in addition to input and output RGBA buffers. */
    val workingBytes: Long
    /** Resolve source inside the adapter, verify digest, honor cancellation through the platform worker boundary; no UI thread work. */
    suspend fun upscale(request: SrRequest): SrArtifact
}

object UnavailableReaderSrBackend : ReaderSrBackend {
    override val unavailableReason = "未配置经过校验的原生超分后端和模型"
    override val workingBytes = 0L
    override suspend fun upscale(request: SrRequest): SrArtifact = error(unavailableReason)
}

sealed interface SrStatus {
    /** Render the normal reader image for every state except a matching Ready. */
    data object Original : SrStatus
    data object Processing : SrStatus
    data class Ready(val artifact: SrArtifact) : SrStatus
    data class Unavailable(val reason: String) : SrStatus
    data object Failed : SrStatus
}

/**
 * Reader coordinator; the native adapter validates its packaged model/runtime before inference.
 * State is confined to the supplied scope dispatcher. submit/close must use that same dispatcher.
 * collectLatest cancels AND JOINS the old selection before starting the next one, so even a native
 * call that cannot interrupt promptly never overlaps a second inference. Obsolete output is rejected.
 */
class ReaderSrCoordinator(
    scope: CoroutineScope,
    private val backend: ReaderSrBackend = UnavailableReaderSrBackend,
    cacheBytes: Int = 16 * 1024 * 1024,
    private val pixelBufferBudget: Long = 96L * 1024 * 1024,
    private val timeoutMillis: Long = 30_000,
) {
    private val selections = MutableStateFlow<List<SrRequest>>(emptyList())
    private val cache = SrResultCache(cacheBytes)
    private val mutableStates = MutableStateFlow<Map<String, SrStatus>>(emptyMap())
    val states: StateFlow<Map<String, SrStatus>> = mutableStates.asStateFlow()
    private var closed = false

    init { require(pixelBufferBudget > 0 && timeoutMillis > 0 && backend.workingBytes >= 0) }

    private val worker = scope.launch {
        try {
            selections.collectLatest { requests ->
                mutableStates.value = requests.associate { it.cacheKey to SrStatus.Original }
                for (request in requests) {
                    currentCoroutineContext().ensureActive()
                    val key = request.cacheKey
                    val unavailable = backend.unavailableReason ?: admissionFailure(request)
                    if (unavailable != null) {
                        update(key, SrStatus.Unavailable(unavailable))
                        continue
                    }
                    cache.get(key)?.let { update(key, SrStatus.Ready(it)) } ?: run {
                        update(key, SrStatus.Processing)
                        try {
                            val output = withTimeout(timeoutMillis) { backend.upscale(request) }
                            currentCoroutineContext().ensureActive()
                            require(output.width.toLong() == request.source.width.toLong() * 2 &&
                                output.height.toLong() == request.source.height.toLong() * 2)
                            require(hasMatchingPngHeader(output))
                            cache.put(key, output)
                            update(key, SrStatus.Ready(output))
                        } catch (_: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            update(key, SrStatus.Failed)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            update(key, SrStatus.Failed)
                        }
                    }
                }
            }
        } finally {
            cache.clear()
            mutableStates.value = emptyMap()
        }
    }

    /** A conflated selection of at most current + next + previous; never append a chapter queue. */
    fun submit(current: SrRequest?, neighbors: List<SrRequest> = emptyList()) {
        check(!closed)
        selections.value = if (current == null) emptyList()
            else (listOf(current) + neighbors.take(2)).distinctBy { it.cacheKey }
    }

    /** Cancel on reader disposal, account/session change, disable, and when hidden/backgrounded. */
    fun close() {
        if (closed) return
        closed = true
        selections.value = emptyList()
        mutableStates.value = emptyMap()
        worker.cancel()
    }

    private fun update(key: String, value: SrStatus) {
        if (!closed) mutableStates.value = mutableStates.value + (key to value)
    }

    private fun admissionFailure(request: SrRequest): String? {
        val source = request.source
        // Long images require an actual region decoder + streaming tile sink, absent in this stage.
        if (source.width > 4096 || source.height > 4096) return "长图需分块后端，保留原图"
        val pixelBytes = source.width.toLong() * source.height * 4L * 5L
        if (backend.workingBytes > pixelBufferBudget || pixelBytes > pixelBufferBudget - backend.workingBytes)
            return "超出本次超分内存预算，保留原图"
        return null
    }

    private fun hasMatchingPngHeader(output: SrArtifact): Boolean {
        val bytes = output.png
        if (bytes.size !in 33..MAX_ENCODED_BYTES || bytes.take(8) != PNG_HEADER.toList()) return false
        fun intAt(offset: Int): Long = (0..3).fold(0L) { value, i ->
            (value shl 8) or (bytes[offset + i].toLong() and 255L)
        }
        // A cheap admission check, not a complete PNG validator. The native adapter must fully
        // validate the encoded image and enforce the output bound while encoding, before returning.
        return intAt(8) == 13L && bytes.slice(12..15) == listOf<Byte>(73, 72, 68, 82) &&
            intAt(16) == output.width.toLong() && intAt(20) == output.height.toLong()
    }

    companion object {
        const val MAX_ENCODED_BYTES = 16 * 1024 * 1024
        private val PNG_HEADER = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    }
}

/** Encoded results only, independently bounded by bytes AND count. Caller remains on one dispatcher. */
internal class SrResultCache(private val maxBytes: Int) {
    init { require(maxBytes >= 0) }
    private val entries = linkedMapOf<String, SrArtifact>()
    private var bytes = 0L
    fun get(key: String): SrArtifact? = entries.remove(key)?.also { entries[key] = it }
    fun put(key: String, value: SrArtifact) {
        entries.remove(key)?.let { bytes -= it.png.size }
        if (value.png.size > maxBytes) return
        entries[key] = value
        bytes += value.png.size
        while (bytes > maxBytes || entries.size > 3) {
            bytes -= entries.remove(entries.keys.first())!!.png.size
        }
    }
    fun clear() { entries.clear(); bytes = 0 }
}
