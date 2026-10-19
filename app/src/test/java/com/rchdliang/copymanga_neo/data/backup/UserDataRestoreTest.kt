package com.rchdliang.copymanga_neo.data.backup

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class UserDataRestoreTest {
    private class Memory : UserDataFiles {
        val data = linkedMapOf<String, String?>("settings" to "old", "notes" to "old notes")
        var journal: UserDataRestoreJournal? = null
        var failOnce: String? = null
        var crashOnce: String? = null
        var failCleanup = false
        override suspend fun read(key: String) = data[key]
        override suspend fun write(key: String, value: String?) {
            if (crashOnce == key) {
                crashOnce = null; throw SimulatedPowerLoss()
            }
            if (failOnce == key) {
                failOnce = null; error("disk full")
            }
            data[key] = value
        }

        override suspend fun readJournal() = journal
        override suspend fun writeJournal(journal: UserDataRestoreJournal?) {
            if (journal == null && failCleanup) error("cleanup failed")
            this.journal = journal
        }
    }

    private class SimulatedPowerLoss : Error()

    private val keys = linkedSetOf("settings", "notes")
    private val incoming = linkedMapOf<String, String?>("settings" to "new", "notes" to "new notes")
    private fun engine(disk: Memory) = UserDataRestore(disk, keys) { values ->
        require(values.values.none { it == "invalid" })
    }

    @Test
    fun prepareDoesNotMutateLiveDataAndInvalidInputNeverStages() = runBlocking {
        val disk = Memory()
        val restore = engine(disk)
        assertTrue(runCatching { restore.prepare(incoming + ("../auth" to "bad")) }.isFailure)
        assertNull(disk.journal)
        assertTrue(runCatching { restore.prepare(incoming + ("notes" to "invalid")) }.isFailure)
        assertNull(disk.journal)
        restore.prepare(incoming)
        assertEquals("old", disk.data["settings"])
        assertTrue(restore.isPending())
        restore.cancel()
        assertFalse(restore.isPending())
        assertEquals("old notes", disk.data["notes"])
    }

    @Test
    fun ioFailureRollsBackBothFiles() = runBlocking {
        val disk = Memory()
        val restore = engine(disk)
        restore.prepare(incoming)
        disk.failOnce = "notes"
        assertTrue(restore.recover()!!.contains("已恢复原数据"))
        assertEquals("old", disk.data["settings"])
        assertEquals("old notes", disk.data["notes"])
        assertEquals(UserDataRestore.ROLLED_BACK, disk.journal!!.phase)
        restore.recover()
        assertEquals("old", disk.data["settings"])
    }

    @Test
    fun processDeathCanResumeBeforeConsumersStart() = runBlocking {
        val disk = Memory()
        engine(disk).prepare(incoming)
        disk.crashOnce = "notes"
        assertTrue(runCatching { engine(disk).recover() }.exceptionOrNull() is SimulatedPowerLoss)
        assertEquals(UserDataRestore.APPLYING, disk.journal!!.phase)
        assertEquals("old", disk.journal!!.before!!["settings"])
        engine(disk).recover()
        assertEquals(incoming, disk.data)
        assertNull(disk.journal)
    }

    @Test
    fun committedJournalCleanupFailureDoesNotReplayOverNewUserWrites() = runBlocking {
        val disk = Memory()
        val restore = engine(disk)
        restore.prepare(incoming)
        disk.failCleanup = true
        restore.recover()
        assertEquals(UserDataRestore.COMMITTED, disk.journal!!.phase)
        disk.data["notes"] = "written after recovery"
        engine(disk).recover()
        assertEquals("written after recovery", disk.data["notes"])
    }

    @Test
    fun rollbackUsesDataAtRestartRatherThanAtSelectionTime() = runBlocking {
        val disk = Memory()
        val restore = engine(disk)
        restore.prepare(incoming)
        disk.data["notes"] = "last reading before exit"
        disk.failOnce = "notes"
        restore.recover()
        assertEquals("last reading before exit", disk.data["notes"])
    }
}
