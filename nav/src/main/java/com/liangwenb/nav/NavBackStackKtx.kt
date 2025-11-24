package com.liangwenb.nav

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.collections.get


private fun NavBackStack<NavKey>.back() {
    if (size == 1) {
        Log.d("NavBackStack", "还剩最后一个界面不能在移除了")
    } else {
        removeLastOrNull()
    }
}

private fun NavBackStack<NavKey>.finish(key: NavKey?) {
    remove(key)
}

fun <T : NavKey> NavBackStack<T>.go(navKey: T) {
    runCatching {
        if(isSerializable(navKey)){
            add(navKey)
        }
    }.onFailure { it.printStackTrace() }
}
@OptIn(InternalSerializationApi::class)
fun isSerializable(navKey: Any): Boolean {
    return try {
        val kClass = navKey::class
        // 尝试获取序列化器
        kClass.serializer()
        true
    } catch (e: SerializationException) {
        e.printStackTrace()
        false
    }
}
object NavBackStackUtils {

    lateinit var mianClass: Class<out Activity>
    private val routerMap = LinkedHashMap<Context, NavBackStack<NavKey>>()

    fun attach(context: Context, navBackStack: NavBackStack<NavKey>) {
        if (routerMap[context] != navBackStack) {
            routerMap[context] = navBackStack
        }
    }

    fun detach(context: Context) {
        routerMap.remove(context)
    }

    fun go(navKey: NavKey, context: Context? = null) {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        backStack?.let {
            if (it.lastOrNull() == navKey) {
                Log.e("NavBackStack", "已经存在导航Key 界面 不能重复打开")
            } else {
                it.go(navKey)
            }

        }
    }

    /**
     * 打开新界面并关闭其他的所有界面
     */
    fun goOffAll(navKey: NavKey, context: Context? = null) {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        go(navKey, context)
        backStack?.removeIf { key -> key != navKey }

    }

    fun <T> goResult(
        navKey: ResultNavKey<T>,
        context: Context? = null,
        onResult: (T?) -> Unit = {}
    ) {
        navKey.resultCallback = onResult
        go(navKey, context)
    }

    fun back(context: Context? = null) {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        backStack?.back()
    }

    fun closeActivity(context: Context) {
        if (context is Activity) {
            context.finish()
        }
    }

    fun finish(navKey: NavKey, context: Context? = null, isRemoveTop: Boolean = false) {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        if (isRemoveTop && backStack?.contains(navKey) == true) {
            // 保留 navKey 及其之后的部分（模拟 “清空栈顶”）
            val index = backStack.indexOf(navKey)
            if (index >= 0 && index < backStack.size - 1) {
                backStack.subList(index + 1, backStack.size).clear()
            }
        }
        backStack?.finish(navKey)
    }

    fun <T> finishResult(navKey: ResultNavKey<T>?, result: T? = null, context: Context? = null) {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        navKey?.sendResult(result)
        backStack?.finish(navKey)
    }

    fun <T : NavKey> getTopKey(context: Context? = null): T? = runCatching {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        backStack?.lastOrNull() as T?
    }.getOrNull()

}

