package com.rchdliang.copymanga_neo.multiplatform.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderBatteryStatus

/** 在后台读取系统电量及充电状态，不启动外部命令。 */
internal fun desktopReaderBatteryStatuses() = flow {
    while (true) {
        emit(readDesktopBatteryStatus())
        delay(30_000)
    }
}.distinctUntilChanged().flowOn(Dispatchers.IO)

private fun readDesktopBatteryStatus(): ReaderBatteryStatus = try {
    when {
        System.getProperty("os.name").startsWith("Windows", true) -> Memory(12).use { status ->
            val function = Function.getFunction("kernel32", "GetSystemPowerStatus", Function.ALT_CONVENTION)
            if (function.invokeInt(arrayOf(status)) == 0) ReaderBatteryStatus()
            else windowsBatteryStatus(status.getByte(1).toInt() and 255, status.getByte(2).toInt() and 255)
        }
        System.getProperty("os.name").startsWith("Linux", true) -> {
            File("/sys/class/power_supply").listFiles().orEmpty()
                .firstOrNull { File(it, "type").readText().trim() == "Battery" }
                ?.let {
                    val percentage = File(it, "capacity").takeIf(File::isFile)?.readText()?.trim()?.toIntOrNull()
                    val status = File(it, "status").takeIf(File::isFile)?.readText()?.trim()
                    ReaderBatteryStatus(percentage?.takeIf { level -> level in 0..100 }, status == "Charging")
                } ?: ReaderBatteryStatus()
        }
        else -> ReaderBatteryStatus()
    }
} catch (_: Exception) {
    ReaderBatteryStatus()
} catch (_: LinkageError) {
    ReaderBatteryStatus()
}

internal fun windowsBatteryStatus(flags: Int, percent: Int): ReaderBatteryStatus =
    if (flags == 255 || flags and 128 != 0) ReaderBatteryStatus()
    else ReaderBatteryStatus(percent.takeIf { it in 0..100 }, flags and 8 != 0)
