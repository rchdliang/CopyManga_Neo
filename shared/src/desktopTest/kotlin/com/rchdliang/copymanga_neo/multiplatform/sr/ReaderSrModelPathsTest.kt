package com.rchdliang.copymanga_neo.multiplatform.sr

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ReaderSrModelPathsTest {
    @Test fun acceptsDownloadedModelDirectoryOutsideTransientJobCache() {
        val root = Files.createTempDirectory("sr-model-path-").toFile()
        try {
            val files = File(root, "files")
            val downloaded = readerSrModelDirectory(files).apply { mkdirs() }
            val jobCache = File(root, "cache/reader-sr-v1")
            assertFalse(downloaded.canonicalPath.startsWith(jobCache.canonicalPath + File.separator))
            assertEquals(downloaded.canonicalPath, checkedReaderSrModelDirectory(files, downloaded.path))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsJobsSiblingVersionsChildrenAndFiles() {
        val root = Files.createTempDirectory("sr-model-path-").toFile()
        try {
            val files = File(root, "files")
            val downloaded = readerSrModelDirectory(files).apply { mkdirs() }
            val wrong = listOf(
                File(root, "cache/reader-sr-v1/jobs/job-1").apply { mkdirs() },
                File(downloaded.parentFile, "other-version").apply { mkdirs() },
                File(downloaded, "nested").apply { mkdirs() },
                File(downloaded, "scale2.0x_model.bin").apply { writeText("fixture") },
                File(files, "../outside").apply { mkdirs() }
            )
            wrong.forEach { assertFailsWith<IllegalArgumentException> { checkedReaderSrModelDirectory(files, it.path) } }
        } finally { root.deleteRecursively() }
    }
}
