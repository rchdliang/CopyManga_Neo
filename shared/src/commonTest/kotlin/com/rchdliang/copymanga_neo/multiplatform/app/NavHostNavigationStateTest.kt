package com.rchdliang.copymanga_neo.multiplatform.app

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavHostNavigationStateTest {
    @Test fun legacySavedNavigationRetainsVisitIdsAndPayloadsBeforeGraphAttachment() {
        val routes = listOf(
            AppRoute("detail", id = "first", path = "comic-a"),
            AppRoute("detail", id = "second", path = "comic-a"),
            AppRoute("reader", id = "reader", path = "comic-a", chapter = "chapter-9", page = 42, fraction = .3)
        )
        val encoded = Json.encodeToString(SavedNavigation.serializer(), SavedNavigation(MainTab.FAVORITES, routes))
        val state = NavHostNavigation(Json.decodeFromString(SavedNavigation.serializer(), encoded))
        assertEquals(MainTab.FAVORITES, state.tab)
        assertEquals(routes, state.snapshot().stack)
        assertEquals(routes.last(), state.payload("reader"))
        assertTrue(state.retain(emptySet()).isEmpty(), "Restoration cannot prune before graph attachment")
        assertEquals(routes, state.snapshot().stack)
    }

    @Test fun routeKeyContainsOnlyOpaqueVisitId() {
        val encoded = Json.encodeToString(ContentRouteKey.serializer(), ContentRouteKey("visit-123"))
        assertEquals("{\"id\":\"visit-123\"}", encoded)
    }

    @Test fun actionsCannotRaceGraphRestoration() {
        val state = NavHostNavigation()
        assertFailsWith<IllegalStateException> { state.open(AppRoute("detail")) }
        assertFailsWith<IllegalStateException> { state.select(MainTab.DOWNLOADS) }
        assertEquals(false, state.back())
    }
}
