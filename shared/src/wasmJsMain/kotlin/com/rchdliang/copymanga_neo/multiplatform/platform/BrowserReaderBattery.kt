@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.JsFun
import kotlin.js.JsAny
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderBatteryStatus

/** 浏览器拒绝或不支持接口时报告未知状态，取消订阅后移除事件监听。 */
internal fun browserReaderBatteryStatuses() = callbackFlow<ReaderBatteryStatus> {
    val subscription = watchBrowserBattery { level, charging ->
        trySend(ReaderBatteryStatus(level.takeIf { it in 0..100 }, charging))
    }
    awaitClose { stopWatchingBrowserBattery(subscription) }
}.distinctUntilChanged()

@JsFun("""onLevel => {
    const state = { stopped: false, battery: null, update: null };
    const unavailable = () => { if (!state.stopped) onLevel(-1, false); };
    if (!globalThis.navigator?.getBattery) { unavailable(); return state; }
    try {
        navigator.getBattery().then(battery => {
            if (state.stopped) return;
            state.battery = battery;
            state.update = () => {
                if (!state.stopped) onLevel(Number.isFinite(battery.level) && battery.level >= 0 && battery.level <= 1
                    ? Math.round(battery.level * 100) : -1, battery.charging === true && battery.level < 1);
            };
            battery.addEventListener('levelchange', state.update);
            battery.addEventListener('chargingchange', state.update);
            state.update();
        }).catch(unavailable);
    } catch (_) { unavailable(); }
    return state;
}""")
private external fun watchBrowserBattery(onLevel: (Int, Boolean) -> Unit): JsAny

@JsFun("""state => {
    state.stopped = true;
    if (state.battery && state.update) {
        state.battery.removeEventListener('levelchange', state.update);
        state.battery.removeEventListener('chargingchange', state.update);
    }
}""")
private external fun stopWatchingBrowserBattery(state: JsAny)
