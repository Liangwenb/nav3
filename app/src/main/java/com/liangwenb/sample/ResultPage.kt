package com.liangwenb.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route

// 保留原有二参数注解写法，KSP 仍只生成 Key 路由入口。
@Route(Page.Result::class, NavType.Screen)
@Composable
fun ResultPage(key: Page.Result) {
    Column(Modifier.fillMaxSize()) {
    }
}
