package com.liangwenb.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liangwenb.nav.NavBackStackUtils
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route

/** 展示 [NavType.Dialog] 的默认居中弹窗容器。 */
@Route(Dialog.Sample::class, NavType.Dialog, route = "dialog/sample")
@Composable
fun DialogPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text("普通弹窗")
        Text("点击遮罩或返回键会关闭，也可以通过按钮显式关闭。")
        Button(onClick = NavBackStackUtils::back) {
            Text("关闭")
        }
    }
}
