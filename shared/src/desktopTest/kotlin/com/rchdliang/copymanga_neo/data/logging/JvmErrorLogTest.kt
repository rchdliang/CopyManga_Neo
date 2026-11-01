package com.rchdliang.copymanga_neo.data.logging

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.test.*

class JvmErrorLogTest {
    @Test fun concurrentWritersProduceCompleteRecords() {
        val directory = Files.createTempDirectory("cmp-error-log").toFile()
        try {
            val store = ErrorLogStore(JvmErrorLogFiles(directory))
            val threads = List(4) { writer ->
                thread { repeat(20) { store.append("""{"writer":$writer,"index":$it}""", false) } }
            }
            threads.forEach { it.join() }
            val lines = ErrorLogStore(JvmErrorLogFiles(directory)).snapshot().lines().filter(String::isNotBlank)
            assertEquals(80, lines.size)
            assertEquals(80, lines.distinct().size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun uncaughtExceptionIsSavedBeforePreviousHandlerEvenWhenStorageFails() {
        val directory = Files.createTempDirectory("cmp-handler").toFile()
        try {
            AppErrorLog.install(ErrorLogStore(JvmErrorLogFiles(directory)), "test")
            var forwarded: Throwable? = null
            val error = IllegalStateException("handler marker")
            ErrorLogExceptionHandler { _, failure ->
                assertContains(ErrorLogStore(JvmErrorLogFiles(directory)).snapshot(), "handler marker")
                forwarded = failure
            }.uncaughtException(Thread.currentThread(), error)
            assertSame(error, forwarded)
            val blocked = File(directory, "file").apply { writeText("not a directory") }
            AppErrorLog.install(ErrorLogStore(JvmErrorLogFiles(blocked)), "test")
            forwarded = null
            ErrorLogExceptionHandler { _, failure -> forwarded = failure }.uncaughtException(Thread.currentThread(), error)
            assertSame(error, forwarded)
        } finally { directory.deleteRecursively() }
    }

    @Test fun actualProcessCrashCanBeExportedByANewProcess() {
        val directory = Files.createTempDirectory("cmp-crash-restart").toFile()
        try {
            val crash = launch(directory, "crash")
            assertTrue(crash.first != 0, crash.second)
            val restarted = launch(directory, "export")
            assertEquals(0, restarted.first, restarted.second)
            assertContains(restarted.second, "subprocess crash marker")
            assertContains(restarted.second, "IllegalStateException")
            assertContains(restarted.second, "\"kind\":\"crash\"")
        } finally { directory.deleteRecursively() }
    }

    private fun launch(directory: File, mode: String): Pair<Int, String> {
        val paths = linkedSetOf<String>()
        paths += System.getProperty("java.class.path").split(File.pathSeparator)
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) paths += loader.urLs.map { File(it.toURI()).path }
            loader = loader.parent
        }
        val java = File(System.getProperty("java.home"), "bin/java.exe").takeIf(File::exists)
            ?: File(System.getProperty("java.home"), "bin/java")
        val output = File(directory, "$mode-output.txt")
        val process = ProcessBuilder(java.path, "-cp", paths.joinToString(File.pathSeparator),
            ErrorLogCrashProbe::class.java.name, directory.path, mode)
            .redirectErrorStream(true).redirectOutput(output).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("诊断子进程超时")
        }
        return process.exitValue() to output.readText()
    }
}

/** 独立进程验证同步崩溃写入，不在测试进程内安装全局处理器。 */
object ErrorLogCrashProbe {
    @JvmStatic fun main(args: Array<String>) {
        JvmErrorLog.install(File(args[0], "logs"), "test process")
        if (args[1] == "crash") throw IllegalStateException("subprocess crash marker")
        println(runBlocking { AppErrorLog.export().getOrThrow().decodeToString() })
    }
}
