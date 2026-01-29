package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 简单的测试用例，演示拦截器的使用
 */
object InterceptorTest {

    // 定义一些测试用的 NavKey
    @Serializable
    object HomeNavKey : NavKey

    @Serializable
    object LoginNavKey : NavKey

    @Serializable
    object ProfileNavKey : NavKey

    @Serializable
    object AdminNavKey : NavKey

    // 模拟用户状态
    object MockUserManager {
        var isLoggedIn = false
        var isAdmin = false
    }

    /**
     * 测试场景 1: 登录拦截
     */
    fun testLoginInterceptor() {
        println("=== 测试登录拦截器 ===")

        val loginInterceptor = object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                // 如果已经是登录页面，直接放行
                if (navKey is LoginNavKey) {
                    return InterceptResult.Continue
                }

                // 如果用户未登录，重定向到登录页面
                if (!MockUserManager.isLoggedIn) {
                    println("用户未登录，重定向到登录页面")
                    return InterceptResult.Redirect(LoginNavKey)
                }

                return InterceptResult.Continue
            }
        }

        NavBackStackUtils.addInterceptor(loginInterceptor)

        // 测试：未登录时访问个人中心
        println("\n测试 1: 未登录访问个人中心")
        MockUserManager.isLoggedIn = false
        // NavBackStackUtils.go(ProfileNavKey)  // 将被重定向到 LoginNavKey

        // 测试：已登录时访问个人中心
        println("\n测试 2: 已登录访问个人中心")
        MockUserManager.isLoggedIn = true
        // NavBackStackUtils.go(ProfileNavKey)  // 正常访问

        NavBackStackUtils.removeInterceptor(loginInterceptor)
    }

    /**
     * 测试场景 2: 权限拦截
     */
    fun testPermissionInterceptor() {
        println("\n=== 测试权限拦截器 ===")

        val permissionInterceptor = object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                // 检查管理员页面权限
                if (navKey is AdminNavKey && !MockUserManager.isAdmin) {
                    println("没有管理员权限，取消导航")
                    return InterceptResult.Cancel("需要管理员权限")
                }
                return InterceptResult.Continue
            }
        }

        NavBackStackUtils.addInterceptor(permissionInterceptor)

        // 测试：非管理员访问管理页面
        println("\n测试 1: 非管理员访问管理页面")
        MockUserManager.isAdmin = false
        // NavBackStackUtils.go(AdminNavKey)  // 将被取消

        // 测试：管理员访问管理页面
        println("\n测试 2: 管理员访问管理页面")
        MockUserManager.isAdmin = true
        // NavBackStackUtils.go(AdminNavKey)  // 正常访问

        NavBackStackUtils.removeInterceptor(permissionInterceptor)
    }

    /**
     * 测试场景 3: 多个拦截器链
     */
    fun testInterceptorChain() {
        println("\n=== 测试拦截器链 ===")

        // 拦截器 1: 日志
        val loggingInterceptor = object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                println("日志: 导航到 ${navKey::class.simpleName}, 动作: $action")
                return InterceptResult.Continue
            }
        }

        // 拦截器 2: 登录检查
        val loginInterceptor = object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                if (navKey !is LoginNavKey && !MockUserManager.isLoggedIn) {
                    println("登录检查: 重定向到登录页面")
                    return InterceptResult.Redirect(LoginNavKey)
                }
                return InterceptResult.Continue
            }
        }

        // 拦截器 3: 权限检查
        val permissionInterceptor = object : NavInterceptor {
            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                if (navKey is AdminNavKey && !MockUserManager.isAdmin) {
                    println("权限检查: 取消导航")
                    return InterceptResult.Cancel("需要管理员权限")
                }
                return InterceptResult.Continue
            }
        }

        // 按顺序添加拦截器
        NavBackStackUtils.addInterceptor(loggingInterceptor)
        NavBackStackUtils.addInterceptor(loginInterceptor)
        NavBackStackUtils.addInterceptor(permissionInterceptor)

        // 测试：未登录访问管理页面
        println("\n测试: 未登录访问管理页面")
        MockUserManager.isLoggedIn = false
        MockUserManager.isAdmin = false
        // NavBackStackUtils.go(AdminNavKey)
        // 预期: 日志 -> 重定向到登录页 -> 继续

        // 清空拦截器
        NavBackStackUtils.clearInterceptors()
    }

    /**
     * 测试场景 4: 防重复点击
     */
    fun testAntiDuplicateInterceptor() {
        println("\n=== 测试防重复点击拦截器 ===")

        val antiDuplicateInterceptor = object : NavInterceptor {
            private var lastNavTime = 0L
            private var lastNavKey: NavKey? = null
            private val intervalMs = 500L

            override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
                val currentTime = System.currentTimeMillis()

                if (navKey == lastNavKey && currentTime - lastNavTime < intervalMs) {
                    println("防重复: 操作过于频繁，取消导航")
                    return InterceptResult.Cancel("操作过于频繁")
                }

                lastNavTime = currentTime
                lastNavKey = navKey
                return InterceptResult.Continue
            }
        }

        NavBackStackUtils.addInterceptor(antiDuplicateInterceptor)

        // 测试：快速连续点击
        println("\n测试: 快速连续点击")
        // NavBackStackUtils.go(ProfileNavKey)  // 第一次：成功
        // Thread.sleep(100)
        // NavBackStackUtils.go(ProfileNavKey)  // 第二次：被取消
        // Thread.sleep(500)
        // NavBackStackUtils.go(ProfileNavKey)  // 第三次：成功

        NavBackStackUtils.removeInterceptor(antiDuplicateInterceptor)
    }
}

/**
 * 使用说明：
 * 
 * 在你的代码中调用这些测试方法来验证拦截器功能：
 * 
 * InterceptorTest.testLoginInterceptor()
 * InterceptorTest.testPermissionInterceptor()
 * InterceptorTest.testInterceptorChain()
 * InterceptorTest.testAntiDuplicateInterceptor()
 */
