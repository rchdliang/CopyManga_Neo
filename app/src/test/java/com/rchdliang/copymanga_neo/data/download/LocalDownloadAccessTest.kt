package com.rchdliang.copymanga_neo.data.download

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class LocalDownloadAccessTest {
    @Test
    fun exportProtectsSelectedChaptersAndTheirComicButAllowsOtherChapters() {
        val access = LocalDownloadAccess()
        access.export(listOf("comic/chapter")).use {
            assertThrows(IOException::class.java) { access.mutate("comic/chapter") }
            assertThrows(IOException::class.java) { access.mutate("comic/") }
            access.mutate("comic/other").close()
            access.mutate("other/").close()
        }
        access.mutate("comic/chapter").close()
    }

    @Test
    fun allReadersMustReleaseBeforeMutationAndCloseIsIdempotent() {
        val access = LocalDownloadAccess()
        val first = access.export(listOf("comic/chapter", "comic/chapter"))
        val second = access.export(listOf("comic/chapter"))
        first.close()
        first.close()
        assertThrows(IOException::class.java) { access.mutate("comic/chapter") }
        second.close()
        access.mutate("comic/").close()
    }

    @Test
    fun inProgressMutationPreventsExportAndFailedAcquisitionDoesNotLeakLease() {
        val access = LocalDownloadAccess()
        access.mutate("comic/chapter").use {
            assertThrows(IOException::class.java) {
                access.export(
                    listOf(
                        "comic/other",
                        "comic/chapter"
                    )
                )
            }
            access.mutate("comic/other").close()
        }
        access.export(listOf("comic/chapter")).close()
    }
}
