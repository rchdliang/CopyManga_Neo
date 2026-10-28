package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlin.test.*

class PortableNovelAnnotationsTest {
    private class Documents : DocumentStorage {
        val rows = mutableMapOf<String, String>()
        override suspend fun read(key: String) = rows[key]
        override suspend fun write(key: String, value: String) { rows[key] = value }
        override suspend fun remove(key: String) { rows.remove(key) }
    }
    private val original = NovelAnnotation("id", "owner", "WEB", "book", "volume", "第一卷",
        "示例😀", NovelTextRange(1, 2, 1, 6), "TERTIARY", "多行\n笔记", 10, 20)
    @Test fun sameRangeKeepsIdentityAndOtherAccountsStaySeparate() = runTest {
        val files = Documents()
        val repository = PortableNovelAnnotations(files)
        repository.initialize().getOrThrow()
        repository.save(original).getOrThrow()
        repository.save(original.copy(id = "new", note = "更新", createdAt = 100)).getOrThrow()
        assertEquals("id", repository.annotations.first().single().id)
        assertEquals(10, repository.annotations.first().single().createdAt)
        for (other in listOf(original.copy(owner = "other"), original.copy(mode = "APP"),
            original.copy(path = "other"), original.copy(volume = "other"))) repository.save(other).getOrThrow()
        assertEquals(4, repository.annotations.first().size)
        repository.remove(original).getOrThrow()
        assertEquals(3, repository.annotations.first().size)
        val restored = PortableNovelAnnotations(files)
        restored.initialize().getOrThrow()
        assertEquals(repository.annotations.first(), restored.annotations.first())
    }
    @Test fun corruptDataAndInvalidRangesCannotEraseSavedNotes() = runTest {
        val files = Documents()
        val repository = PortableNovelAnnotations(files)
        repository.save(original).getOrThrow()
        assertTrue(repository.save(original.copy(range = NovelTextRange(1, 2, 1, 2))).isFailure)
        assertEquals(listOf(original), repository.annotations.first())
        files.rows["novel-annotations.json"] = "{broken"
        val restored = PortableNovelAnnotations(files)
        assertTrue(restored.initialize().isFailure)
        assertTrue(restored.save(original).isFailure)
        assertEquals("{broken", files.rows["novel-annotations.json"])
    }
}
