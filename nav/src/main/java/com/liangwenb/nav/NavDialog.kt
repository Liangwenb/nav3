package com.liangwenb.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy

/**
 * 普通弹窗
 */
inline fun <reified K : NavKey> EntryProviderScope<NavKey>.dialog(
    metadata: Map<String, Any> = DialogSceneStrategy.dialog(
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ),
    noinline content: @Composable (K) -> Unit,
) {
    entry<K>(metadata = metadata, content = { key ->
        content(key)
    })
}

/**
 * 底部弹窗
 */
inline fun <reified K : NavKey> EntryProviderScope<NavKey>.bottomDialog(
    metadata: Map<String, Any> = DialogSceneStrategy.dialog(
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ),
    noinline content: @Composable (K) -> Unit,
) {

    entry<K>(metadata = metadata) { key ->
        BottomSheetDialog {
//            val window = (LocalView.current.parent as DialogWindowProvider).window
//            window.setDimAmount(0.5f)
            content(key)
        }
    }
}
@Composable
fun BottomSheetDialog(content: @Composable () -> Unit) {

    Box(
        contentAlignment = Alignment.BottomCenter, modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null  // 去掉水波纹
            ) {
                NavBackStackUtils.back()
            }) {

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter) // 关键：保持在底部
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {}) // 拦截点击，不让外层响应
                }
        ) {
            content()
        }
    }
}