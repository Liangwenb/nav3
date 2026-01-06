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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.liangwenb.app.generated.appInitEntryProvider
import com.liangwenb.nav.KeyViewModel
import com.liangwenb.nav.NavBackStackUtils
import com.liangwenb.nav.ResultNavKey
import com.liangwenb.sample.ui.theme.Nav3Theme
import com.rizzmeup.route_annotation.NavType
import com.rizzmeup.route_annotation.Route
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Nav3Theme {
                val backStack = rememberNavBackStack(Home)
                val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
                NavBackStackUtils.attach(this, backStack)
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategy = dialogStrategy,
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
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        //将生成的路由方法放到这里
                        appInitEntryProvider()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NavBackStackUtils.detach(this)

    }
}


@Route(Home::class)
@Composable
fun Home() {
    Column(
        Modifier
            .systemBarsPadding()
            .fillMaxSize()
    ) {

        Text(
            "跳转到把Key传递到Page中", modifier = Modifier
                .clickable {
                    NavBackStackUtils.go(Page.Key())
                }
                .fillMaxWidth()
                .height(56.dp)
                .wrapContentSize())
        Text(
            "底部弹窗", modifier = Modifier
                .clickable {
                    NavBackStackUtils.go(Dialog.Bottom)
                }
                .fillMaxWidth()
                .height(56.dp)
                .wrapContentSize())
    }
}


