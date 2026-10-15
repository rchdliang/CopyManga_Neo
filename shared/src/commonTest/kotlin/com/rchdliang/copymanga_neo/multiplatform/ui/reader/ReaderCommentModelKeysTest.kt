package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderCommentModelKeysTest {
    @Test
    fun modalCannotReuseAnyInlineChapterOwner() {
        listOf("", "chapter-1", "reader-modal", "reader-inline:chapter-1").forEach { chapter ->
            assertNotEquals(ReaderCommentModelKeys.modal, ReaderCommentModelKeys.inline(chapter))
        }
    }

    @Test
    fun inlineOwnersAreStableAndDistinctForDifferentChapters() {
        assertEquals(ReaderCommentModelKeys.inline("chapter-1"), ReaderCommentModelKeys.inline("chapter-1"))
        assertNotEquals(ReaderCommentModelKeys.inline("chapter-1"), ReaderCommentModelKeys.inline("chapter-2"))
    }
}
