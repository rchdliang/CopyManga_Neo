package com.rchdliang.copymanga_neo.data.logging

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 同目录原子替换，崩溃记录先同步文件内容；同一进程内串行读写。 */
class JvmErrorLogFiles(private val directory: File) : ErrorLogFiles {
    private val lock = Any()
    override fun <T> locked(block: () -> T): T = synchronized(lock, block)
    override fun size(name: String): Long = File(directory, name).length()
    override fun read(name: String): String? {
        val file = File(directory, name)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
    override fun write(name: String, text: String, durable: Boolean) {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建错误日志目录" }
        val destination = File(directory, name).toPath()
        val temporary = File(directory, "$name.tmp")
        FileOutputStream(temporary).use {
            it.write(text.toByteArray(Charsets.UTF_8))
            if (durable) it.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    override fun remove(name: String) { Files.deleteIfExists(File(directory, name).toPath()) }
}

/** 保留 Android/桌面原有终止与报告行为；重复安装不会叠加处理器。 */
object JvmErrorLog {
    @Synchronized
    fun install(directory: File, platform: String) {
        AppErrorLog.install(ErrorLogStore(JvmErrorLogFiles(directory)), platform)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous !is ErrorLogExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(ErrorLogExceptionHandler(previous))
        }
    }
}

internal class ErrorLogExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { AppErrorLog.record("E", "UncaughtException", "线程：${thread.name}", error, crash = true) }
        finally {
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                // 默认处理器为空时，避免 ThreadGroup 再次进入当前全局处理器。
                System.err.println(error.stackTraceToString())
            }
        }
    }
}
