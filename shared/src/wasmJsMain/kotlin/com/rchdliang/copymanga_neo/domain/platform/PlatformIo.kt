package com.rchdliang.copymanga_neo.domain.platform

actual open class PlatformIOException : Exception {
    actual constructor(message: String) : super(message)
    actual constructor(message: String, cause: Throwable?) : super(message, cause)
}

actual abstract class NetworkLogOutputStream actual constructor() {
    actual abstract fun write(value: Int)
    actual open fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    actual open fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        for (index in offset until offset + length) write(bytes[index].toInt() and 255)
    }
    actual open fun flush() = Unit
    actual open fun close() = Unit
}
