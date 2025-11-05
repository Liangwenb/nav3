package com.liangwenb.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey


open class KeyViewModel<T : NavKey>(val key: T) : ViewModel() {




}


@Composable
public inline fun <reified K : NavKey, reified VM : KeyViewModel<K>> keyViewModel(key: NavKey): VM {
    return viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // 获取 VM 的 Class 对象
            val viewModelClass = VM::class.java

            // 获取构造函数：这里会尝试寻找构造函数匹配 NavKey 类型
            val constructor = viewModelClass.getConstructor(K::class.java)

            // 使用反射创建 ViewModel 实例，传入 key 参数
            return constructor.newInstance(key) as T
        }
    })
}