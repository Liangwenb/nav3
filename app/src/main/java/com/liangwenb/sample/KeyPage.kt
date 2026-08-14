package com.liangwenb.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liangwenb.nav.route.Route

@Route(Page.Key::class, route = "page/{message}")
@Composable
fun KeyPage(key: Page.Key) {
    Text(key.message, modifier = Modifier
        .fillMaxSize()
        .wrapContentSize())
}

@Route(
    key = Page.RouteArgs::class,
    route = "page/detail/{id}?highlighted={highlighted}&tab={tab}",
)
@Composable
fun RouteArgsPage(key: Page.RouteArgs) {
    Text(
        "id=${key.id}, highlighted=${key.highlighted}, tab=${key.tab}",
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(),
    )
}
