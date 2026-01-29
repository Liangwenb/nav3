package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey

/**
 * 导航拦截器接口
 * 用于在导航跳转时进行拦截、重定向或阻止跳转
 */
interface NavInterceptor {
    /**
     * 拦截导航
     * @param navKey 要导航到的目标 NavKey
     * @param action 导航动作类型
     * @return 拦截结果
     */
    fun intercept(navKey: NavKey, action: NavAction): InterceptResult
}

/**
 * 导航动作类型
 */
enum class NavAction {
    /** 普通跳转 */
    GO,
    /** 跳转并清空其他界面 */
    GO_OFF_ALL,
    /** 带结果的跳转 */
    GO_RESULT
}

/**
 * 拦截结果
 */
sealed class InterceptResult {
    /**
     * 继续原始导航
     */
    object Continue : InterceptResult()

    /**
     * 取消导航
     * @param reason 取消原因（可选）
     */
    data class Cancel(val reason: String? = null) : InterceptResult()

    /**
     * 重定向到新的 NavKey
     * @param newNavKey 新的导航目标
     */
    data class Redirect(val newNavKey: NavKey) : InterceptResult()
}
