package com.rchdliang.copymanga_neo.multiplatform.platform

/** All handles are opaque; tests inject this boundary without loading JNA or opening a clipboard. */
internal interface WindowsClipboardApi {
    val owner: Long
    fun registerFormat(name: String): Int
    /** Allocate, lock, fill, and unlock GMEM_MOVEABLE memory; clean up internally if this fails. */
    fun allocate(bytes: ByteArray): Long
    fun open(): Boolean
    fun empty(): Boolean
    /** Success transfers ownership to Windows; the caller must never free that handle afterward. */
    fun publish(format: Int, handle: Long): Boolean
    fun close(): Boolean
    fun wipeAndFree(handle: Long)
}

internal class WindowsSensitiveClipboard(private val api: WindowsClipboardApi) {
    fun copy(text: String) {
        require(api.owner != 0L) { "无法取得应用窗口，未复制敏感内容" }
        val plaintext = unicodeText(text)
        val owned = linkedSetOf<Long>()
        var opened = false
        var emptied = false
        var failure: Throwable? = null
        try {
            val formats = SUPPRESSION_FORMATS.map { name ->
                api.registerFormat(name).also { check(it != 0) { "无法设置 Windows 剪贴板保护标记，未复制敏感内容" } }
            }
            val payloads = listOf(byteArrayOf(1), ByteArray(4), ByteArray(4), plaintext)
            val handles = payloads.map { bytes ->
                api.allocate(bytes).also {
                    check(it != 0L) { "无法分配剪贴板内存，未复制敏感内容" }
                    check(owned.add(it)) { "剪贴板返回了重复内存句柄" }
                }
            }
            check(api.open()) { "剪贴板正在被其他应用使用，请稍后重试" }
            opened = true
            check(api.empty()) { "无法取得剪贴板所有权，未复制敏感内容" }
            emptied = true
            // No plaintext is submitted until every exclusion marker has been accepted.
            (formats + CF_UNICODETEXT).zip(handles).forEach { (format, handle) ->
                check(api.publish(format, handle)) { "Windows 剪贴板写入失败；未使用普通剪贴板回退" }
                owned.remove(handle)
            }
        } catch (error: Throwable) {
            failure = error
            if (opened && emptied) {
                // Still holding the clipboard lock: discard our partial metadata, never a later owner's data.
                try { check(api.empty()) { "未能清除部分剪贴板标记" } }
                catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            }
        } finally {
            plaintext.fill(0)
            if (opened) {
                try { check(api.close()) { "无法确认剪贴板已关闭；复制结果可能已写入" } }
                catch (close: Throwable) { failure?.addSuppressed(close) ?: run { failure = close } }
            }
            owned.forEach { handle ->
                try { api.wipeAndFree(handle) }
                catch (cleanup: Throwable) { failure?.addSuppressed(cleanup) ?: run { failure = cleanup } }
            }
        }
        failure?.let { throw it }
    }

    companion object {
        internal const val CF_UNICODETEXT = 13
        internal val SUPPRESSION_FORMATS = listOf(
            "ExcludeClipboardContentFromMonitorProcessing",
            "CanIncludeInClipboardHistory",
            "CanUploadToCloudClipboard"
        )
        private fun unicodeText(text: String): ByteArray {
            require(text.length <= 512 * 1024 && '\u0000' !in text) { "敏感文本过长或包含空字符，未复制" }
            // Explicit UTF-16LE preserves supplementary characters without a platform charset/BOM.
            text.forEachIndexed { index, char ->
                require(!char.isHighSurrogate() || text.getOrNull(index + 1)?.isLowSurrogate() == true) { "敏感文本含无效字符，未复制" }
                require(!char.isLowSurrogate() || text.getOrNull(index - 1)?.isHighSurrogate() == true) { "敏感文本含无效字符，未复制" }
            }
            return ByteArray((text.length + 1) * 2).also { bytes ->
                text.forEachIndexed { index, char ->
                    bytes[index * 2] = char.code.toByte()
                    bytes[index * 2 + 1] = (char.code ushr 8).toByte()
                }
            }
        }
    }
}
