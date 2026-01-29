# NavBackStack 导航拦截器

## 概述

导航拦截器允许你在导航跳转时进行拦截、重定向或阻止跳转。这对于实现登录验证、权限检查、条件路由等功能非常有用。

## 核心组件

### 1. NavInterceptor 接口

```kotlin
interface NavInterceptor {
    fun intercept(navKey: NavKey, action: NavAction): InterceptResult
}
```

### 2. NavAction 枚举

表示导航动作类型：
- `GO` - 普通跳转
- `GO_OFF_ALL` - 跳转并清空其他界面
- `GO_RESULT` - 带结果的跳转

### 3. InterceptResult 密封类

拦截结果有三种类型：

- **Continue** - 继续原始导航
- **Cancel(reason: String?)** - 取消导航，可选提供取消原因
- **Redirect(newNavKey: NavKey)** - 重定向到新的 NavKey

## 使用方法

### 添加拦截器

```kotlin
// 创建拦截器实例
val loginInterceptor = LoginInterceptor { UserManager.isLoggedIn() }

// 添加到导航系统
NavBackStackUtils.addInterceptor(loginInterceptor)
```

### 移除拦截器

```kotlin
NavBackStackUtils.removeInterceptor(loginInterceptor)
```

### 清空所有拦截器

```kotlin
NavBackStackUtils.clearInterceptors()
```

## 实现示例

### 1. 登录拦截器

```kotlin
class LoginInterceptor(private val isLoggedIn: () -> Boolean) : NavInterceptor {
    override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
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
```

### 2. 权限拦截器

```kotlin
class PermissionInterceptor(private val hasPermission: (NavKey) -> Boolean) : NavInterceptor {
    override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
        if (!hasPermission(navKey)) {
            return InterceptResult.Cancel("没有权限访问该页面")
        }
        return InterceptResult.Continue
    }
}
```

### 3. 防重复点击拦截器

```kotlin
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
```

### 4. 日志拦截器

```kotlin
class LoggingInterceptor : NavInterceptor {
    override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
        Log.d("Navigation", "导航到: ${navKey::class.simpleName}, 动作: $action")
        return InterceptResult.Continue
    }
}
```

## 完整使用示例

```kotlin
// 在 Application 或合适的初始化位置

// 1. 添加登录拦截器
NavBackStackUtils.addInterceptor(
    LoginInterceptor { UserManager.isLoggedIn() }
)

// 2. 添加权限拦截器
NavBackStackUtils.addInterceptor(
    PermissionInterceptor { navKey ->
        when (navKey) {
            is AdminPageNavKey -> UserManager.isAdmin()
            is VipPageNavKey -> UserManager.isVip()
            else -> true
        }
    }
)

// 3. 添加防重复点击拦截器
NavBackStackUtils.addInterceptor(
    AntiDuplicateInterceptor(intervalMs = 500)
)

// 4. 添加日志拦截器（用于调试）
if (BuildConfig.DEBUG) {
    NavBackStackUtils.addInterceptor(LoggingInterceptor())
}

// 正常使用导航，拦截器会自动生效
NavBackStackUtils.go(ProfilePageNavKey)
```

## 拦截器执行顺序

拦截器按照添加的顺序依次执行，形成拦截器链：

```
原始 NavKey 
  ↓
拦截器 1 → Continue/Cancel/Redirect
  ↓
拦截器 2 → Continue/Cancel/Redirect
  ↓
拦截器 3 → Continue/Cancel/Redirect
  ↓
最终导航或取消
```

- 如果任何一个拦截器返回 `Cancel`，导航将被取消
- 如果拦截器返回 `Redirect`，后续拦截器将处理重定向后的 NavKey
- 所有拦截器都返回 `Continue` 时，使用最终的 NavKey 进行导航

## 注意事项

1. **拦截器顺序很重要** - 先添加的拦截器先执行
2. **避免循环重定向** - 确保重定向逻辑不会造成无限循环
3. **性能考虑** - 拦截器会在每次导航时执行，避免在拦截器中执行耗时操作
4. **线程安全** - 如果在多线程环境中使用，注意拦截器的线程安全性
5. **ResultNavKey 重定向** - 如果将 `ResultNavKey` 重定向到普通 `NavKey`，回调将被忽略

## 高级用法

### 条件拦截器

```kotlin
class ConditionalInterceptor(
    private val condition: () -> Boolean,
    private val interceptor: NavInterceptor
) : NavInterceptor {
    override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
        return if (condition()) {
            interceptor.intercept(navKey, action)
        } else {
            InterceptResult.Continue
        }
    }
}

// 使用
NavBackStackUtils.addInterceptor(
    ConditionalInterceptor(
        condition = { !BuildConfig.DEBUG },
        interceptor = LoginInterceptor { UserManager.isLoggedIn() }
    )
)
```

### 组合拦截器

```kotlin
class CompositeInterceptor(
    private val interceptors: List<NavInterceptor>
) : NavInterceptor {
    override fun intercept(navKey: NavKey, action: NavAction): InterceptResult {
        var currentKey = navKey
        
        for (interceptor in interceptors) {
            when (val result = interceptor.intercept(currentKey, action)) {
                is InterceptResult.Continue -> continue
                is InterceptResult.Cancel -> return result
                is InterceptResult.Redirect -> currentKey = result.newNavKey
            }
        }
        
        return if (currentKey == navKey) {
            InterceptResult.Continue
        } else {
            InterceptResult.Redirect(currentKey)
        }
    }
}
```

## API 文档

### NavBackStackUtils 新增方法

```kotlin
// 添加拦截器
fun addInterceptor(interceptor: NavInterceptor)

// 移除拦截器
fun removeInterceptor(interceptor: NavInterceptor)

// 清空所有拦截器
fun clearInterceptors()
```

所有现有的导航方法（`go`, `goOffAll`, `goResult`）都会自动应用拦截器。
