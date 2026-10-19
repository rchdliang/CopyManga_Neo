package com.rchdliang.copymanga_neo.data.local.security

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore

/** 用户明确确认后只写重置请求；不在 DataStore 仍活动时删除凭据。 */
internal fun requestCredentialReset(context: Context) {
    val marker = AtomicFile(File(context.noBackupFilesDir, "reset-credentials.request"))
    val output = marker.startWrite()
    try {
        output.write(byteArrayOf(1))
        output.fd.sync()
        marker.finishWrite(output)
    } catch (failure: Exception) { marker.failWrite(output); throw failure }
    check(marker.openRead().use { it.read() } == 1)
}

/** 在 Hilt 和认证存储创建前执行，重置失败保留请求以供下次重试。 */
internal fun applyRequestedCredentialReset(context: Context): Boolean {
    val marker = AtomicFile(File(context.noBackupFilesDir, "reset-credentials.request"))
    if (!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists()) return false
    check(marker.openRead().use { it.read() } == 1) { "登录重置请求无效，已停止处理" }
    for (name in listOf("auth-v1.preferences_pb", "web-cookies-v1.bin")) {
        val file = File(context.noBackupFilesDir, name)
        AtomicFile(file).delete()
        val temporary = File(file.path + ".tmp")
        check(!temporary.exists() || temporary.delete())
        check(!file.exists() && !File(file.path + ".bak").exists())
    }
    val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    for (name in listOf("auth", "cookies")) keys.deleteEntry(context.packageName + ".secrets." + name + ".v1")
    marker.delete()
    check(!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists())
    return true
}
