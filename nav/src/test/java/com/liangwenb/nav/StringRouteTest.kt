package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringRouteTest {

    @Test
    fun resolver_buildsKeyFromPathAndOptionalQuery() {
        val resolver = StringRouteResolver.of(
            StringRouteDefinition("profile/{id}?tab={tab}", createKey = { arguments ->
                Profile(
                    id = arguments.required("id"),
                    tab = arguments.optional("tab"),
                )
            }),
        )

        assertEquals(
            StringRouteResolveResult.Matched(Profile("42", "posts")),
            resolver.resolve("profile/42?tab=posts"),
        )
        assertEquals(
            StringRouteResolveResult.Matched(Profile("42", null)),
            resolver.resolve("profile/42"),
        )
    }

    @Test
    fun resolver_rejectsMissingPathAndDuplicateQueryValues() {
        val resolver = StringRouteResolver.of(
            StringRouteDefinition("profile/{id}?tab={tab}", createKey = { arguments ->
                Profile(arguments.required("id"), arguments.optional("tab"))
            }),
        )

        assertTrue(resolver.resolve("profile") is StringRouteResolveResult.NotFound)
        assertTrue(
            resolver.resolve("profile/42?tab=one&tab=two") is StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("profile/42?unknown=value") is StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("https://example.com/profile/42") is StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("profile/%ZZ") is StringRouteResolveResult.Invalid,
        )
        assertTrue(
            resolver.resolve("profile/42?tab") is StringRouteResolveResult.Invalid,
        )
    }

    @Test
    fun resolver_preservesPlusInPathAndDecodesPlusAsSpaceInQuery() {
        val resolver = StringRouteResolver.of(
            StringRouteDefinition("profile/{id}?tab={tab}", createKey = { arguments ->
                Profile(arguments.required("id"), arguments.optional("tab"))
            }),
        )

        assertEquals(
            StringRouteResolveResult.Matched(Profile("a+b", "top posts")),
            resolver.resolve("profile/a+b?tab=top+posts"),
        )
    }

    @Test
    fun formatter_percentEncodesValuesAndRoundTrips() {
        val resolver = StringRouteResolver.of(
            StringRouteDefinition(
                pattern = "profile/{id}?tab={tab}",
                formatKey = { key ->
                    (key as? Profile)?.let {
                        formatStringRoute(
                            "profile/{id}?tab={tab}",
                            mapOf("id" to it.id, "tab" to it.tab),
                        )
                    }
                },
                createKey = { arguments ->
                    Profile(arguments.required("id"), arguments.optional("tab"))
                },
            ),
        )
        val key = Profile("a b", "news/latest")

        val route = resolver.format(key)

        assertEquals("profile/a%20b?tab=news%2Flatest", route)
        assertEquals(StringRouteResolveResult.Matched(key), resolver.resolve(route!!))
        assertEquals(null, resolver.format(Unknown))
        assertEquals("First", encodeStringRouteValue(RouteTab.First))
    }

    private data class Profile(
        val id: String,
        val tab: String?,
    ) : NavKey

    private data object Unknown : NavKey

    private enum class RouteTab {
        First;

        override fun toString(): String = "custom-label"
    }
}
