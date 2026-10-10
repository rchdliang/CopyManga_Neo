package com.rchdliang.copymanga_neo.data.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rchdliang.copymanga_neo.MainActivity
import com.rchdliang.copymanga_neo.R

/** 下载过程静音更新；完成时独立提醒，快捷操作交给下载服务处理。 */
internal class DownloadNotifications(private val context: Context) {
    companion object {
        const val ID = 1001
        const val CHANNEL = "comic_downloads"
        private const val COMPLETED_ID = 1002
        const val COMPLETED_CHANNEL = "comic_downloads_completed"
        const val OPEN_DOWNLOADS = "com.rchdliang.copymanga_neo.OPEN_DOWNLOADS"

        @Volatile
        private var promotionDismissed = false

        // 与内存下载队列同寿命，重启服务不会重复提醒；再次下载同一漫画仍会提醒。
        private val announcedCompletions = mutableMapOf<String, Pair<String, Int>>()

        /** 新的用户下载会话可再次申请提升；同一会话中尊重用户的通知移除操作。 */
        fun beginSession() {
            promotionDismissed = false
        }

        fun dismissPromotion() {
            promotionDismissed = true
        }
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "漫画下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示下载进度，并提供暂停、继续和取消操作"
                setShowBadge(false)
            })
        manager.createNotificationChannel(
            NotificationChannel(
                COMPLETED_CHANNEL,
                "下载完成",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "漫画下载完成时提醒，可在系统设置中单独关闭声音"
            })
    }

    fun build(state: DownloadNotificationState): Notification {
        val current = state.primary
        val title = when {
            current != null -> current.name
            state.isRunning -> "正在准备下载"
            state.items.isNotEmpty() -> "下载完成"
            else -> "下载队列已清空"
        }
        val chapterCount = current?.let { "${it.completedChapters}/${it.totalChapters}" }
        val detail = if (current?.status == DownloadTaskStatus.COMPLETED) {
            "下载完成 · $chapterCount 章"
        } else if (current?.status == DownloadTaskStatus.WAITING_FOR_WIFI) {
            "等待 Wi-Fi · $chapterCount 章"
        } else if (current != null) {
            "$chapterCount 章 · ${
                Formatter.formatShortFileSize(
                    context,
                    current.speedBytesPerSecond
                )
            }/s"
        } else if (state.items.isNotEmpty()) {
            "${state.items.sumOf { it.completedChapters }}/${state.items.sumOf { it.totalChapters }} 章"
        } else "0/0 章 · 0 B/s"
        val requestLiveUpdate = state.isRunning && current != null && !promotionDismissed &&
                current.status in setOf(
            DownloadTaskStatus.IDLE, DownloadTaskStatus.WAITING,
            DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING
        )

        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(
                if (current?.status == DownloadTaskStatus.COMPLETED) {
                    R.drawable.ic_download_done_notification
                } else R.drawable.ic_download_notification
            )
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openDownloads())
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, ID,
                    Intent(context, DownloadNotificationDismissReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOngoing(state.isRunning)
            .setRequestPromotedOngoing(requestLiveUpdate)
            .setShortCriticalText(
                if (requestLiveUpdate) {
                chapterCount?.takeIf { it.length <= 7 } ?: "${state.progress / 10}%"
            } else null)
            .setAutoCancel(!state.isRunning)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (current != null || state.isRunning) {
                    setProgress(1000, state.progress, current == null)
                    if (Build.VERSION.SDK_INT >= 36) {
                        setStyle(
                            NotificationCompat.ProgressStyle()
                                .setProgressSegments(
                                    listOf(
                                        NotificationCompat.ProgressStyle.Segment(
                                            1000
                                        )
                                    )
                                )
                                .setProgress(state.progress)
                                .setProgressIndeterminate(current == null)
                        )
                    }
                }
                if (state.unfinished.isNotEmpty()) {
                    if (state.isRunning) {
                        addAction(0, "全部暂停", action(DownloadService.PAUSE))
                    } else {
                        addAction(0, "继续下载", action(DownloadService.RESUME))
                    }
                    addAction(0, "取消队列", action(DownloadService.CANCEL))
                }
            }.build()
    }

    private fun action(action: String): PendingIntent = PendingIntent.getForegroundService(
        context, action.hashCode(), Intent(context, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openDownloads(): PendingIntent = PendingIntent.getActivity(
        context, ID,
        Intent(context, MainActivity::class.java).setAction(OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 完成事件使用普通通知；不把已结束的任务继续提升为实时胶囊。 */
    private fun notifyCompleted(items: List<DownloadProgressInfo>) {
        val completed = items.filter {
            it.status == DownloadTaskStatus.COMPLETED && it.totalChapters > 0 &&
                    announcedCompletions[it.comicPathWord] != (it.taskId to it.totalChapters)
        }
        if (completed.isEmpty()) return
        val title = if (completed.size == 1) completed.single().name
        else "${completed.last().name}等 ${completed.size} 部漫画"
        val count =
            "${completed.sumOf { it.completedChapters }}/${completed.sumOf { it.totalChapters }}"
        val summary = if (completed.any { it.statusText.contains("部分评论") })
            "图片下载完成 · $count 章，部分评论未完整下载" else "章节和评论下载完成 · $count 章"
        manager.notify(
            COMPLETED_ID, NotificationCompat.Builder(context, COMPLETED_CHANNEL)
                .setSmallIcon(R.drawable.ic_download_done_notification)
                .setContentTitle(title)
                .setContentText(summary)
                .setContentIntent(openDownloads())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setOngoing(false)
                .setRequestPromotedOngoing(false)
                .build()
        )
        completed.forEach { announcedCompletions[it.comicPathWord] = it.taskId to it.totalChapters }
    }

    fun update(state: DownloadNotificationState) {
        // 用户拒绝通知时仍可运行前台服务，系统负责在活动应用列表中展示它。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (manager.areNotificationsEnabled()) {
            notifyCompleted(state.items)
            if (state.isRunning || state.unfinished.isNotEmpty()) manager.notify(ID, build(state))
        }
    }

    fun cancel() = manager.cancel(ID)
}
