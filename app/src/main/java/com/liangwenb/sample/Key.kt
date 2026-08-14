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

    @Serializable
    enum class Tab { Info, Posts }

    /** 展示字符串路由的数值、布尔值、枚举与可空 query 参数。 */
    @Serializable
    data class RouteArgs(
        val id: Long,
        val highlighted: Boolean,
        val tab: Tab? = null,
    ) : NavKey

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

    /** 普通弹窗。 */
    @Serializable
    data object Sample : NavKey

    /** 底部弹窗。 */
    @Serializable
    data object Bottom : NavKey

}
