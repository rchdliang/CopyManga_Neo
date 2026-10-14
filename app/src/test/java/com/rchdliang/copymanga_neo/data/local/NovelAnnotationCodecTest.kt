package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelAnnotationCodecTest {
    private val codec = NovelAnnotationCodec()
    private val original = NovelAnnotation(
        "id", "owner", "WEB", "book", "volume", "第一卷",
        "示例😀", NovelTextRange(1, 2, 1, 6), "TERTIARY", "保留\n多行笔记", 10L, 20L
    )

    @Test
    fun unicodeTextNoteAndCoordinatesRoundTrip() {
        assertEquals(listOf(original), codec.decode(codec.encode(listOf(original))))
    }

    @Test
    fun savingSameRangeUpdatesNoteAndKeepsIdentityAndCreationTime() {
        val updated = original.copy(id = "new", note = "新笔记", createdAt = 100, updatedAt = 200)
        val values = codec.upsert(listOf(original), updated)
        assertEquals(1, values.size)
        assertEquals("id", values.single().id)
        assertEquals(10L, values.single().createdAt)
        assertEquals("新笔记", values.single().note)
    }

    @Test
    fun sameCoordinatesInOtherAccountModeBookAndVolumeRemainIsolated() {
        for (other in listOf(
            original.copy(owner = "other"), original.copy(mode = "APP"),
            original.copy(path = "other-book"), original.copy(volume = "other-volume")
        )) {
            assertEquals(2, codec.upsert(listOf(original), other).size)
        }
    }

    @Test(expected = Exception::class)
    fun corruptDataIsRejectedInsteadOfBeingSilentlyDiscarded() {
        codec.decode("[{")
    }

    @Test(expected = Exception::class)
    fun explicitNullCannotEraseExistingAnnotations() {
        codec.decode("null")
    }

    @Test
    fun missingOptionalNoteAndStyleUseSafeDefaults() {
        val json =
            codec.encode(listOf(original)).replace("\"note\":\"保留\\n多行笔记\"", "\"note\":null")
                .replace("\"style\":\"TERTIARY\"", "\"style\":null")
        val restored = codec.decode(json).single()
        assertEquals("", restored.note)
        assertEquals("PRIMARY", restored.style)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRangeCannotBeWritten() {
        codec.upsert(emptyList(), original.copy(range = NovelTextRange(1, 2, 1, 2)))
    }
}
