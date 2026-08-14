# Nav3 注解路由

基于 Jetpack Navigation 3 和 Jetpack Compose 的轻量封装。通过 `@Route` 生成 `entryProvider`，
同时支持类型安全 `NavKey` 路由、可选字符串路由、普通弹窗与底部弹窗。

[![JitPack](https://jitpack.io/v/Liangwenb/nav3.svg)](https://jitpack.io/#Liangwenb/nav3)

## 接入

```kotlin
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

dependencies {
    implementation("com.github.Liangwenb.nav3:nav:<version>")
    ksp("com.github.Liangwenb.nav3:route_processor:<version>")
}
```

KSP 只需在 App 模块声明。普通 Android Library Feature 只依赖 `nav`，直接在
`public` 顶层 Compose 函数上使用 `@Route` 即可；无需应用 KSP、声明处理器、配置模块名或包名。
App KSP 会自动扫描其编译 classpath，并生成唯一的全局路由表。

源码当前使用 Java 17、minSdk 24、compileSdk/targetSdk 37。核心工具链为 Gradle 9.7.0、
AGP 9.3.1、Kotlin 2.4.10、KSP 2.3.11、Compose BOM 2026.08.00 和 Navigation 3 1.1.6；
完整版本集中维护在 `gradle/libs.versions.toml`。

## Key 路由快速开始

Key 必须实现 `NavKey` 并使用 `@Serializable`。原有 `@Route(Key::class, NavType)` 写法保持有效；
不填写 `route` 时只生成 Key 路由。

```kotlin
@Serializable
data object Home : NavKey

@Serializable
data class Profile(val id: Long) : NavKey

@Route(Home::class)
@Composable
fun HomePage() = Unit

@Route(Profile::class)
@Composable
fun ProfilePage(key: Profile) {
    Text("id=${key.id}")
}
```

## 多模块零配置

Feature 模块不需要 KSP：

```kotlin
// feature-profile/build.gradle.kts
dependencies {
    implementation("com.github.Liangwenb.nav3:nav:<version>")
}
```

Feature 内的路由写法与 App 完全一致：

```kotlin
@Serializable
data class FeatureProfile(val id: Long) : NavKey

@Route(FeatureProfile::class, route = "feature/profile/{id}")
@Composable
fun FeatureProfilePage(key: FeatureProfile) {
    Text("profile=${key.id}")
}
```

App 只需直接依赖 Feature。处理器会从 Feature 编译产物中发现 `@Route`，并将其合并到
`com.liangwenb.nav.generated.appInitEntryProvider()` 和
`com.liangwenb.nav.generated.appStringRouteResolver()`。跨模块重复 Key、重复字符串路由和
歧义路由会在 App KSP 阶段统一报错。

约束：

- 要求 KSP2；不支持 KSP1 或自定义 Resolver 实现。
- Feature 必须位于 App 编译 classpath，通常由 App 直接 `implementation(project(...))`。
- `@Route` 页面必须是 `public` 顶层函数，以便 App 生成代码跨模块调用。
- Android Dynamic Feature 依赖 App，不在 App 编译 classpath 中，不属于此自动聚合范围。

从旧版多模块方案迁移时，删除各 Feature 的 KSP 插件、`ksp(route_processor)` 和
`MODULE_NAME` 参数，只在 App 保留 KSP 与处理器。同时将生成函数的 import 更新为
`com.liangwenb.nav.generated.*`。

Activity 中安装生成的 entry provider，并在销毁时解绑 Context：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = rememberNavBackStack(Home)
            val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }

            DisposableEffect(backStack) {
                NavBackStackUtils.attach(this@MainActivity, backStack)
                onDispose { NavBackStackUtils.detach(this@MainActivity) }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategies = listOf(dialogStrategy),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    appInitEntryProvider()
                },
            )
        }
    }

}
```

常用 Key API：

```kotlin
NavBackStackUtils.go(Profile(42))
NavBackStackUtils.goOffAll(Home)
NavBackStackUtils.back()
NavBackStackUtils.finish(Profile(42))
```

需要页面结果时让 Key 继承 `ResultNavKey<T>`，打开时使用 `goResult`，关闭时使用
`finishResult`。带 Key 的 ViewModel 可继承 `KeyViewModel<K>`；KSP 会按页面参数自动注入。

## 弹窗路由

使用 `NavType.Dialog` 注册普通弹窗，使用 `NavType.BottomDialog` 注册底部弹窗。KSP 会自动生成入口，
无需手动调用 `entryProvider` 的 `dialog` 或 `bottomDialog` 方法。

```kotlin
@Serializable
data object Notice : NavKey

@Serializable
data object Sheet : NavKey

@Route(Notice::class, NavType.Dialog, route = "dialog/notice")
@Composable
fun NoticeDialog() {
    Column(Modifier.padding(24.dp)) {
        Text("这是普通弹窗")
        Button(onClick = NavBackStackUtils::back) {
            Text("关闭")
        }
    }
}

@Route(Sheet::class, NavType.BottomDialog, route = "dialog/sheet")
@Composable
fun SheetDialog() {
    Column(Modifier.padding(24.dp)) {
        Text("这是底部弹窗")
        Button(onClick = NavBackStackUtils::back) {
            Text("关闭")
        }
    }
}
```

两种容器都会展示 Material 3 遮罩：点击遮罩或按系统返回键会关闭当前弹窗；内容区域的点击不会
关闭弹窗。需要从业务逻辑显式关闭时，调用 `NavBackStackUtils.back()`。

## Key 与字符串双路由

应用内跳转继续优先使用类型安全的 `NavKey`。通知、已验证的 Deep Link、服务端菜单等外部边界，
可以使用字符串路由；字符串会先由 KSP 生成的 resolver 转成同一个 `NavKey`，再进入现有拦截器和
`NavBackStack`，不会建立第二套导航栈。

```kotlin
@Serializable
data class Profile(
    val id: Long,
    val tab: String? = null,
) : NavKey

@Route(
    key = Profile::class,
    route = "profile/{id}?tab={tab}",
)
@Composable
fun ProfilePage(key: Profile) {
    Text("用户 ${key.id}，页签 ${key.tab}")
}
```

KSP 会生成 `appInitEntryProvider()` 和 `appStringRouteResolver()`，两者统一位于
`com.liangwenb.nav.generated`。在 Activity
绑定回退栈时传入 resolver：

```kotlin
val backStack = rememberNavBackStack(Home)
val routeResolver = remember { appStringRouteResolver() }

DisposableEffect(backStack, routeResolver) {
    NavBackStackUtils.attach(
        context = this@MainActivity,
        navBackStack = backStack,
        stringRouteResolver = routeResolver,
    )
    onDispose { NavBackStackUtils.detach(this@MainActivity) }
}
```

两种调用最终进入相同页面：

```kotlin
NavBackStackUtils.go(Profile(id = 42, tab = "posts"))

when (val result = NavBackStackUtils.go("profile/42?tab=posts")) {
    StringRouteNavigationResult.Navigated -> Unit
    StringRouteNavigationResult.Duplicate -> Unit
    is StringRouteNavigationResult.NotFound -> Log.w("Route", "未注册的路由")
    is StringRouteNavigationResult.Invalid -> Log.w("Route", result.reason)
    is StringRouteNavigationResult.Intercepted -> Log.d("Route", result.reason.orEmpty())
    StringRouteNavigationResult.NoAttachedBackStack,
    StringRouteNavigationResult.NoRouteResolver,
    StringRouteNavigationResult.AmbiguousContext,
    -> Unit
}
```

也可以从 Key 得到 canonical 字符串，用于构造应用内分享参数：

```kotlin
val route = NavBackStackUtils.routeOf(Profile(id = 42, tab = "posts"))
// profile/42?tab=posts
```

路由约束：

- 只接收相对路径，例如 `profile/{id}`，不直接接收 scheme、host 或 fragment。
- 路径参数必须存在；可空参数放在 query 中，缺省时为 `null`。
- 支持 `String`、`Int`、`Long`、`Float`、`Double`、`Boolean` 和枚举。
- Key 必须使用 `@Serializable`；重复、歧义、参数类型不支持或构造参数不匹配会在 KSP 阶段失败。
- path 和 query 值使用 UTF-8 percent encoding；重复、未知或格式不完整的 query 会返回 `Invalid`。
- `goResult` 和 `finish` 继续使用 Key 模式，以保留结果泛型和具体实例语义。

字符串 API 不直接解析完整 Deep Link。应用应先校验外部 URI 的 scheme、host 和来源，再把已验证的
相对路径交给 resolver。没有显式传 `context` 时，仅允许存在一个已绑定回退栈；多个 Activity 同时
绑定时会返回 `AmbiguousContext`，此时应传入目标 Activity。

## 测试

项目沿用 JUnit 4、AndroidX Test 和 Compose UI Test，不额外引入 Mock 或 DI 框架。常用验证命令：

```shell
# JVM 单元测试、跨模块 KSP 生成、Debug/Release 构建和全部 Lint
./gradlew test :app:assembleDebug :app:assembleRelease \
  :app:lint :nav:lint :feature_sample:lint --warning-mode all

# 字符串路由真实 BackStack 测试（需要设备或模拟器）
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.liangwenb.sample.StringRouteNavigationTest

# 普通弹窗、底部弹窗的遮罩与系统返回测试（需要设备或模拟器）
./gradlew :nav:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.liangwenb.nav.NavDialogTest
```
