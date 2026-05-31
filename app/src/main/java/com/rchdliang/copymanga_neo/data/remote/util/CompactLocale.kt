package com.rchdliang.copymanga_neo.data.remote.util

import android.icu.text.CompactDecimalFormat
import java.util.Locale


fun Int.toCompactLocale(locale: Locale = Locale.CHINESE): String {
    val formatter = CompactDecimalFormat.getInstance(
        locale,
        CompactDecimalFormat.CompactStyle.SHORT
    )
    return formatter.format(this.toLong())
}