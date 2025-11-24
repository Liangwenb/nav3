package com.liangwenb.sample

import androidx.navigation3.runtime.NavKey
import com.liangwenb.nav.ResultNavKey
import kotlinx.serialization.Serializable


@Serializable
data object Home : NavKey

@Serializable
data object Page {
    /**
     * 把key传递到 Page 中
     */
    @Serializable
    data class Key(val message: String = "把key传递到 Page 中") : NavKey

    /**
     * 把key传递到 viewModel 中
     */
    @Serializable
    data object KeyViewModel : NavKey

    /**
     * 带返回值界面
     */
    @Serializable
    data object Result : ResultNavKey<Int>()
}

@Serializable
data object Dialog : NavKey {

    /**
     * 实例Dialog
     */
    @Serializable
    data object Sample

    /**
     * 底部Dialog
     */
    @Serializable
    data object Bottom

}