package com.rchdliang.copymanga_neo.multiplatform.restore

import java.nio.ByteBuffer

/** 三个平台共用的 JNI 边界；像素只在内存中传递。 */
internal object VulkanRestoreBridge {
    @JvmStatic external fun create(allowSoftware: Boolean): Long
    @JvmStatic external fun loadModel(handle: Long, key: Int, data: ByteArray)
    @JvmStatic external fun renderTile(handle: Long, key: Int, source: ByteBuffer, width: Int, height: Int, output: ByteBuffer, x: Int, y: Int)
    @JvmStatic external fun deviceName(handle: Long): String
    @JvmStatic external fun destroy(handle: Long)
}
