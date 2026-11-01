package com.rchdliang.copymanga_neo.multiplatform.platform

/**
 * Nucleus 2.5.18 的 Windows Tao 宿主将 Ctrl＋滚轮转换为双指事件，曲线为 2^(刻度/12)。
 * 对应 TaoWheelPinchZoom.stepFromWheelDelta；AWT 与其他平台不能套用此换算。
 */
internal fun desktopCtrlWheelPinchStepsPerDoubling(osName: String, nativeWindow: Boolean): Float? =
    if (nativeWindow && osName.startsWith("Windows", ignoreCase = true)) 12f else null
