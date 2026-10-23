package com.rchdliang.copymanga_neo.multiplatform.sr

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/** SIGSEGV/driver failures happen here in a child process, never inside the JVM. */
internal class DesktopSrWorker(private val executable: File) : SrWorker {
    override suspend fun probe(models: File, gpu: Boolean): Int {
        val text = execute(listOf("probe", models.absolutePath, if (gpu) "gpu" else "cpu"))
        return Regex("^CMP_SR_1 (-?[0-9]+)\\s*$").matchEntire(text)?.groupValues?.get(1)?.toInt()
            ?.also { require(it >= -1) } ?: throw IOException("超分自检响应无效")
    }
    override suspend fun run(models: File, gpu: Int, input: File, output: File) {
        execute(listOf("run", models.absolutePath, gpu.toString(), input.absolutePath, output.absolutePath))
    }
    private suspend fun execute(arguments: List<String>): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            process.outputStream.close()
            // The protocol writes at most a single short line. No shell or inherited stdin.
            // Poll instead of blocking waitFor so cancellation always reaches the kill/reap finally.
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) currentCoroutineContext().ensureActive()
            currentCoroutineContext().ensureActive()
            if (process.exitValue() != 0) throw IOException("超分工作进程失败 (${process.exitValue()})")
            process.inputStream.use { String(it.readLimited(4096), Charsets.UTF_8) }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (process.isAlive) process.destroyForcibly()
                // Never release the global inference slot before the child has exited.
                process.waitFor()
                process.inputStream.close(); process.errorStream.close(); process.outputStream.close()
            }
        }
    }
}
