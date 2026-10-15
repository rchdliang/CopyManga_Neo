@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.CFStringConvertIANACharSetNameToEncoding
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingInvalidId
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSString
import platform.Foundation.create

internal actual fun decodePlatformNovelText(raw: ByteArray, encoding: String): String {
    fun resolve(name: String): UInt {
        val nameRef = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8)
            ?: throw IllegalArgumentException("Invalid charset: $name")
        return try { CFStringConvertIANACharSetNameToEncoding(nameRef) }
        finally { CFRelease(nameRef) }
    }
    var charset = resolve(encoding)
    if (charset == kCFStringEncodingInvalidId) {
        charset = resolve(encoding.replace("_", "").replace("-", ""))
    }
    require(charset != kCFStringEncodingInvalidId) { "Unsupported charset: $encoding" }
    if (raw.isEmpty()) return ""
    return raw.usePinned { pinned ->
        NSString.create(
            bytes = pinned.addressOf(0),
            length = raw.size.toULong(),
            encoding = CFStringConvertEncodingToNSStringEncoding(charset)
        )?.toString() ?: throw CharacterCodingException()
    }
}
