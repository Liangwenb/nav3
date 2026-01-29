package com.liangwenb.nav

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.collections.get
import kotlin.coroutines.resume


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
        if (isSerializable(navKey)) {
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

    lateinit var mainClass: Class<out Activity>
    private val routerMap = java.util.WeakHashMap<Context, NavBackStack<NavKey>>()
    private val interceptors = mutableListOf<NavInterceptor>()

    fun attach(context: Context, navBackStack: NavBackStack<NavKey>) {
        if (routerMap[context] != navBackStack) {
            routerMap[context] = navBackStack
        }
    }

    fun detach(context: Context) {
        routerMap.remove(context)
    }

    /**
     * 添加导航拦截器
     */
    fun addInterceptor(interceptor: NavInterceptor) {
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor)
        }
    }

    /**
     * 移除导航拦截器
     */
    fun removeInterceptor(interceptor: NavInterceptor) {
        interceptors.remove(interceptor)
    }

    /**
     * 清空所有拦截器
     */
    fun clearInterceptors() {
        interceptors.clear()
    }

    /**
     * 处理拦截器链
     * @return 处理后的 NavKey，如果返回 null 则取消导航
     */
    private fun processInterceptors(navKey: NavKey, action: NavAction): NavKey? {
        var currentKey: NavKey? = navKey
        
        for (interceptor in interceptors) {
            currentKey?.let {
                when (val result = interceptor.intercept(it, action)) {
                    is InterceptResult.Continue -> {
                        // 继续使用当前的 navKey
                    }
                    is InterceptResult.Cancel -> {
                        Log.d("NavBackStack", "导航被拦截取消: ${result.reason ?: "未提供原因"}")
                        return null
                    }
                    is InterceptResult.Redirect -> {
                        Log.d("NavBackStack", "导航被重定向: ${it::class.simpleName} -> ${result.newNavKey::class.simpleName}")
                        currentKey = result.newNavKey
                    }
                }
            } ?: return null
        }
        
        return currentKey
    }

    fun go(navKey: NavKey, context: Context? = null) {
        val processedKey = processInterceptors(navKey, NavAction.GO) ?: return
        
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        backStack?.let {
            if (it.lastOrNull() == processedKey) {
                Log.e("NavBackStack", "已经存在导航Key 界面 不能重复打开")
            } else {
                it.go(processedKey)
            }

        }
    }

    /**
     * 打开新界面并关闭其他的所有界面
     */
    fun goOffAll(navKey: NavKey, context: Context? = null) {
        val processedKey = processInterceptors(navKey, NavAction.GO_OFF_ALL) ?: return
        
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        go(processedKey, context)
        backStack?.removeIf { key -> key != processedKey }

    }

    fun <T> goResult(
        navKey: ResultNavKey<T>,
        context: Context? = null,
        onResult: (T?) -> Unit = {}
    ) {
        val processedKey = processInterceptors(navKey, NavAction.GO_RESULT) ?: return
        
        // 如果被重定向到非 ResultNavKey，则忽略回调
        if (processedKey is ResultNavKey<*>) {
            @Suppress("UNCHECKED_CAST")
            (processedKey as ResultNavKey<T>).resultCallback = onResult
        } else {
            Log.w("NavBackStack", "导航被重定向到非 ResultNavKey，回调将被忽略")
        }
        
        go(processedKey, context)
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun <T> goResult(
        navKey: ResultNavKey<T>,
        context: Context? = null,
    ): T? {
        val processedKey = processInterceptors(navKey, NavAction.GO_RESULT) ?: return null
        
        // 如果被重定向到非 ResultNavKey，返回 null
        if (processedKey !is ResultNavKey<*>) {
            Log.w("NavBackStack", "导航被重定向到非 ResultNavKey，返回 null")
            return null
        }
        
        val cancellableCoroutine =
            suspendCancellableCoroutine { suspendCancellableCoroutine ->
                @Suppress("UNCHECKED_CAST")
                (processedKey as ResultNavKey<T>).resultCallback = {
                    val token = suspendCancellableCoroutine.tryResume(it)
                    if (token != null) {
                        suspendCancellableCoroutine.completeResume(token)
                    }
                }
                go(processedKey, context)
            }
        return cancellableCoroutine
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

    fun getBackStack(context: Context? = null): NavBackStack<NavKey>? = runCatching {
        return@runCatching routerMap[context] ?: routerMap.values.lastOrNull()
    }.getOrNull()

    fun getTopKey(context: Context? = null): NavKey? = runCatching {
        val backStack = routerMap[context] ?: routerMap.values.lastOrNull()
        backStack?.lastOrNull()
    }.getOrNull()

}

