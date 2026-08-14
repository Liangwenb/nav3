package com.liangwenb.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy

private const val DialogScrimTestTag = "nav-dialog-scrim"
private const val BottomDialogScrimTestTag = "nav-bottom-dialog-scrim"

/**
 * 注册由 [NavType.Dialog][com.liangwenb.nav.route.NavType.Dialog] 标记的普通弹窗路由。
 *
 * 默认容器会在内容周围展示居中的 Material 3 [Surface]。点击遮罩或按返回键会调用
 * [NavBackStackUtils.back]，内容区域的点击不会关闭当前路由。自动生成的路由入口无需传入
 * [metadata]；只有需要自定义 `DialogSceneStrategy` 元数据时才应覆盖它。
 */
inline fun <reified K : NavKey> EntryProviderScope<NavKey>.dialog(
    metadata: Map<String, Any> = DialogSceneStrategy.dialog(
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ),
    noinline content: @Composable (K) -> Unit,
) {
    entry<K>(metadata = metadata) { key ->
        DialogContent {
            content(key)
        }
    }
}

/**
 * 注册由 [NavType.BottomDialog][com.liangwenb.nav.route.NavType.BottomDialog] 标记的底部弹窗路由。
 *
 * 默认容器提供遮罩、顶部圆角和底部系统栏安全区。点击遮罩或按返回键会调用
 * [NavBackStackUtils.back]，内容区域的点击由页面自身处理，不会关闭当前路由。
 */
inline fun <reified K : NavKey> EntryProviderScope<NavKey>.bottomDialog(
    metadata: Map<String, Any> = DialogSceneStrategy.dialog(
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ),
    noinline content: @Composable (K) -> Unit,
) {
    entry<K>(metadata = metadata) { key ->
        BottomSheetDialog {
            content(key)
        }
    }
}

/**
 * 居中显示普通弹窗内容。
 *
 * 默认情况下，遮罩点击和系统返回都会调用 [NavBackStackUtils.back]。调用方可传入
 * [onDismissRequest] 以便在测试或特殊场景中接管关闭动作。
 */
@Composable
fun DialogContent(
    onDismissRequest: () -> Unit = NavBackStackUtils::back,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .testTag(DialogScrimTestTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            content = content,
        )
    }
}

/**
 * 贴底显示底部弹窗内容。
 *
 * 默认情况下，遮罩点击和系统返回都会调用 [NavBackStackUtils.back]。内容会避开导航栏，
 * 其点击事件不会传播到遮罩；调用方可传入 [onDismissRequest] 接管关闭动作。
 */
@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit = NavBackStackUtils::back,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .testTag(BottomDialogScrimTestTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            content = content,
        )
    }
}
