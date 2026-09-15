package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DownloadedFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun interruptedBodyNeverPublishesAPartialImage() {
        val target = File(folder.root, "page.jpg")
        assertThrows(IOException::class.java) {
            writeDownloadedImage(target, byteArrayOf(1, 2).inputStream(), 4)
        }
        assertFalse(target.exists())
        assertFalse(File(folder.root, "page.jpg.part").exists())
        writeDownloadedImage(target, byteArrayOf(1, 2, 3, 4).inputStream(), 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), target.readBytes())
    }

    @Test
    fun cancellationDuringWritePreservesPreviousCompleteFile() {
        val target = folder.newFile("page.jpg").apply { writeBytes(byteArrayOf(9)) }
        assertThrows(IOException::class.java) {
            writeDownloadedImage(target, byteArrayOf(1, 2).inputStream(), 2) {
                throw IOException("cancelled")
            }
        }
        assertArrayEquals(byteArrayOf(9), target.readBytes())
        assertFalse(File(folder.root, "page.jpg.part").exists())
        writeDownloadedImage(target, byteArrayOf(3, 4).inputStream(), 2)
        assertArrayEquals(byteArrayOf(3, 4), target.readBytes())
    }

    @Test
    fun emptyResponseIsNeverACompletedImage() {
        assertThrows(IOException::class.java) {
            writeDownloadedImage(File(folder.root, "page.jpg"), byteArrayOf().inputStream(), 0)
        }
    }

    @Test
    fun chapterRequiresEveryRecordedImageAndCompletedStatus() {
        val page = folder.newFile("page.jpg").apply { writeBytes(byteArrayOf(1)) }
        val chapter =
            DownloadedChapterEntity("one", "comic", "one", 1, 1, page.path, "downloaded", 0)
        assertTrue(chapter.hasCompleteFiles())
        assertFalse(chapter.copy(status = "error").hasCompleteFiles())
        assertFalse(chapter.copy(imageCount = 2).hasCompleteFiles())
        assertFalse(chapter.copy(imageCount = 0, imagePaths = "").hasCompleteFiles())
        assertFalse(
            chapter.copy(imageCount = 2, imagePaths = "${page.path},${page.path}")
                .hasCompleteFiles()
        )
        page.delete()
        assertFalse(chapter.hasCompleteFiles())
    }
}
