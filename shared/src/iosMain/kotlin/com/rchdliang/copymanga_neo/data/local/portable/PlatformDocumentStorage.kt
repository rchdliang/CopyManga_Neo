@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.local.portable

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

actual fun platformDocumentStorage(directory: String): DocumentStorage = object : DocumentStorage {
    private val files = NSFileManager.defaultManager
    private fun path(key: String): String {
        require(key.matches(Regex("[A-Za-z0-9_.-]+")) && key != "." && key != "..")
        return "${directory.trimEnd('/')}/$key"
    }
    override suspend fun read(key: String): String? {
        val path = path(key)
        if (!files.fileExistsAtPath(path)) return null
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: error("无法读取本地数据：$key")
    }
    override suspend fun write(key: String, value: String) {
        check(files.createDirectoryAtPath(directory, true, null, null)) { "无法创建应用存储目录" }
        check(NSString.create(string = value).writeToFile(path(key), true, NSUTF8StringEncoding, null)) {
            "本地数据原子保存失败：$key"
        }
    }
    override suspend fun remove(key: String) {
        val path = path(key)
        if (files.fileExistsAtPath(path)) check(files.removeItemAtPath(path, null)) { "本地数据删除失败：$key" }
    }
}
