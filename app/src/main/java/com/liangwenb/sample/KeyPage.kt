package com.liangwenb.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liangwenb.nav.route.Route

@Route(Page.Key::class)
@Composable
fun KeyPage(key: Page.Key) {
    Text(key.message, modifier = Modifier
        .fillMaxSize()
        .wrapContentSize())
}
