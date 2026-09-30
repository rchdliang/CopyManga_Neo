package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 手机至少三列，宽屏按可用宽度增加列数；剩余空间由网格均匀分配。 */
fun comicGridColumns(contentWidth: Dp): Int =
    ((contentWidth + 8.dp) / 128.dp).toInt().coerceAtLeast(3)
