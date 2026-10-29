package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState.UIDeviceBatteryStateCharging
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderBatteryStatus
import kotlin.math.roundToInt

/** 监测仅在订阅期间启用，退出后恢复系统原状态。 */
internal fun iosReaderBatteryStatuses() = flow {
    val device = UIDevice.currentDevice
    val previous = device.batteryMonitoringEnabled
    device.batteryMonitoringEnabled = true
    try {
        while (true) {
            val level = device.batteryLevel
            emit(ReaderBatteryStatus(
                percentage = if (level in 0f..1f) (level * 100).roundToInt() else null,
                charging = device.batteryState == UIDeviceBatteryStateCharging
            ))
            delay(30_000)
        }
    } finally {
        device.batteryMonitoringEnabled = previous
    }
}.distinctUntilChanged().flowOn(Dispatchers.Main)
