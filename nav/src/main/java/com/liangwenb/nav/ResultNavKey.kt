package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey

// 所有带回调的 NavKey 都可以继承这个
abstract class ResultNavKey<T> : NavKey {
    // 回调函数，可返回任意类型 T
    var resultCallback: ((T?) -> Unit)? = null

    // 提供一个方法触发回调
    fun sendResult(result: T?) {
        resultCallback?.invoke(result)
    }
}
