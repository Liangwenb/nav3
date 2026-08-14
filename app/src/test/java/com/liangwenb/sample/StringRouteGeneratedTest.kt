package com.liangwenb.sample

import com.liangwenb.feature.sample.FeatureSample
import com.liangwenb.feature.sample.FeatureSheet
import com.liangwenb.nav.StringRouteResolveResult
import com.liangwenb.nav.generated.appStringRouteResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringRouteGeneratedTest {

    private val resolver = appStringRouteResolver()

    @Test
    fun generatedResolver_parsesScreenAndDialogRoutes() {
        assertEquals(
            StringRouteResolveResult.Matched(Home),
            resolver.resolve("home"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(Page.Key("from-route")),
            resolver.resolve("page/from-route"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(FeatureSample("from-feature")),
            resolver.resolve("feature/from-feature"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(FeatureSheet),
            resolver.resolve("dialog/feature-sheet"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(Dialog.Sample),
            resolver.resolve("dialog/sample"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(Dialog.Bottom),
            resolver.resolve("dialog/bottom"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(
                Page.RouteArgs(id = 7L, highlighted = true, tab = Page.Tab.Posts),
            ),
            resolver.resolve("page/detail/7?highlighted=true&tab=Posts"),
        )
    }

    @Test
    fun generatedResolver_formatsCanonicalRoute() {
        assertEquals("home", resolver.format(Home))
        assertEquals(null, resolver.format(Page.Result))
        assertEquals("page/a%20b", resolver.format(Page.Key("a b")))
        assertEquals("feature/a%20b", resolver.format(FeatureSample("a b")))
        assertEquals("dialog/feature-sheet", resolver.format(FeatureSheet))
        assertEquals("dialog/sample", resolver.format(Dialog.Sample))
        assertEquals(
            "page/detail/7?highlighted=true&tab=Posts",
            resolver.format(Page.RouteArgs(7L, highlighted = true, tab = Page.Tab.Posts)),
        )
        assertEquals(
            "page/detail/7?highlighted=false",
            resolver.format(Page.RouteArgs(7L, highlighted = false)),
        )
    }

    @Test
    fun generatedResolver_reportsInvalidScalarArguments() {
        assertTrue(
            resolver.resolve("page/detail/not-a-number?highlighted=true") is
                StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("page/detail/7") is StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("page/detail/7?highlighted=true&tab=Unknown") is
                StringRouteResolveResult.Invalid,
        )
    }
}
