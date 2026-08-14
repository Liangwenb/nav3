package com.liangwenb.sample

import android.content.ContextWrapper
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.liangwenb.feature.sample.FeatureSample
import com.liangwenb.nav.InterceptResult
import com.liangwenb.nav.NavAction
import com.liangwenb.nav.NavBackStackUtils
import com.liangwenb.nav.NavInterceptor
import com.liangwenb.nav.StringRouteNavigationResult
import com.liangwenb.nav.generated.appStringRouteResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringRouteNavigationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        NavBackStackUtils.detach(context)
        NavBackStackUtils.clearInterceptors()
    }

    @Test
    fun stringRoute_resolvesToKeyAndUsesAttachedBackStack() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())

        val result = NavBackStackUtils.go("page/from-route", context)

        assertEquals(StringRouteNavigationResult.Navigated, result)
        assertEquals(Page.Key("from-route"), backStack.last())
    }

    @Test
    fun dependencyModuleRoute_isAggregatedWithoutFeatureKsp() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())

        val result = NavBackStackUtils.go("feature/from-feature", context)

        assertEquals(StringRouteNavigationResult.Navigated, result)
        assertEquals(FeatureSample("from-feature"), backStack.last())
    }

    @Test
    fun unknownStringRoute_doesNotChangeBackStack() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())

        val result = NavBackStackUtils.go("missing", context)

        assertEquals(StringRouteNavigationResult.NotFound("missing"), result)
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun existingKeyModeAndTwoParameterAttach_remainSupported() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())
        NavBackStackUtils.attach(context, backStack)

        NavBackStackUtils.go(Page.Key("key-mode"), context)

        assertEquals(Page.Key("key-mode"), backStack.last())
        assertEquals(
            StringRouteNavigationResult.NoRouteResolver,
            NavBackStackUtils.go("home", context),
        )
    }

    @Test
    fun stringRoute_withoutContextRejectsAmbiguousAttachedBackStacks() {
        val firstContext = ContextWrapper(context)
        val secondContext = ContextWrapper(context)
        try {
            NavBackStackUtils.attach(
                firstContext,
                NavBackStack<NavKey>(Home),
                appStringRouteResolver(),
            )
            NavBackStackUtils.attach(
                secondContext,
                NavBackStack<NavKey>(Home),
                appStringRouteResolver(),
            )

            assertEquals(
                StringRouteNavigationResult.AmbiguousContext,
                NavBackStackUtils.go("page/from-route"),
            )
        } finally {
            NavBackStackUtils.detach(firstContext)
            NavBackStackUtils.detach(secondContext)
        }
    }

    @Test
    fun stringRoute_usesExistingInterceptorChain() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())
        NavBackStackUtils.addInterceptor(object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult =
                InterceptResult.Cancel("blocked-for-test")
        })

        val result = NavBackStackUtils.go("dialog/sample", context)

        assertEquals(StringRouteNavigationResult.Intercepted("blocked-for-test"), result)
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun stringRoute_usesExistingInterceptorRedirect() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())
        NavBackStackUtils.addInterceptor(object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult =
                if (navKey is Page.Key) InterceptResult.Redirect(Dialog.Sample)
                else InterceptResult.Continue
        })

        val result = NavBackStackUtils.go("page/from-route", context)

        assertEquals(StringRouteNavigationResult.Navigated, result)
        assertEquals(Dialog.Sample, backStack.last())
    }

    @Test
    fun stringRoute_doesNotPushDuplicateTopKey() {
        val backStack = NavBackStack<NavKey>(Home)
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())

        val result = NavBackStackUtils.go("home", context)

        assertEquals(StringRouteNavigationResult.Duplicate, result)
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun stringRoute_goOffAllKeepsOnlyResolvedKey() {
        val backStack = NavBackStack<NavKey>(Home, Page.Key("old"))
        NavBackStackUtils.attach(context, backStack, appStringRouteResolver())

        val result = NavBackStackUtils.goOffAll("dialog/bottom", context)

        assertEquals(StringRouteNavigationResult.Navigated, result)
        assertEquals(listOf(Dialog.Bottom), backStack.toList())
    }
}
