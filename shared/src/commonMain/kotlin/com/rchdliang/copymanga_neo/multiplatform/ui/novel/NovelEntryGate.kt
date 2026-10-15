package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord

/** ViewModel 持有入口身份；重组及配置重建不会覆盖用户后来选中的卷和阅读位置。 */
internal class NovelEntryGate {
    private var entry: Pair<String?, LocalReadingRecord?>? = null

    fun accept(path: String?, resume: LocalReadingRecord?): Boolean {
        val next = path to resume
        if (entry == next) return false
        entry = next
        return true
    }
}
