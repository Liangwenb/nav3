package com.liangwenb.nav.processor

import androidx.navigation3.runtime.NavKey
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

internal class TestKspSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        TestKspSymbolProcessor(environment)
}

/**
 * ksp处理程序
 */
class TestKspSymbolProcessor(private val environment: SymbolProcessorEnvironment) :
    SymbolProcessor {


    override fun process(resolver: Resolver): List<KSAnnotated> {
        val routeFunctions = resolver
            .getSymbolsWithAnnotation(Route::class.qualifiedName!!)
            .filterIsInstance<KSFunctionDeclaration>()

        if (routeFunctions.none()) return emptyList()
        val functionDeclarations = routeFunctions.filter { !it.validate() }.toList()
        val entries = routeFunctions.map { func ->
            val routeAnno = func.annotations.firstOrNull { it.shortName.asString() == "Route" }
                ?: throw IllegalStateException("函数 ${func.simpleName.asString()} 缺少 @Route 注解")

            // 1. 获取并验证 navType
            val typeArg = routeAnno.arguments.firstOrNull { it.name?.asString() == "type" }
                ?: throw IllegalStateException("函数 ${func.simpleName.asString()} 的 @Route 注解缺少 'type' 参数")

            val navTypeArg = typeArg.value
            val navType = try {
                NavType.valueOf(navTypeArg.toString().split(".").last())
            } catch (e: Exception) {
                val errorMsg = "无法解析函数 ${func.simpleName.asString()} 的 navType: $navTypeArg"
                environment.logger.error(errorMsg, typeArg) // 关联到具体的注解参数
                throw IllegalStateException(errorMsg, e)
            }

            // 2. 获取并验证 key 的 KClass (NPE 发生点)
            val keyArgEntry = routeAnno.arguments.firstOrNull { it.name?.asString() == "key" }
            val keyArg = keyArgEntry?.value as? KSType
                ?: run {
                    val errorMsg =
                        "函数 ${func.simpleName.asString()} 的 @Route 注解中 'key' 参数缺失或类型错误"
                    environment.logger.error(errorMsg, func)
                    throw IllegalStateException(errorMsg)
                }

            val declaration = keyArg.declaration
            val qualifiedName = declaration.qualifiedName?.asString() ?: run {
                // 如果 qualifiedName 为空，通常是因为该类型在当前 context 下不可见或未定义
                val errorMsg = """
            无法获取类型 [${declaration.simpleName.asString()}] 的全限定名 (Qualified Name)。
            报错位置: ${func.packageName.asString()}.${func.simpleName.asString()}
            可能原因: 
            1. 该类是局部类或匿名对象。
            2. 该类是由其他处理器生成，且当前处理轮次不可见。
            3. 该类在编译路径中不存在。
        """.trimIndent()
                environment.logger.error(errorMsg, keyArgEntry)
                throw IllegalStateException(errorMsg)
            }

            NavEntryData(
                functionName = func.simpleName.asString(),
                packageName = func.packageName.asString(),
                navType = navType,
                keyType = qualifiedName
            )
        }.toList()
        environment.logger.warn("------->")

        if (entries.isNotEmpty()) {
            generateEntryProvider(entries, resolver)
        }

        return functionDeclarations
    }

    private fun generateEntryProvider(entries: List<NavEntryData>, resolver: Resolver) {
        val moduleName = environment.options["MODULE_NAME"] ?: "UnknownModule"

        // Cache all files and functions once
        val allFiles = resolver.getAllFiles().toList()
        val allFunctions =
            allFiles.flatMap { it.declarations }.filterIsInstance<KSFunctionDeclaration>()

        val currentPkg = allFiles
            .map { it.packageName.asString() }
            .distinct()
            .maxByOrNull { it.length }
            ?.split(".")
            ?.take(2)       // 只取前两级
            ?.joinToString(".")

        val fileBuilder =
            FileSpec.builder("$currentPkg.$moduleName.generated", "GeneratedEntryProvider")

        fileBuilder.addImport("androidx.navigation3.runtime", "entryProvider")
        fileBuilder.addImport("com.liangwenb.nav", "keyViewModel")
        fileBuilder.addImport("com.liangwenb.nav", "bottomDialog")
        fileBuilder.addImport("com.liangwenb.nav", "dialog")

        val funBuilder = FunSpec.builder("${moduleName}InitEntryProvider")
            .receiver(
                ClassName("androidx.navigation3.runtime", "EntryProviderScope")
                    .parameterizedBy(NavKey::class.asClassName())
            )

        entries.forEach { entry ->

            // 导入函数
            fileBuilder.addImport(entry.packageName, entry.functionName)

            val funcDecl = allFunctions
                .firstOrNull { it.simpleName.asString() == entry.functionName && it.packageName.asString() == entry.packageName }
                ?: return@forEach

            // 是否存在 KeyViewModel 需求
            val needsKey = funcDecl.parameters.any { param ->
                val type =
                    param.type.resolve().declaration as? KSClassDeclaration ?: return@any false
                type.superTypes.any { superType ->
                    superType.resolve().declaration.qualifiedName?.asString() ==
                            "com.liangwenb.nav.KeyViewModel"
                }
            }

            // ✅ 是否存在 key 类型参数
            val hasKeyParam = funcDecl.parameters.any { param ->
                param.type.resolve().declaration.qualifiedName?.asString() == entry.keyType
            }

            val provider = when (entry.navType) {
                NavType.Screen -> "entry"
                NavType.Dialog -> "dialog"
                NavType.BottomDialog -> "bottomDialog"
            }

            val call = when {
                hasKeyParam -> "${entry.functionName}(it)"
                needsKey -> "${entry.functionName}(keyViewModel(it))"
                else -> "${entry.functionName}()"
            }

            funBuilder.addCode("    $provider<${entry.keyType}> { $call }\n")
        }

        fileBuilder.addFunction(funBuilder.build())
        val deps = Dependencies(
            aggregating = true,
            sources = allFiles.toTypedArray()
        )
        // 生成文件
        fileBuilder.build().writeTo(environment.codeGenerator, deps)
    }
}

data class NavEntryData(
    val functionName: String,
    val packageName: String,
    val navType: NavType,
    val keyType: String
)
