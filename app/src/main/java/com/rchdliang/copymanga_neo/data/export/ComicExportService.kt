package com.rchdliang.copymanga_neo.data.export

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rchdliang.copymanga_neo.MainActivity
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications
import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import com.rchdliang.copymanga_neo.domain.model.ComicExportStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 用户在前台发起，离开下载页后仍显示导出进度；系统超时时取消并清理未完成文件。 */
@AndroidEntryPoint
class ComicExportService : Service() {
    companion object {
        private const val CHANNEL = "comic_exports"
        private const val ID = 4103
        private const val CANCEL = "com.rchdliang.copymanga_neo.export.CANCEL"
        const val RETRY = "com.rchdliang.copymanga_neo.export.RETRY"
        const val TASK_ID = "export_task_id"
        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, ComicExportService::class.java)
        )
    }

    @Inject
    lateinit var exports: ComicExportManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "漫画导出",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        try {
            startForeground(
                ID,
                notification(exports.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:comic-export").apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L)
                }
        } catch (error: RuntimeException) {
            android.util.Log.e("ComicExport", "前台导出服务启动失败", error)
            exports.cancel("无法启动后台导出，请返回应用重试")
            stopSelf()
            return
        }
        scope.launch {
            exports.state.sample(500).collect { state ->
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                        this@ComicExportService, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notifications.notify(ID, notification(state))
                }
                if (!state.running) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) exports.cancel()
        return START_NOT_STICKY
    }

    private fun notification(state: ComicExportState): android.app.Notification {
        val returnToApp = Intent(this, MainActivity::class.java)
            .setAction(DownloadNotifications.OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val retry = Intent(this, MainActivity::class.java).setAction(RETRY)
            .setData("copymanga-export:retry/${state.taskId}".toUri())
            .putExtra(TASK_ID, state.taskId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val files = state.outputs.filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
        val folders =
            state.outputs.filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
        val resultIntent = when {
            state.running -> returnToApp
            files.size > 1 -> ComicExportIntents.share(files)
            files.size == 1 -> ComicExportIntents.open(files.first())
            folders.size == 1 -> ComicExportIntents.open(folders.first())
            folders.size > 1 -> ComicExportIntents.openFolder(state)
            else -> retry
        }
        val hint = when {
            state.running -> state.current
            files.size > 1 -> "点按分享 ${files.size} 个导出文件"
            files.size == 1 -> "点按选择应用打开 ${files.first().name}"
            folders.isNotEmpty() -> "点按选择文件管理器打开导出目录"
            else -> "点按重新选择保存位置并重试"
        }
        val details = if (state.running) state.current else buildString {
            append(state.resultMessage()).append('\n').append(hint)
            state.failures.take(5)
                .forEach { append('\n').append(it.name).append("：").append(it.message) }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(if (state.running) "正在导出漫画" else state.resultMessage())
            .setContentText(hint).setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(activityIntent(resultIntent, ID)).setOnlyAlertOnce(true)
            .setOngoing(state.running).setAutoCancel(!state.running)
        if (state.running) {
            val cancel = PendingIntent.getService(
                this, ID + 1, Intent(this, ComicExportService::class.java).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setProgress(1000, (state.progress * 1000).toInt(), state.totalBytes <= 0)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消导出", cancel)
        } else {
            if (files.isNotEmpty()) {
                builder.addAction(
                    android.R.drawable.ic_menu_share, if (files.size > 1) "分享全部" else "分享",
                    activityIntent(ComicExportIntents.share(files), ID + 2)
                )
                if (files.size > 1) builder.addAction(
                    android.R.drawable.ic_menu_view, "打开首个文件",
                    activityIntent(ComicExportIntents.open(files.first()), ID + 3)
                )
            }
            if (state.failures.isNotEmpty() || state.status == ComicExportStatus.CANCELLED || state.status == ComicExportStatus.INTERRUPTED) {
                builder.addAction(
                    android.R.drawable.ic_menu_rotate,
                    "重试未完成文件",
                    activityIntent(retry, ID + 4)
                )
            }
        }
        return builder.build()
    }

    private fun activityIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onTimeout(startId: Int, fgsType: Int) {
        exports.cancel("导出达到系统时限，请重试未完成文件")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}
