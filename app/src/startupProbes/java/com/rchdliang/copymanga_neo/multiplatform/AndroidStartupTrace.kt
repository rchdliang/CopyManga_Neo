package com.rchdliang.copymanga_neo.multiplatform

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.rchdliang.copymanga_neo.multiplatform.app.StartupTrace
import java.util.concurrent.ConcurrentHashMap

/** 仅 -PenableStartupProbes=true 打包。每个进程每个阶段只记一次，不记录敏感数据。 */
object AndroidStartupTrace : StartupTrace {
    override val enabled = true
    private val phases = ConcurrentHashMap.newKeySet<String>()

    override fun begin(phase: String): (() -> Unit)? {
        if (!phases.add("span_$phase")) return null
        mark("${phase}_start")
        val wall = SystemClock.elapsedRealtime()
        val cpu = SystemClock.currentThreadTimeMillis()
        android.os.Trace.beginSection("startup:$phase")
        return {
            android.os.Trace.endSection()
            val elapsed = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            Log.d("StartupTiming", "phase=$phase totalMs=$elapsed durationMs=${SystemClock.elapsedRealtime() - wall}" +
                " cpuMs=${SystemClock.currentThreadTimeMillis() - cpu} thread=${Thread.currentThread().name}")
        }
    }

    override fun mark(phase: String, durationMillis: Long?) {
        if (!phases.add(phase)) return
        val elapsed = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.d("StartupTiming", "phase=$phase totalMs=$elapsed" +
            (durationMillis?.let { " durationMs=$it" } ?: "") + " thread=${Thread.currentThread().name}")
    }

    override fun palette(screen: String, primary: ULong, surface: ULong) {
        if (!phases.add("palette_$screen")) return
        Log.d("StartupTiming", "palette=$screen primary=${primary.toString(16)} surface=${surface.toString(16)}")
    }
}
