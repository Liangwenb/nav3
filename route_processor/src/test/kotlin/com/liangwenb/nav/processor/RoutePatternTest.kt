package com.liangwenb.nav.processor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePatternTest {

    @Test
    fun dynamicAndLiteralRoutesWithSameShape_areAmbiguous() {
        assertTrue(areRoutesAmbiguous("profile/{id}", "profile/new"))
        assertTrue(areRoutesAmbiguous("profile/{id}", "profile/{name}"))
        assertTrue(
            areRoutesAmbiguous(
                "profile/{id}?tab={tab}",
                "profile/{id}?sort={sort}",
            ),
        )
    }

    @Test
    fun routesWithDifferentLiteralSegments_areNotAmbiguous() {
        assertFalse(areRoutesAmbiguous("profile/{id}", "settings/{id}"))
        assertFalse(areRoutesAmbiguous("profile/{id}/edit", "profile/{id}/detail"))
    }
}
