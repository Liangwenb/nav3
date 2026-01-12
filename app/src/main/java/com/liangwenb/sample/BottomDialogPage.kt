package com.liangwenb.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route

@Route(Dialog.Bottom::class, NavType.BottomDialog)
@Composable
fun BottomDialogPage(key: Dialog.Bottom) {
    Text(key.toString(), modifier = Modifier
        .fillMaxWidth().height(300.dp).background(Color.LightGray)
        .wrapContentSize())
}
