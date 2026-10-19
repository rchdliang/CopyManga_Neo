package com.rchdliang.copymanga_neo.data.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 只允许固定清单中的个人数据；键由平台适配映射，绝不接受任意路径。 */
interface UserDataFiles {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String?)
    suspend fun readJournal(): UserDataRestoreJournal?
    suspend fun writeJournal(journal: UserDataRestoreJournal?)
}

data class UserDataRestoreJournal(
    val phase: String,
    val after: Map<String, String?>,
    val before: Map<String, String?>? = null
)

/** 恢复只在创建仓库/后台消费者之前执行；进程中断后根据日志幂等重放。 */
class UserDataRestore(
    private val files: UserDataFiles,
    private val keys: Set<String>,
    private val validate: suspend (Map<String, String?>) -> Unit
) {
    private val mutex = Mutex()
    private fun checkKeys(values: Map<String, String?>) {
        require(values.keys == keys) { "备份的数据清单不受支持" }
    }

    suspend fun snapshot(): Map<String, String?> = mutex.withLock {
        keys.associateWith { files.read(it) }.also { checkKeys(it); validate(it) }
    }

    suspend fun prepare(values: Map<String, String?>) = mutex.withLock {
        checkKeys(values)
        validate(values)
        check(files.readJournal()?.phase != APPLYING) { "请先完成上次数据恢复" }
        files.writeJournal(UserDataRestoreJournal(PREPARED, values.toMap()))
    }

    suspend fun isPending(): Boolean = mutex.withLock { files.readJournal()?.phase == PREPARED }
    suspend fun cancel() = mutex.withLock {
        check(files.readJournal()?.phase != APPLYING) { "恢复已开始，不能丢弃恢复日志" }
        files.writeJournal(null)
    }

    suspend fun recover(): String? = mutex.withLock {
        var journal = files.readJournal() ?: return@withLock null
        checkKeys(journal.after)
        journal.before?.let(::checkKeys)
        check(
            journal.phase in setOf(
                PREPARED,
                APPLYING,
                COMMITTED,
                ROLLED_BACK
            )
        ) { "恢复记录版本无效" }
        if (journal.phase == ROLLED_BACK) return@withLock "上次数据恢复失败，已恢复原数据。可重新选择备份。"
        if (journal.phase == COMMITTED) {
            clearCompletedJournal()
            return@withLock null
        }
        validate(journal.after)
        if (journal.phase == PREPARED) {
            journal = journal.copy(phase = APPLYING, before = keys.associateWith { files.read(it) })
            files.writeJournal(journal)
        }
        val before = requireNotNull(journal.before) { "恢复记录缺少原数据，已停止写入" }
        try {
            keys.forEach { files.write(it, journal.after[it]) }
            files.writeJournal(journal.copy(phase = COMMITTED))
        } catch (cancel: CancellationException) {
            // 保留 APPLYING；下次启动先完成恢复，再开放正常读写。
            throw cancel
        } catch (failure: Exception) {
            try {
                keys.forEach { files.write(it, before[it]) }
                files.writeJournal(journal.copy(phase = ROLLED_BACK))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                throw IllegalStateException("数据恢复尚未完成，原数据仍在恢复记录中。请释放空间后重新启动。")
            }
            return@withLock "数据恢复失败，已恢复原数据。请检查可用空间后重试。"
        }
        clearCompletedJournal()
        "用户数据已恢复"
    }

    private suspend fun clearCompletedJournal() {
        try {
            files.writeJournal(null)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) { /* 已提交标志保证下次启动不会重复覆盖用户的新数据。 */
        }
    }

    companion object {
        const val PREPARED = "prepared"
        const val APPLYING = "applying"
        const val COMMITTED = "committed"
        const val ROLLED_BACK = "rolled-back"
        const val MAX_ARCHIVE_BYTES = 48 * 1024 * 1024
    }
}
