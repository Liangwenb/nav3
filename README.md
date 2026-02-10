# Compose navigation3 简单封装。 通过注解设置路由 [![](https://jitpack.io/v/Liangwenb/nav3.svg)](https://jitpack.io/#Liangwenb/nav3)

### 插件设置

    plugins {
        alias(libs.plugins.ksp)
    }
    ksp {
        arg("MODULE_NAME", project.name)
    }
    dependencies {
        api("com.github.Liangwenb.nav3:nav:v1.1.0")
        ksp("com.github.Liangwenb.nav3:route_processor:v1.1.0")
    }

### Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
  
        setContent {
                val backStack = rememberNavBackStack(Home)
                val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
                NavBackStackUtils.attach(this, backStack)
                NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategy = dialogStrategy,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    //将生成的路由方法放到这里
                    //生成的规则 是 ${project.name}InitEntryProvider
                    //如果是模块名是 app  则生成  appInitEntryProvider()
                    //如果是模块名是 base  则生成  baseInitEntryProvider()
                    appInitEntryProvider()
                }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        NavBackStackUtils.detach(this)

    }

### 新建路由

新建Compose界面 在函数上添加注解

    @Route(key = XXX::class)
    @Composable
    fun XXX(viewModel: HomeViewModel = viewModel()) {
       Box() {
    
        }
     }

在data 模块中 nav路径下 新增一个 XXX 的类,需要被 `@Serializable`注解 和 继承 `NavKey` ，如果跳转需要返回值则继承
`ResultNavKey` ，例如：

    @Serializable
    data object XXX : NavKey
    
    @Serializable
    object Im {
        @Serializable
        data class XXX(val uid: Int) : NavKey
    }
    
    @Serializable
    object Live {
        @Serializable
        data class XXX(val uid: Int) : ResultNavKey
    }

### 跳转

普通跳转使用

    NavBackStackUtils.go(XXX)

如果需要返回的时候带回数据使用

    NavBackStackUtils.goResult(XXX)
    界面返回时使用
    NavBackStackUtils.finishResult(XXX)

返回上级界面

    NavBackStackUtils.back()

指定关闭界面

    NavBackStackUtils.finish(XXX)

### ViewModel

**==逻辑和数据都保存在viewModel里面，界面的函数里面只负责显示UI==**

在不需要传递参数的情况下继承`viewModel`

    class XXXViewModel : ViewModel() {
    
    }
    //界面中使用
    @Route(key = XXX::class)
    @Composable
    fun XXX(viewModel: HomeViewModel = viewModel()) {
        Box() {
        
            }
    }

在需要传递路由的情况需要继承`KeyViewModel`

    class XXXViewModel(key: XXX) : KeyViewModel<XXX>(key) {
    
    }
    //界面中使用
    @Route(key = XXX::class)
    @Composable
    fun XXX(viewModel: HomeViewModel) {
        Box() {
        
            }
    }
