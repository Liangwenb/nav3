package com.liangwenb.nav.route

import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
/**
 * 将 Compose 页面注册到 App KSP 生成的 Navigation 3 全局 entry provider。
 *
 * 注解保留在编译产物中，使 App 可以零配置聚合普通依赖模块的路由。
 *
 * @property key 页面对应的类型安全 [NavKey]
 * @property type 页面展示类型，默认使用普通 Screen
 * @property route 可选 canonical 字符串路由；为空时只生成 Key 路由入口
 */
annotation class Route(
    val key: KClass<out NavKey>,
    val type: NavType = NavType.Screen,
    val route: String = "",
)

enum class NavType { Screen, Dialog, BottomDialog }
