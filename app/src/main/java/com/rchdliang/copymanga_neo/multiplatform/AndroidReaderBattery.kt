package com.rchdliang.copymanga_neo.multiplatform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderBatteryStatus

/** 接收系统粘性电量广播，离开阅读界面时立即注销。 */
internal fun androidReaderBatteryStatuses(context: Context) = callbackFlow<ReaderBatteryStatus> {
    fun update(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: false
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        trySend(ReaderBatteryStatus(
            percentage = if (present && scale > 0 && level in 0..scale) (level * 100.0 / scale).roundToInt() else null,
            charging = present && status == BatteryManager.BATTERY_STATUS_CHARGING
        ))
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = update(intent)
    }
    update(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()
