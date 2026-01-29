package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey

/**
 * 导航拦截器使用示例
 */
object NavInterceptorExamples {

    /**
     * 示例 1: 登录拦截器
     * 如果用户未登录，重定向到登录页面
     */
    class LoginInterceptor(private val isLoggedIn: () -> Boolean) : NavInterceptor {
        override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
            // 假设有一个 LoginNavKey 表示登录页面
            // 如果已经是登录页面，直接放行
            if (navKey is LoginNavKey) {
                return InterceptResult.Continue
            }

            // 如果用户未登录，重定向到登录页面
            if (!isLoggedIn()) {
                return InterceptResult.Redirect(LoginNavKey)
            }

            return InterceptResult.Continue
        }
    }

    /**
     * 示例 2: 权限拦截器
     * 检查用户是否有权限访问某些页面
     */
    class PermissionInterceptor(private val hasPermission: (NavKey) -> Boolean) : NavInterceptor {
        override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
            if (!hasPermission(navKey)) {
                return InterceptResult.Cancel("没有权限访问该页面")
            }
            return InterceptResult.Continue
        }
    }

    /**
     * 示例 3: 条件重定向拦截器
     * 根据特定条件重定向到不同页面
     */
    class ConditionalRedirectInterceptor : NavInterceptor {
        override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
            // 示例：如果是某个特定页面且满足条件，重定向到另一个页面
            if (navKey is SomeSpecificNavKey && shouldRedirect()) {
                return InterceptResult.Redirect(AlternativeNavKey)
            }
            return InterceptResult.Continue
        }

        private fun shouldRedirect(): Boolean {
            // 实现你的重定向逻辑
            return false
        }
    }

    /**
     * 示例 4: 防重复点击拦截器
     * 防止短时间内重复导航到同一页面
     */
    class AntiDuplicateInterceptor(private val intervalMs: Long = 500) : NavInterceptor {
        private var lastNavTime = 0L
        private var lastNavKey: NavKey? = null

        override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
            val currentTime = System.currentTimeMillis()
            
            if (navKey == lastNavKey && currentTime - lastNavTime < intervalMs) {
                return InterceptResult.Cancel("操作过于频繁")
            }

            lastNavTime = currentTime
            lastNavKey = navKey
            return InterceptResult.Continue
        }
    }

    /**
     * 示例 5: 日志拦截器
     * 记录所有导航事件
     */
    class LoggingInterceptor : NavInterceptor {
        override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
            android.util.Log.d("Navigation", "导航到: ${navKey::class.simpleName}, 动作: $action")
            return InterceptResult.Continue
        }
    }
}

// 示例 NavKey 定义（需要根据实际项目定义）
object LoginNavKey : NavKey
object SomeSpecificNavKey : NavKey
object AlternativeNavKey : NavKey

/**
 * 使用示例：
 * 
 * // 1. 添加拦截器
 * NavBackStackUtils.addInterceptor(LoginInterceptor { UserManager.isLoggedIn() })
 * NavBackStackUtils.addInterceptor(LoggingInterceptor())
 * 
 * // 2. 正常使用导航
 * NavBackStackUtils.go(SomePageNavKey)
 * 
 * // 3. 移除拦截器
 * NavBackStackUtils.removeInterceptor(loginInterceptor)
 * 
 * // 4. 清空所有拦截器
 * NavBackStackUtils.clearInterceptors()
 */
