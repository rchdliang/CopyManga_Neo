package com.rchdliang.copymanga_neo.domain.model

/** Java-compatible code point count, including isolated UTF-16 surrogates. */
internal fun String.unicodeCodePointCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) index++
        index++
        count++
    }
    return count
}
