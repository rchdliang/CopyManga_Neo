package com.rchdliang.copymanga_neo.multiplatform.ui.components

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** Chinese short decimal units with the original ICU formatter's two significant digits. */
fun Int.toCompactLocale(): String {
    val magnitude = abs(toLong())
    val (divisor, suffix) = when {
        magnitude >= 100_000_000L -> 100_000_000L to "亿"
        magnitude >= 10_000L -> 10_000L to "万"
        else -> return toString()
    }
    val value = magnitude.toDouble() / divisor
    val scale = 10.0.pow(1 - floor(log10(value)))
    val rounded = round(value * scale) / scale
    if (divisor == 10_000L && rounded >= 10_000.0) {
        return (if (this < 0) "-" else "") + "1亿"
    }
    val number = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
    return (if (this < 0) "-" else "") + number + suffix
}
