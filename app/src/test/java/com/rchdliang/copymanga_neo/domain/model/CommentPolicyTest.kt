package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentPolicyTest {
    @Test
    fun closeRoastDisablesMangaAndChapterCommentsEvenWhenCloseCommentIsFalse() {
        CommentKind.entries.forEach { kind ->
            assertFalse(CommentPolicy.postingAllowed(kind, closeComment = false, closeRoast = true))
            assertFalse(CommentPolicy.postingAllowed(kind, closeComment = true, closeRoast = true))
        }
    }

    @Test
    fun closeCommentOnlyDisablesMangaComments() {
        assertFalse(
            CommentPolicy.postingAllowed(
                CommentKind.COMIC,
                closeComment = true,
                closeRoast = false
            )
        )
        assertTrue(
            CommentPolicy.postingAllowed(
                CommentKind.CHAPTER,
                closeComment = true,
                closeRoast = false
            )
        )
        CommentKind.entries.forEach { kind ->
            assertTrue(CommentPolicy.postingAllowed(kind, closeComment = false, closeRoast = false))
        }
    }

    @Test
    fun explicitCommentRestrictionIsBusiness210ForBothTransportForms() {
        listOf(
            "/api/v3/comments",
            "/api/v3/roasts",
            "/api/v3/member/comment",
            "/api/v3/member/roast"
        ).forEach { path ->
            assertTrue(CommentPolicy.isClosedResponse(path, 200, 210, "该漫画不允许评论"))
            assertTrue(CommentPolicy.isClosedResponse(path, 210, null, "该漫画不允许评论"))
        }
    }

    @Test
    fun genuineRiskControlAndUnrelatedEndpointsAreNeverExempted() {
        assertFalse(
            CommentPolicy.isClosedResponse(
                "/api/v3/member/comment",
                200,
                210,
                "访问过于频繁"
            )
        )
        assertFalse(CommentPolicy.isClosedResponse("/api/v3/member/comment", 210, null, null))
        assertFalse(
            CommentPolicy.isClosedResponse(
                "/api/v3/comic2/example",
                200,
                210,
                "该漫画不允许评论"
            )
        )
        assertFalse(
            CommentPolicy.isClosedResponse(
                "/comicdetail/example/chapters",
                210,
                null,
                "该漫画不允许评论"
            )
        )
        assertFalse(
            CommentPolicy.isClosedResponse(
                "/api/v3/member/comment",
                200,
                200,
                "该漫画不允许评论"
            )
        )
    }
}
