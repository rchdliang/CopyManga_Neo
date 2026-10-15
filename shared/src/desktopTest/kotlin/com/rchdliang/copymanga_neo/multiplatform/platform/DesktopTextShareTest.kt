package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopTextShareTest {
    @Test fun cancellationWhileDialogIsOpenNeverCopiesOrOpensMail() = runBlocking {
        for (selection in listOf(0, 1)) {
            val actions = mutableListOf<String>()
            val job = launch {
                val context = currentCoroutineContext()
                performDesktopTextShare(true,
                    choose = { context.cancel(); selection },
                    copy = { actions += "copy" }, mail = { actions += "mail" })
            }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(actions.isEmpty())
        }
    }

    @Test fun alreadyCancelledCallerDoesNotOpenDialog() = runBlocking {
        var opened = false
        val job = launch {
            currentCoroutineContext().cancel()
            performDesktopTextShare(true, choose = { opened = true; 0 },
                copy = { error("Unexpected copy") }, mail = { error("Unexpected mail") })
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(!opened)
    }

    @Test fun activeCallerPerformsOnlyTheSelectedAction() = runBlocking {
        for (mailAvailable in listOf(false, true)) {
            val actions = mutableListOf<String>()
            performDesktopTextShare(mailAvailable, choose = { 0 },
                copy = { actions += "copy" }, mail = { actions += "mail" })
            assertEquals(listOf("copy"), actions)
        }
        val actions = mutableListOf<String>()
        performDesktopTextShare(true, choose = { 1 },
            copy = { actions += "copy" }, mail = { actions += "mail" })
        assertEquals(listOf("mail"), actions)
    }

    @Test fun cancelAndCloseRemainQuietUserCancellation() = runBlocking {
        for ((mailAvailable, selection) in listOf(false to 1, false to -1, true to 2, true to -1)) {
            assertFailsWith<UserActionCancelled> {
                performDesktopTextShare(mailAvailable, choose = { selection },
                    copy = { error("Unexpected copy") }, mail = { error("Unexpected mail") })
            }
        }
    }

    @Test fun actionFailureIsNotReportedAsSuccess(): Unit = runBlocking {
        assertFailsWith<IllegalStateException> {
            performDesktopTextShare(true, choose = { 0 },
                copy = { error("Clipboard unavailable") }, mail = {})
        }
    }
}
