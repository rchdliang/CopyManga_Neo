package com.rchdliang.copymanga_neo.multiplatform.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationStateTest {
    @Test fun originalTabOrderIsPreserved() {
        assertEquals(listOf(MainTab.HOME, MainTab.DOWNLOADS, MainTab.FAVORITES, MainTab.PROFILE), MainTab.entries)
    }
    @Test fun homeRankArgumentsRemainSeparateAndDecoded() {
        val arguments = exploreArguments("ranks?audience=male&period=week&topicTitle=%E6%8E%A8%E8%8D%90")
        assertEquals(mapOf("section" to "ranks", "audience" to "male", "period" to "week", "topicTitle" to "推荐"), arguments)
    }
    @Test fun sectionCannotBeOverriddenByQuery() {
        assertEquals(mapOf("section" to "filter", "top" to "new"), exploreArguments("filter?section=ranks&top=new"))
    }
}
