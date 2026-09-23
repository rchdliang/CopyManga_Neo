package com.rchdliang.copymanga_neo.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 用户移除实时通知后，本次下载只更新普通前台通知，不反复申请置顶。 */
class DownloadNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DownloadNotifications.dismissPromotion()
    }
}
