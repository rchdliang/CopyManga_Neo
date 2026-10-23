package com.rchdliang.copymanga_neo.multiplatform.sr

import android.content.*
import android.os.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*

internal class AndroidSrWorker(private val context: Context) : SrWorker {
    override suspend fun probe(models: File, gpu: Boolean): Int = call(Bundle().apply {
        putString("models", models.absolutePath); putBoolean("probe", true); putBoolean("gpu", gpu)
    }).also { if (it < -1) throw IOException("超分自检失败") }
    override suspend fun run(models: File, gpu: Int, input: File, output: File) {
        val result = call(Bundle().apply {
            putString("models", models.absolutePath); putInt("device", gpu)
            putString("input", input.absolutePath); putString("output", output.absolutePath)
        })
        if (result != 0) throw IOException("超分服务处理失败")
    }
    private suspend fun call(data: Bundle): Int = withContext(Dispatchers.Main.immediate) {
        check(!quarantined) { "超分服务未能安全退出，本次运行已停用" }
        val result = CompletableDeferred<Int>()
        val died = CompletableDeferred<Unit>()
        var remote: Messenger? = null
        var binder: IBinder? = null
        var finishing = false
        val death = IBinder.DeathRecipient {
            died.complete(Unit)
            result.completeExceptionally(IOException("超分工作进程已退出，继续显示原图"))
        }
        val response = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                ReaderSrService.STARTED -> if (message.arg1 == Process.myPid()) result.completeExceptionally(IOException("超分服务进程配置无效"))
                ReaderSrService.RESULT -> result.complete(message.arg1)
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (finishing) return // A cancelled bind must never start late native work.
                binder = service; remote = Messenger(service)
                try {
                    service.linkToDeath(death, 0)
                    remote!!.send(Message.obtain(null, ReaderSrService.EXECUTE).apply { this.data = data; replyTo = response })
                } catch (_: RemoteException) { death.binderDied() }
            }
            override fun onServiceDisconnected(name: ComponentName) { death.binderDied() }
            override fun onBindingDied(name: ComponentName) { death.binderDied() }
            override fun onNullBinding(name: ComponentName) { result.completeExceptionally(IOException("超分服务不可用")); died.complete(Unit) }
        }
        var bound = false
        try {
            bound = context.bindService(Intent(context, ReaderSrService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "无法连接超分服务" }
            result.await()
        } finally {
            withContext(NonCancellable) {
                finishing = true
                remote?.let { runCatching { it.send(Message.obtain(null, ReaderSrService.STOP)) } }
                if (bound) {
                    // Unbinding also invokes onDestroy, which terminates the service's own process.
                    runCatching { context.unbindService(connection) }
                }
                if (binder != null && binder!!.isBinderAlive && withTimeoutOrNull(3_000) { died.await() } == null) {
                    // Fail closed; never start another job while an old native call might still live.
                    quarantined = true
                }
                binder?.let { runCatching { it.unlinkToDeath(death, 0) } }
            }
        }
    }
    companion object { @Volatile private var quarantined = false }
}
