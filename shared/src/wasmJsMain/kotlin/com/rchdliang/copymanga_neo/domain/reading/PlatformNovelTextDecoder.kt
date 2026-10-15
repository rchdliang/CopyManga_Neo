@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rchdliang.copymanga_neo.domain.reading

import kotlin.JsFun

// Binary strings avoid lossy UTF-8 conversion at the JS boundary.
@JsFun("(encoding, bytes) => { try { return new TextDecoder(encoding, { fatal: true, ignoreBOM: true }).decode(Uint8Array.from(bytes, c => c.charCodeAt(0))); } catch (e) { return null; } }")
private external fun decodeBrowserText(encoding: String, bytes: String): String?

@JsFun("encoding => { try { new TextDecoder(encoding); return true; } catch (e) { return false; } }")
private external fun supportsBrowserEncoding(encoding: String): Boolean

internal actual fun decodePlatformNovelText(raw: ByteArray, encoding: String): String {
    val charset = when {
        supportsBrowserEncoding(encoding) -> encoding
        supportsBrowserEncoding(encoding.replace("_", "").replace("-", "")) ->
            encoding.replace("_", "").replace("-", "")
        else -> throw IllegalArgumentException("Unsupported charset: $encoding")
    }
    val binary = buildString(raw.size) { raw.forEach { append((it.toInt() and 255).toChar()) } }
    return decodeBrowserText(charset, binary) ?: throw CharacterCodingException()
}
