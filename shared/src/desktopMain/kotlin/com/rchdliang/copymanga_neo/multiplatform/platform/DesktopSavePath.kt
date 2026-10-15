package com.rchdliang.copymanga_neo.multiplatform.platform

import java.nio.file.Path

/** Restore the suggested suffix only for bare names; explicit dotted names stay user-controlled. */
internal fun resolveDesktopSavePath(selected: Path, suggestedName: String): Path {
    val selectedName = selected.fileName?.toString() ?: return selected
    if (selectedName.isEmpty() || '.' in selectedName) return selected
    val suggestedLeaf = suggestedName.substringAfterLast('/').substringAfterLast('\\')
    val suffixStart = suggestedLeaf.lastIndexOf('.')
    if (suffixStart <= 0 || suffixStart == suggestedLeaf.lastIndex) return selected
    return selected.resolveSibling(selectedName + suggestedLeaf.substring(suffixStart))
}
