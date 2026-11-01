@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package com.rchdliang.copymanga_neo.data.logging

import platform.Foundation.*
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

private class IosErrorLogFiles(private val directory: String) : ErrorLogFiles {
    private val lock = NSRecursiveLock()
    private val files = NSFileManager.defaultManager
    override fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }
    override fun read(name: String): String? {
        val path = "$directory/$name"
        if (!files.fileExistsAtPath(path)) return null
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: error("错误日志读取失败")
    }
    override fun write(name: String, text: String, durable: Boolean) {
        check(files.createDirectoryAtPath(directory, true, null, null))
        val path = "$directory/$name"
        check(NSString.create(string = text).writeToFile(path, true, NSUTF8StringEncoding, null))
        if (durable) {
            val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: error("错误日志同步失败")
            try { handle.synchronizeFile() } finally { handle.closeFile() }
        }
    }
    override fun remove(name: String) {
        val path = "$directory/$name"
        if (files.fileExistsAtPath(path)) check(files.removeItemAtPath(path, null))
    }
}

/** 安装在 UIKit/Compose 初始化前，保留 Kotlin 原有未处理异常语义。 */
object IosErrorLog {
    private val lock = NSRecursiveLock()
    private var installed = false
    fun install(directory: String) {
        lock.lock()
        try {
            if (installed) return
            AppErrorLog.install(ErrorLogStore(IosErrorLogFiles(directory)), "iOS")
            val previous = getUnhandledExceptionHook()
            setUnhandledExceptionHook { error ->
                try { AppErrorLog.record("E", "UnhandledException", "未处理的 Kotlin 异常", error, crash = true) }
                finally {
                    if (previous != null) previous(error)
                    else terminateWithUnhandledException(error)
                }
            }
            installed = true
        } finally { lock.unlock() }
    }
}
