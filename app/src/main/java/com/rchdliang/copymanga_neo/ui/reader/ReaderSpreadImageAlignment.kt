package com.rchdliang.copymanga_neo.ui.reader
import androidx.compose.ui.Alignment

/** 视觉左右两页贴合书脊；等宽命中区、Fit 比例及图像自带白边不变。 */
internal fun readerSpreadImageAlignment(leaves: Int, visualIndex: Int): Alignment = when {
    leaves != 2 -> Alignment.Center
    visualIndex == 0 -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}
