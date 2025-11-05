package com.rizzmeup.processor

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
import com.rizzmeup.route_annotation.NavType
import com.rizzmeup.route_annotation.Route
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
            val routeAnno = func.annotations.first { it.shortName.asString() == "Route" }

            val navTypeArg = routeAnno.arguments.first { it.name?.asString() == "type" }.value
            val navType = NavType.valueOf(navTypeArg.toString().split(".")[1])

            environment.logger.warn("$navTypeArg")

            // 获取 key 的 KClass
            val keyArg = routeAnno.arguments.first { it.name?.asString() == "key" }.value as KSType
            val keyQualifiedName = keyArg.declaration.qualifiedName!!.asString()



            NavEntryData(
                functionName = func.simpleName.asString(),
                packageName = func.packageName.asString(),
                navType = navType,
                keyType = keyQualifiedName
            )
        }.toList()
        environment.logger.warn("------->")

        if (entries.isNotEmpty()) {
            generateEntryProvider(entries, resolver,)
        }

        return functionDeclarations
    }

    private fun generateEntryProvider(entries: List<NavEntryData>, resolver: Resolver) {
        val moduleName = environment.options["MODULE_NAME"] ?: "UnknownModule"
        val currentPkg = resolver.getAllFiles()
            .map { it.packageName.asString() }
            .distinct()
            .maxByOrNull { it.length }

        val fileBuilder = FileSpec.builder("$currentPkg.generated", "GeneratedEntryProvider")

        fileBuilder.addImport("androidx.navigation3.runtime","entryProvider")
        fileBuilder.addImport("com.liangwenb.nav","keyViewModel")
        fileBuilder.addImport("com.liangwenb.nav","bottomDialog")
        fileBuilder.addImport("com.liangwenb.nav","dialog")

        val funBuilder = FunSpec.builder("${moduleName}InitEntryProvider")
            .receiver(ClassName("androidx.navigation3.runtime", "EntryProviderScope")
                .parameterizedBy(NavKey::class.asClassName()))

        entries.forEach { entry ->

            // 导入函数
            fileBuilder.addImport(entry.packageName, entry.functionName)

            val funcDecl = resolver.getAllFiles()
                .flatMap { it.declarations }
                .filterIsInstance<KSFunctionDeclaration>()
                .firstOrNull { it.simpleName.asString() == entry.functionName } ?: return@forEach

            // 是否存在 KeyViewModel 需求
            val needsKey = funcDecl.parameters.any { param ->
                val type = param.type.resolve().declaration as? KSClassDeclaration ?: return@any false
                type.superTypes.any { superType ->
                    superType.resolve().declaration.qualifiedName?.asString() ==
                            "com.liangwenb.nav.KeyViewModel"
                }
            }

            // ✅ 是否存在 key 类型参数（新增）
            val hasKeyParam = funcDecl.parameters.any { param ->
                param.type.resolve().declaration.qualifiedName?.asString() == entry.keyType
            }

            val provider = when(entry.navType) {
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
            sources = resolver.getAllFiles().toList().toTypedArray()
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


