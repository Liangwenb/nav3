package com.liangwenb.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liangwenb.nav.KeyViewModel
import com.rizzmeup.route_annotation.Route

@Route(Page.KeyViewModel::class)
@Composable
fun KeyViewModelPage(viewModel: KeyViewModelPageViewModel) {
    Column(Modifier.fillMaxSize()) {


    }
}
class  KeyViewModelPageViewModel(key: Page.KeyViewModel) : KeyViewModel<Page.KeyViewModel>(key) {

}