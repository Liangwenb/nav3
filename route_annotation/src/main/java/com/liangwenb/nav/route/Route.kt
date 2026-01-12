package com.liangwenb.nav.route

import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Route(
    val key: KClass<out NavKey>,
    val type: NavType = NavType.Screen,

)

enum class NavType { Screen, Dialog, BottomDialog }
