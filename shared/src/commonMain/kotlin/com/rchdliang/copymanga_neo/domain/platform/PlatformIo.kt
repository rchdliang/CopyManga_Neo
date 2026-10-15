package com.rchdliang.copymanga_neo.domain.platform

/** JVM aliases preserve IOException handling in the existing Android networking stack. */
expect open class PlatformIOException : Exception {
    constructor(message: String)
    constructor(message: String, cause: Throwable?)
}

/** Caller-owned export sink. Exporters must not close the sink supplied by the caller. */
expect abstract class NetworkLogOutputStream() {
    abstract fun write(value: Int)
    open fun write(bytes: ByteArray)
    open fun write(bytes: ByteArray, offset: Int, length: Int)
    open fun flush()
    open fun close()
}
