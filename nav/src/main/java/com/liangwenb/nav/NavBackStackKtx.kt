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
    private val routerMap = java.util.WeakHashMap<Context, RouterBinding>()
    private val interceptors = mutableListOf<NavInterceptor>()

    private data class RouterBinding(
        val backStack: NavBackStack<NavKey>,
        val stringRouteResolver: StringRouteResolver? = null,
    )

    private sealed interface InterceptorOutcome {
        data class Continue(val key: NavKey) : InterceptorOutcome

        data class Cancel(val reason: String?) : InterceptorOutcome
    }

    private sealed interface DispatchOutcome {
        data class Navigated(val key: NavKey, val binding: RouterBinding) : DispatchOutcome

        data object Duplicate : DispatchOutcome

        data class Intercepted(val reason: String?) : DispatchOutcome

        data object NoAttachedBackStack : DispatchOutcome

        data object InvalidKey : DispatchOutcome
    }

    /** 绑定仅支持 Key 模式的回退栈；若此前绑定过字符串 resolver，会将其移除。 */
    fun attach(context: Context, navBackStack: NavBackStack<NavKey>) {
        val current = routerMap[context]
        if (current?.backStack != navBackStack || current.stringRouteResolver != null) {
            routerMap[context] = RouterBinding(navBackStack)
        }
    }

    /**
     * 绑定回退栈和 KSP 生成的字符串路由解析器。
     *
     * 原有二参数 [attach] 保持不变；只有需要调用 [go] 的 String 重载时才使用此方法。
     */
    fun attach(
        context: Context,
        navBackStack: NavBackStack<NavKey>,
        stringRouteResolver: StringRouteResolver,
    ) {
        val current = routerMap[context]
        if (current?.backStack != navBackStack || current.stringRouteResolver != stringRouteResolver) {
            routerMap[context] = RouterBinding(navBackStack, stringRouteResolver)
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
     * @return 处理后的 Key 或携带原因的取消结果
     */
    private fun processInterceptors(navKey: NavKey, action: NavAction): InterceptorOutcome {
        var currentKey = navKey

        for (interceptor in interceptors) {
            when (val result = interceptor.intercept(currentKey, action)) {
                InterceptResult.Continue -> Unit
                is InterceptResult.Cancel -> {
                    Log.d("NavBackStack", "导航被拦截取消: ${result.reason ?: "未提供原因"}")
                    return InterceptorOutcome.Cancel(result.reason)
                }
                is InterceptResult.Redirect -> {
                    Log.d(
                        "NavBackStack",
                        "导航被重定向: ${currentKey::class.simpleName} -> ${result.newNavKey::class.simpleName}",
                    )
                    currentKey = result.newNavKey
                }
            }
        }

        return InterceptorOutcome.Continue(currentKey)
    }

    fun go(navKey: NavKey, context: Context? = null) {
        when (dispatch(navKey, NavAction.GO, findBinding(context))) {
            DispatchOutcome.Duplicate -> Log.e("NavBackStack", "已经存在导航Key 界面 不能重复打开")
            else -> Unit
        }
    }

    /**
     * 将相对字符串路由解析成 [NavKey] 后进入与 Key 模式相同的导航管线。
     *
     * 多个 Context 同时绑定且未传 [context] 时返回 [StringRouteNavigationResult.AmbiguousContext]。
     */
    fun go(route: String, context: Context? = null): StringRouteNavigationResult {
        val bindingResult = findStringRouteBinding(context)
        if (bindingResult is StringRouteBindingResult.Failure) return bindingResult.result
        val binding = (bindingResult as StringRouteBindingResult.Success).binding
        return when (val resolved = binding.stringRouteResolver!!.resolve(route)) {
            is StringRouteResolveResult.Matched -> dispatch(resolved.key, NavAction.GO, binding).toPublicResult(route)
            is StringRouteResolveResult.NotFound -> StringRouteNavigationResult.NotFound(route)
            is StringRouteResolveResult.Invalid -> StringRouteNavigationResult.Invalid(route, resolved.reason)
        }
    }

    /**
     * 打开新界面并关闭其他的所有界面
     */
    fun goOffAll(navKey: NavKey, context: Context? = null) {
        val processedKey = when (val outcome = processInterceptors(navKey, NavAction.GO_OFF_ALL)) {
            is InterceptorOutcome.Continue -> outcome.key
            is InterceptorOutcome.Cancel -> return
        }
        val backStack = findBinding(context)?.backStack
        go(processedKey, context)
        backStack?.removeIf { key -> key != processedKey }
    }

    /** 字符串路由版本的 [goOffAll]。 */
    fun goOffAll(route: String, context: Context? = null): StringRouteNavigationResult {
        val bindingResult = findStringRouteBinding(context)
        if (bindingResult is StringRouteBindingResult.Failure) return bindingResult.result
        val binding = (bindingResult as StringRouteBindingResult.Success).binding
        return when (val resolved = binding.stringRouteResolver!!.resolve(route)) {
            is StringRouteResolveResult.Matched -> {
                val outcome = dispatch(resolved.key, NavAction.GO_OFF_ALL, binding)
                if (outcome is DispatchOutcome.Navigated) {
                    binding.backStack.removeIf { key -> key != outcome.key }
                }
                outcome.toPublicResult(route)
            }
            is StringRouteResolveResult.NotFound -> StringRouteNavigationResult.NotFound(route)
            is StringRouteResolveResult.Invalid -> StringRouteNavigationResult.Invalid(route, resolved.reason)
        }

    }

    fun <T> goResult(
        navKey: ResultNavKey<T>,
        context: Context? = null,
        onResult: (T?) -> Unit = {}
    ) {
        val processedKey = when (val outcome = processInterceptors(navKey, NavAction.GO_RESULT)) {
            is InterceptorOutcome.Continue -> outcome.key
            is InterceptorOutcome.Cancel -> return
        }
        
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
        val processedKey = when (val outcome = processInterceptors(navKey, NavAction.GO_RESULT)) {
            is InterceptorOutcome.Continue -> outcome.key
            is InterceptorOutcome.Cancel -> return null
        }
        
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
        findBinding(context)?.backStack?.back()
    }

    fun closeActivity(context: Context) {
        if (context is Activity) {
            context.finish()
        }
    }

    fun finish(navKey: NavKey, context: Context? = null, isRemoveTop: Boolean = false) {
        val backStack = findBinding(context)?.backStack
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
        val backStack = findBinding(context)?.backStack
        navKey?.sendResult(result)
        backStack?.finish(navKey)
    }

    fun getBackStack(context: Context? = null): NavBackStack<NavKey>? = runCatching {
        return@runCatching findBinding(context)?.backStack
    }.getOrNull()

    fun getTopKey(context: Context? = null): NavKey? = runCatching {
        val backStack = findBinding(context)?.backStack
        backStack?.lastOrNull()
    }.getOrNull()

    /**
     * 将 Key 格式化为其 canonical 字符串路由；未声明 route 或无法唯一定位 Context 时返回 null。
     */
    fun routeOf(navKey: NavKey, context: Context? = null): String? =
        when (val result = findStringRouteBinding(context)) {
            is StringRouteBindingResult.Success -> result.binding.stringRouteResolver?.format(navKey)
            is StringRouteBindingResult.Failure -> null
        }

    private fun dispatch(
        navKey: NavKey,
        action: NavAction,
        binding: RouterBinding?,
    ): DispatchOutcome {
        binding ?: return DispatchOutcome.NoAttachedBackStack
        val key = when (val outcome = processInterceptors(navKey, action)) {
            is InterceptorOutcome.Continue -> outcome.key
            is InterceptorOutcome.Cancel -> return DispatchOutcome.Intercepted(outcome.reason)
        }
        if (binding.backStack.lastOrNull() == key) return DispatchOutcome.Duplicate
        return runCatching {
            if (!isSerializable(key)) return@runCatching DispatchOutcome.InvalidKey
            binding.backStack.add(key)
            DispatchOutcome.Navigated(key, binding)
        }.getOrElse {
            it.printStackTrace()
            DispatchOutcome.InvalidKey
        }
    }

    private fun DispatchOutcome.toPublicResult(route: String): StringRouteNavigationResult = when (this) {
        is DispatchOutcome.Navigated -> StringRouteNavigationResult.Navigated
        DispatchOutcome.Duplicate -> StringRouteNavigationResult.Duplicate
        is DispatchOutcome.Intercepted -> StringRouteNavigationResult.Intercepted(reason)
        DispatchOutcome.NoAttachedBackStack -> StringRouteNavigationResult.NoAttachedBackStack
        DispatchOutcome.InvalidKey -> StringRouteNavigationResult.Invalid(route, "解析结果不是可序列化 NavKey")
    }

    private fun findBinding(context: Context?): RouterBinding? =
        routerMap[context] ?: routerMap.values.lastOrNull()

    private sealed interface StringRouteBindingResult {
        data class Success(val binding: RouterBinding) : StringRouteBindingResult

        data class Failure(val result: StringRouteNavigationResult) : StringRouteBindingResult
    }

    private fun findStringRouteBinding(context: Context?): StringRouteBindingResult {
        val binding = if (context != null) {
            routerMap[context] ?: return StringRouteBindingResult.Failure(
                StringRouteNavigationResult.NoAttachedBackStack,
            )
        } else {
            when (routerMap.size) {
                0 -> return StringRouteBindingResult.Failure(StringRouteNavigationResult.NoAttachedBackStack)
                1 -> routerMap.values.first()
                else -> return StringRouteBindingResult.Failure(StringRouteNavigationResult.AmbiguousContext)
            }
        }
        if (binding.stringRouteResolver == null) {
            return StringRouteBindingResult.Failure(StringRouteNavigationResult.NoRouteResolver)
        }
        return StringRouteBindingResult.Success(binding)
    }

}
