package com.rchdliang.copymanga_neo.domain.reading

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.UnsupportedCharsetException

internal actual fun decodePlatformNovelText(raw: ByteArray, encoding: String): String {
    val charset = try {
        Charset.forName(encoding)
    } catch (error: UnsupportedCharsetException) {
        Charset.forName(encoding.replace("_", "").replace("-", ""))
    }
    return charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(raw)).toString()
}
