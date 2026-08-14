package com.liangwenb.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.liangwenb.nav.NavBackStackUtils
import com.liangwenb.nav.generated.appInitEntryProvider
import com.liangwenb.nav.generated.appStringRouteResolver
import com.liangwenb.nav.route.Route
import com.liangwenb.sample.ui.theme.Nav3Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Nav3Theme {
                val backStack = rememberNavBackStack(Home)
                val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
                val stringRouteResolver = remember { appStringRouteResolver() }
                DisposableEffect(backStack, stringRouteResolver) {
                    NavBackStackUtils.attach(this@MainActivity, backStack, stringRouteResolver)
                    onDispose { NavBackStackUtils.detach(this@MainActivity) }
                }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(dialogStrategy),
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        appInitEntryProvider()
                    },
                )
            }
        }
    }

}

@Route(Home::class, route = "home")
@Composable
fun Home() {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize(),
    ) {
        HomeNavigationItem("Key 路由") {
            NavBackStackUtils.go(Page.Key())
        }
        HomeNavigationItem("普通弹窗") {
            NavBackStackUtils.go(Dialog.Sample)
        }
        HomeNavigationItem("字符串路由") {
            NavBackStackUtils.go("page/字符串路由示例")
        }
        HomeNavigationItem("底部弹窗") {
            NavBackStackUtils.go(Dialog.Bottom)
        }
    }
}

@Composable
private fun HomeNavigationItem(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .wrapContentSize(),
    )
}
