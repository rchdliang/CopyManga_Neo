package com.rchdliang.copymanga_neo.domain.reading

/** 仅撤销仍然等于导入结果的差异，保留导入后的编辑和删除。 */
object ImportRollbackPolicy {
    fun <K, V> restore(before: Map<K, V>, applied: Map<K, V>, current: Map<K, V>): Map<K, V> {
        val restored = current.toMutableMap()
        for (key in before.keys + applied.keys) {
            val changed =
                before.containsKey(key) != applied.containsKey(key) || before[key] != applied[key]
            val stillApplied =
                current.containsKey(key) == applied.containsKey(key) && current[key] == applied[key]
            if (changed && stillApplied) {
                if (before.containsKey(key)) restored[key] = before.getValue(key)
                else restored.remove(key)
            }
        }
        return restored
    }
}
