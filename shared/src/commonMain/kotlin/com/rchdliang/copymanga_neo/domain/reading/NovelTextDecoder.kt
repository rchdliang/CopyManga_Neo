package com.rchdliang.copymanga_neo.domain.reading

/**
 * Strict Unicode/basic single-byte decoding has identical behavior on every platform.
 * Other encodings use the platform charset catalog, including GBK and GB18030.
 * Never substitute replacement characters: doing so would invalidate stored text anchors.
 */
internal fun decodeNovelText(raw: ByteArray, encoding: String): String {
    val name = encoding.trim()
    return when (name.lowercase().replace("_", "").replace("-", "")) {
        "utf8", "unicode11utf8" -> raw.decodeToString(throwOnInvalidSequence = true)
        "utf16", "unicodebig", "unicode" -> decodeUtf16(raw, littleEndian = false, detectBom = true)
        "utf16le", "unicodelittleunmarked", "xutf16le" -> decodeUtf16(raw, littleEndian = true, detectBom = false)
        "utf16be", "unicodebigunmarked", "xutf16be", "iso10646ucs2" -> decodeUtf16(raw, littleEndian = false, detectBom = false)
        "iso88591", "latin1", "l1", "isoir100", "ibm819", "cp819", "csisolatin1", "88591", "819", "iso88591:1987" ->
            buildString(raw.size) { raw.forEach { append((it.toInt() and 255).toChar()) } }
        "usascii", "ascii", "iso646us", "iso646.irv:1983", "iso646.irv:1991", "ansix3.41968", "ansix3.41986",
        "ascii7", "ibm367", "csascii", "646", "us", "cp367", "isoir6" -> buildString(raw.size) {
            raw.forEach {
                if (it < 0) throw CharacterCodingException()
                append(it.toInt().toChar())
            }
        }
        else -> decodePlatformNovelText(raw, name)
    }
}

private fun decodeUtf16(raw: ByteArray, littleEndian: Boolean, detectBom: Boolean): String {
    if (raw.size % 2 != 0) throw CharacterCodingException()
    var little = littleEndian
    var start = 0
    if (detectBom && raw.size >= 2) {
        val first = raw[0].toInt() and 255
        val second = raw[1].toInt() and 255
        if (first == 0xff && second == 0xfe) { little = true; start = 2 }
        else if (first == 0xfe && second == 0xff) { little = false; start = 2 }
    }
    fun unit(at: Int): Char {
        val a = raw[at].toInt() and 255
        val b = raw[at + 1].toInt() and 255
        return (if (little) a or (b shl 8) else (a shl 8) or b).toChar()
    }
    return buildString((raw.size - start) / 2) {
        var at = start
        while (at < raw.size) {
            val character = unit(at)
            at += 2
            if (character.isHighSurrogate()) {
                if (at >= raw.size) throw CharacterCodingException()
                val low = unit(at)
                if (!low.isLowSurrogate()) throw CharacterCodingException()
                append(character); append(low); at += 2
            } else {
                if (character.isLowSurrogate()) throw CharacterCodingException()
                append(character)
            }
        }
    }
}

internal expect fun decodePlatformNovelText(raw: ByteArray, encoding: String): String
