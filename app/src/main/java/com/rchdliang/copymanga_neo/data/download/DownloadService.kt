package com.rchdliang.copymanga_neo.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.multiplatform.sharedContainer
import com.rchdliang.copymanga_neo.data.download.portable.DownloadManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 用户发起的下载前台服务：退出界面后继续执行，空闲时立即释放服务和唤醒锁。 */
class DownloadService : Service() {
    companion object {
        const val PAUSE = "com.rchdliang.copymanga_neo.download.PAUSE"
        const val RESUME = "com.rchdliang.copymanga_neo.download.RESUME"
        const val CANCEL = "com.rchdliang.copymanga_neo.download.CANCEL"
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java)
            )
        }
    }

    lateinit var downloads: DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = MutableStateFlow(0)
    private var monitor: Job? = null
    private var lastStartId = 0
    private var finishing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notifications: DownloadNotifications
    private val errors = CoroutineExceptionHandler { _, error ->
        Log.e("DownloadService", "下载服务异常", error)
        sharedContainer.current?.downloadManager?.pauseAll("后台下载中断，请返回应用后继续")
        finishService()
    }

    override fun onCreate() {
        super.onCreate()
        notifications = DownloadNotifications(this)
        DownloadNotifications.beginSession()
        notifications.createChannel()
        try {
            startForeground(
                DownloadNotifications.ID,
                notifications.build(DownloadNotificationState(emptyList(), null, 1)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:downloads").apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L)
                }
            sharedContainer.downloads.started(this)
        } catch (e: RuntimeException) {
            Log.e("DownloadService", "前台服务启动失败", e)
            sharedContainer.downloads.failed()
            sharedContainer.current?.downloadManager?.pauseAll("系统暂不允许后台下载，请返回应用后继续")
            finishService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (finishing) return START_NOT_STICKY
        lastStartId = startId
        commands.update { it + 1 }
        scope.launch(errors) {
            downloads = sharedContainer.awaitGraph().downloadManager
            try {
                when (intent?.action) {
                    PAUSE -> downloads.pauseAll().join()
                    RESUME -> downloads.resumeAll()
                    CANCEL -> downloads.cancelAll()
                }
            } finally {
                commands.update { it - 1 }
            }
        }
        if (monitor == null) monitor = scope.launch(errors) {
            downloads = sharedContainer.awaitGraph().downloadManager
            combine(
                downloads.downloadProgress,
                downloads.activeTask,
                commands
            ) { progress, active, pending ->
                DownloadNotificationState(progress.values.toList(), active?.taskKey, pending)
            }.conflate().collect { state ->
                updateWakeLock(state)
                notifications.update(state)
                if (!state.isRunning) {
                    // 给同时进入的开始/继续命令留出合并窗口；退出前再次核对最新状态。
                    delay(500.milliseconds)
                    val latest = DownloadNotificationState(
                        downloads.downloadProgress.value.values.toList(),
                        downloads.activeTask.value?.taskKey, commands.value
                    )
                    if (!latest.isRunning) {
                        val completedStartId = lastStartId
                        if (stopSelfResult(completedStartId)) {
                            finishing = true
                            sharedContainer.downloads.stopped(this@DownloadService)
                            // 完成后移除进度胶囊，仅保留完成通知；暂停/出错仍保留恢复入口。
                            stopForeground(
                                if (latest.unfinished.isEmpty()) {
                                    STOP_FOREGROUND_REMOVE
                                } else STOP_FOREGROUND_DETACH
                            )
                            notifications.update(latest)
                        }
                    }
                } else {
                    // 进度最多每半秒刷新一次，合并中间事件，避免刷爆系统通知队列。
                    delay(500.milliseconds)
                }
            }
        }
        // 系统回收或用户强停后不在后台擅自重新发起下载。
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        sharedContainer.current?.downloadManager?.pauseAll("后台下载达到系统时限，请返回应用后继续")
        finishService()
    }

    /** 等待 Wi-Fi 时保留前台服务接收网络变化，但不持续占用 CPU 唤醒锁。 */
    private fun updateWakeLock(state: DownloadNotificationState) {
        val transferring = state.pendingCommands > 0 || state.unfinished.any {
            it.status == DownloadTaskStatus.WAITING || it.status == DownloadTaskStatus.IDLE ||
                    it.status == DownloadTaskStatus.REQUESTING_PAGE || it.status == DownloadTaskStatus.DOWNLOADING
        }
        wakeLock?.let { lock ->
            if (transferring && !lock.isHeld) lock.acquire(6 * 60 * 60 * 1000L)
            else if (!transferring && lock.isHeld) lock.release()
        }
    }

    private fun finishService() {
        finishing = true
        sharedContainer.downloads.stopped(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sharedContainer.downloads.stopped(this)
        if (!finishing) sharedContainer.current?.downloadManager?.pauseAll("后台下载已停止，请返回应用后继续")
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
