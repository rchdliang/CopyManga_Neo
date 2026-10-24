package com.rchdliang.copymanga_neo.multiplatform.sr

import com.rchdliang.copymanga_neo.domain.reading.ReaderSrModel
import java.io.File

/** Android 按需模型位于持久文件目录，不能再按临时推理任务目录校验。 */
internal fun readerSrModelDirectory(filesDir: File): File =
    File(filesDir, "reader-sr-models/cunet-${ReaderSrModel.model.version}")

/** 仅接受当前固定模型目录；不放宽输入／输出任务目录的原有边界。 */
internal fun checkedReaderSrModelDirectory(filesDir: File, path: String): String {
    val expected = readerSrModelDirectory(filesDir).canonicalFile
    val actual = File(path).canonicalFile
    require(actual == expected && actual.isDirectory) { "超分模型目录无效" }
    return actual.path
}
