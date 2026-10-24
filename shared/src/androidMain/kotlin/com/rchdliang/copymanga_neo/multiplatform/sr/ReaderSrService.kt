package com.rchdliang.copymanga_neo.multiplatform.sr

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** Dedicated :reader_sr process. Native signals terminate this worker, not the reader VM. */
class ReaderSrService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private var busy = false
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == STOP) {
            Process.killProcess(Process.myPid()); true
        } else if (message.what == EXECUTE && !busy) {
            busy = true
            val reply = message.replyTo
            val data = Bundle(message.data)
            runCatching { reply.send(Message.obtain(null, STARTED, Process.myPid(), 0)) }
            executor.execute {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                val operation = if (data.getBoolean("probe")) {
                    if (data.getBoolean("gpu")) "probe_gpu" else "probe_cpu"
                } else if (data.getInt("device") >= 0) "enhance_gpu" else "enhance_cpu"
                val started = SystemClock.elapsedRealtime()
                Log.i("ReaderSrService", "SR start operation=$operation pid=${Process.myPid()}")
                val result = try {
                    check(ApplicationProcess.name().endsWith(":reader_sr")) { "Native SR must run outside the main process" }
                    val models = checkedReaderSrModelDirectory(filesDir, data.getString("models")!!)
                    if (data.getBoolean("probe")) NativeSr.probe(models, data.getBoolean("gpu"))
                    else NativeSr.run(models, data.getInt("device"), checkedPath(data.getString("input")!!), checkedPath(data.getString("output")!!))
                } catch (error: Exception) {
                    Log.w("ReaderSrService", "SR failure operation=$operation type=${error.javaClass.simpleName}")
                    FAILURE
                } catch (_: UnsatisfiedLinkError) {
                    Log.w("ReaderSrService", "SR failure operation=$operation type=NativeLoadFailure")
                    FAILURE
                }
                Log.i("ReaderSrService", "SR result operation=$operation code=$result elapsedMs=${SystemClock.elapsedRealtime() - started} pid=${Process.myPid()}")
                runCatching { reply.send(Message.obtain(null, RESULT, result, 0)) }
            }
            true
        } else false
    })
    private fun checkedPath(path: String): String {
        val file = File(path).canonicalFile
        check(file.path.startsWith(File(cacheDir, "reader-sr-v1").canonicalPath + File.separator))
        return file.path
    }
    override fun onBind(intent: Intent): IBinder = inbox.binder
    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
        // Interrupting a Java thread cannot cancel C++; terminate only this private process.
        Process.killProcess(Process.myPid())
    }
    companion object {
        internal const val EXECUTE = 1
        internal const val STOP = 2
        internal const val RESULT = 3
        internal const val STARTED = 4
        internal const val FAILURE = -1000
    }
}

private object ApplicationProcess {
    fun name(): String = android.app.Application.getProcessName()
}

/** Never referenced by the UI/adapter process. JNI cannot catch SIGSEGV. */
internal object NativeSr {
    init { System.loadLibrary("copymanga_sr") }
    external fun probe(models: String, gpu: Boolean): Int
    external fun run(models: String, gpu: Int, input: String, output: String): Int
}
