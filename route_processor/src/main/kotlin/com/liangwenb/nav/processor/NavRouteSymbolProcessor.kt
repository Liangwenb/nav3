package com.liangwenb.nav.processor

import androidx.navigation3.runtime.NavKey
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

internal class NavRouteSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        NavRouteSymbolProcessor(environment)
}

/** 聚合 App 源码和普通依赖模块的 `@Route`，生成全局路由入口。 */
internal class NavRouteSymbolProcessor(private val environment: SymbolProcessorEnvironment) :
    SymbolProcessor {

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val sourceRouteFunctions = resolver
            .getSymbolsWithAnnotation(Route::class.qualifiedName!!)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        val deferredSymbols = sourceRouteFunctions.filterNot { it.validate() }
        if (deferredSymbols.isNotEmpty()) return deferredSymbols

        val entries = (sourceRouteFunctions.map(::sourceRouteEntry) + dependencyRouteEntries(resolver))
            .distinctBy { entry ->
                "${entry.packageName}.${entry.functionName}|${entry.keyType}"
            }
            .sortedWith(compareBy(RouteEntry::keyType, RouteEntry::packageName, RouteEntry::functionName))
        generated = true
        if (entries.isEmpty()) return emptyList()
        entries.groupBy(RouteEntry::keyType).values.firstOrNull { it.size > 1 }?.let { duplicates ->
            val message = "同一 NavKey 只能注册一个页面: " +
                duplicates.joinToString { "${it.keyType} -> ${it.packageName}.${it.functionName}" }
            environment.logger.error(message)
            throw IllegalStateException(message)
        }
        val routedEntries = entries.filter { it.route.isNotEmpty() }
        routedEntries.forEachIndexed { index, entry ->
            routedEntries.drop(index + 1).firstOrNull { other ->
                areRoutesAmbiguous(entry.route, other.route)
            }?.let { other ->
                val message = "字符串路由模式重复或歧义: ${entry.route}, ${other.route}"
                environment.logger.error(message)
                throw IllegalStateException(message)
            }
        }

        generateEntryProvider(entries, resolver)
        return emptyList()
    }

    private fun sourceRouteEntry(function: KSFunctionDeclaration): RouteEntry {
        val routeAnnotation = function.routeAnnotation()
            ?: throw IllegalStateException("函数 ${function.simpleName.asString()} 缺少 @Route 注解")
        val typeArgument = routeAnnotation.arguments.firstOrNull { it.name?.asString() == "type" }
            ?: throw IllegalStateException(
                "函数 ${function.simpleName.asString()} 的 @Route 注解缺少 'type' 参数",
            )
        val keyType = (routeAnnotation.arguments
            .firstOrNull { it.name?.asString() == "key" }
            ?.value as? KSType)?.declaration
            ?: throw IllegalStateException(
                "函数 ${function.simpleName.asString()} 的 @Route 注解中 'key' 参数缺失或类型错误",
            )
        val qualifiedKeyType = keyType.qualifiedName?.asString()
            ?: throw IllegalStateException("无法解析 ${function.simpleName.asString()} 的 NavKey 全限定名")
        return routeEntry(
            function = function,
            keyType = qualifiedKeyType,
            navType = parseNavType(typeArgument.value.toString(), function),
            route = routeAnnotation.arguments
                .firstOrNull { it.name?.asString() == "route" }
                ?.value as? String ?: "",
        )
    }

    @OptIn(KspExperimental::class)
    private fun dependencyRouteEntries(resolver: Resolver): List<RouteEntry> {
        val binaryRoutes = RouteClasspathScanner.scan(resolver.compilationLibraries())
        if (binaryRoutes.isEmpty()) return emptyList()
        val declarationsByPackage = binaryRoutes
            .map(BinaryRouteDeclaration::packageName)
            .distinct()
            .associateWith { packageName ->
                resolver.getDeclarationsFromPackage(packageName)
                    .filterIsInstance<KSFunctionDeclaration>()
                    .toList()
            }
        return binaryRoutes.map { binaryRoute ->
            val functions = declarationsByPackage.getValue(binaryRoute.packageName).filter { function ->
                resolver.getOwnerJvmClassName(function)?.replace('.', '/') == binaryRoute.ownerJvmName &&
                    resolver.getJvmName(function) == binaryRoute.jvmName
            }
            val function = functions.singleOrNull() ?: throw IllegalStateException(
                "无法唯一解析依赖模块路由: ${binaryRoute.ownerJvmName}.${binaryRoute.jvmName}" +
                    binaryRoute.methodDescriptor,
            )
            checkNotNull(function.routeAnnotation()) {
                "依赖模块的 @Route 必须使用 BINARY 保留策略: ${binaryRoute.ownerJvmName}"
            }
            routeEntry(
                function = function,
                keyType = binaryRoute.keyType,
                navType = parseNavType(binaryRoute.navType, function),
                route = binaryRoute.route,
            )
        }
    }

    private fun routeEntry(
        function: KSFunctionDeclaration,
        keyType: String,
        navType: NavType,
        route: String,
    ): RouteEntry {
        val forbiddenVisibilities = setOf(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.INTERNAL)
        require(function.parentDeclaration == null && function.modifiers.none(forbiddenVisibilities::contains)) {
            "@Route 只能标注 public 顶层函数: " +
                "${function.packageName.asString()}.${function.simpleName.asString()}"
        }
        val keyDeclaration = (function.routeAnnotation()?.arguments
            ?.firstOrNull { it.name?.asString() == "key" }
            ?.value as? KSType)?.declaration
            ?: throw IllegalStateException(
                "无法解析 ${function.packageName.asString()}.${function.simpleName.asString()} 的 NavKey",
            )
        require(keyDeclaration.qualifiedName?.asString() == keyType) {
            "@Route NavKey 与编译产物不一致: $keyType"
        }
        if (route.isNotEmpty()) {
            validateRoute(route, function)
            requireSerializableKey(keyDeclaration, function)
        }
        return RouteEntry(
            function = function,
            functionName = function.simpleName.asString(),
            packageName = function.packageName.asString(),
            navType = navType,
            keyType = keyType,
            route = route,
        )
    }

    private fun KSFunctionDeclaration.routeAnnotation() = annotations.firstOrNull { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == Route::class.qualifiedName
    }

    private fun parseNavType(value: String, function: KSFunctionDeclaration): NavType = runCatching {
        NavType.valueOf(value.substringAfterLast('.'))
    }.getOrElse { cause ->
        val message = "无法解析函数 ${function.simpleName.asString()} 的 NavType: $value"
        environment.logger.error(message, function)
        throw IllegalStateException(message, cause)
    }

    private fun generateEntryProvider(entries: List<RouteEntry>, resolver: Resolver) {
        val moduleName = moduleName(resolver)
        val fileBuilder =
            FileSpec.builder("com.liangwenb.nav.generated", "GeneratedEntryProvider")

        fileBuilder.addImport("androidx.navigation3.runtime", "entryProvider")
        fileBuilder.addImport("com.liangwenb.nav", "keyViewModel")
        fileBuilder.addImport("com.liangwenb.nav", "bottomDialog")
        fileBuilder.addImport("com.liangwenb.nav", "dialog")
        fileBuilder.addImport("com.liangwenb.nav", "StringRouteDefinition")
        fileBuilder.addImport("com.liangwenb.nav", "StringRouteResolver")
        fileBuilder.addImport("com.liangwenb.nav", "formatStringRoute")

        val funBuilder = FunSpec.builder("${moduleName}InitEntryProvider")
            .receiver(
                ClassName("androidx.navigation3.runtime", "EntryProviderScope")
                    .parameterizedBy(NavKey::class.asClassName())
            )

        entries.forEach { entry ->

            // 导入函数
            fileBuilder.addImport(entry.packageName, entry.functionName)

            val funcDecl = entry.function

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
        fileBuilder.addFunction(generateStringRouteResolver(entries, resolver, moduleName))
        fileBuilder.build().writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
    }

    @OptIn(KspExperimental::class)
    private fun moduleName(resolver: Resolver): String {
        val rawName = environment.options["MODULE_NAME"] ?: resolver.getModuleName().asString()
        return rawName
            .substringAfterLast(':')
            .substringAfterLast('.')
            .filter { it.isLetterOrDigit() || it == '_' }
            .ifBlank { "app" }
    }

    private fun generateStringRouteResolver(
        entries: List<RouteEntry>,
        resolver: Resolver,
        moduleName: String,
    ): FunSpec {
        val code = CodeBlock.builder()
            .add("return StringRouteResolver.of(\n")
            .indent()
        entries.filter { it.route.isNotEmpty() }.forEach { entry ->
            val declaration = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(entry.keyType),
            ) ?: throw IllegalStateException("无法解析字符串路由 Key: " + entry.keyType)
            code.add("%L,\n", routeDefinition(entry, declaration))
        }
        code.unindent().add(")\n")
        return FunSpec.builder(moduleName + "StringRouteResolver")
            .returns(ClassName("com.liangwenb.nav", "StringRouteResolver"))
            .addCode(code.build())
            .build()
    }

    private fun routeDefinition(
        entry: RouteEntry,
        declaration: KSClassDeclaration,
    ): CodeBlock {
        val pattern = routePattern(entry.route)
        val parameters = routeParameters(declaration, pattern)
        val arguments = if (parameters.isEmpty()) {
            CodeBlock.of("emptyMap()")
        } else {
            CodeBlock.builder().add("mapOf(\n").indent().apply {
                parameters.forEach { parameter ->
                    val name = parameter.name!!.asString()
                    add("%S to key.%L,\n", name, name)
                }
            }.unindent().add(")").build()
        }
        return CodeBlock.builder()
            .add("StringRouteDefinition(\n")
            .indent()
            .add("pattern = %S,\n", entry.route)
            .add("formatKey = { key ->\n")
            .indent()
            .add(
                "if (key is %L) formatStringRoute(%S, %L) else null\n",
                entry.keyType,
                entry.route,
                arguments,
            )
            .unindent()
            .add("},\n")
            .add("createKey = { arguments -> %L },\n", keyCreation(entry, declaration, parameters, pattern))
            .unindent()
            .add(")")
            .build()
    }

    private fun routeParameters(
        declaration: KSClassDeclaration,
        pattern: RoutePattern,
    ): List<KSValueParameter> {
        if (declaration.classKind == ClassKind.OBJECT) {
            require(pattern.allArguments.isEmpty()) { "data object 路由不能声明参数" }
            return emptyList()
        }
        val parameters = declaration.primaryConstructor?.parameters.orEmpty()
        val parametersByName = parameters.associateBy { it.name?.asString().orEmpty() }
        pattern.allArguments.forEach { name ->
            require(parametersByName[name] != null) {
                "路由参数 {$name} 不存在于 " + declaration.simpleName.asString() + " 构造器"
            }
        }
        parameters.forEach { parameter ->
            val name = parameter.name?.asString().orEmpty()
            val type = parameter.type.resolve()
            require(name in pattern.allArguments || parameter.hasDefault || type.nullability == Nullability.NULLABLE) {
                "Key 构造参数 $name 必须出现在 route 中或提供默认值"
            }
            require(name !in pattern.pathArguments || type.nullability != Nullability.NULLABLE) {
                "可空参数 $name 只能放在 query 中"
            }
            if (name in pattern.allArguments) routeArgumentExpression(parameter, name, false)
        }
        return parameters.filter { it.name?.asString() in pattern.allArguments }
    }

    private fun keyCreation(
        entry: RouteEntry,
        declaration: KSClassDeclaration,
        parameters: List<KSValueParameter>,
        pattern: RoutePattern,
    ): CodeBlock {
        if (declaration.classKind == ClassKind.OBJECT) return CodeBlock.of("%L", entry.keyType)
        return CodeBlock.builder().add("%L(\n", entry.keyType).indent().apply {
            parameters.forEach { parameter ->
                val name = parameter.name!!.asString()
                val optional = name in pattern.queryArguments &&
                    parameter.type.resolve().nullability == Nullability.NULLABLE
                add("%L = %L,\n", name, routeArgumentExpression(parameter, name, optional))
            }
        }.unindent().add(")").build()
    }

    private fun routeArgumentExpression(
        parameter: KSValueParameter,
        name: String,
        optional: Boolean,
    ): CodeBlock {
        val type = parameter.type.resolve()
        val qualifiedName = type.declaration.qualifiedName?.asString()
        val source = if (optional) CodeBlock.of("arguments.optional(%S)", name)
        else CodeBlock.of("arguments.required(%S)", name)
        val conversion = when (qualifiedName) {
            "kotlin.String" -> null
            "kotlin.Int" -> "toInt"
            "kotlin.Long" -> "toLong"
            "kotlin.Float" -> "toFloat"
            "kotlin.Double" -> "toDouble"
            "kotlin.Boolean" -> "toBooleanStrict"
            else -> {
                val typeDeclaration = type.declaration as? KSClassDeclaration
                require(typeDeclaration?.classKind == ClassKind.ENUM_CLASS) {
                    "字符串路由不支持参数类型: $qualifiedName"
                }
                return if (optional) {
                    CodeBlock.of("%L?.let(%L::valueOf)", source, qualifiedName)
                } else {
                    CodeBlock.of("%L.valueOf(%L)", qualifiedName, source)
                }
            }
        }
        if (conversion == null) return source
        return if (optional) CodeBlock.of("%L?.%L()", source, conversion)
        else CodeBlock.of("%L.%L()", source, conversion)
    }

    private fun validateRoute(route: String, node: KSAnnotated) {
        runCatching { routePattern(route) }.onFailure {
            environment.logger.error(it.message ?: "字符串路由格式错误", node)
            throw IllegalStateException(it.message, it)
        }
    }

    private fun requireSerializableKey(
        declaration: KSDeclaration,
        node: KSAnnotated,
    ) {
        val isSerializable = declaration.annotations.any { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() ==
                "kotlinx.serialization.Serializable"
        }
        if (!isSerializable) {
            val message = "声明字符串 route 的 NavKey 必须使用 @Serializable: " +
                declaration.qualifiedName?.asString()
            environment.logger.error(message, node)
            throw IllegalStateException(message)
        }
    }

    private fun routePattern(route: String): RoutePattern {
        require(route.isNotBlank() && !route.startsWith('/') && !route.contains("://") && '#' !in route) {
            "字符串路由必须是相对路径"
        }
        val parts = route.split('?', limit = 2)
        val pathSegments = parts[0].split('/')
        require(pathSegments.none(String::isBlank)) { "字符串路由不能包含空路径段" }
        require(pathSegments.none { ('{' in it || '}' in it) && placeholder(it) == null }) {
            "路径参数必须使用完整的 {name} 占位符"
        }
        val pathArguments = pathSegments.mapNotNull(::placeholder)
        val queryNames = mutableSetOf<String>()
        val queryArguments = if (parts.size == 1) emptyList() else
            parts[1].split('&').map { item ->
                val pair = item.split('=', limit = 2)
                require(pair.size == 2 && pair[0].isNotBlank()) { "query 必须是 name={argument}" }
                require(queryNames.add(pair[0])) { "query 参数不能重复: ${pair[0]}" }
                requireNotNull(placeholder(pair[1])) { "query 必须引用参数占位符" }
            }
        val allArguments = pathArguments + queryArguments
        require(allArguments.size == allArguments.toSet().size) { "同一路由参数不能重复" }
        return RoutePattern(pathArguments.toSet(), queryArguments.toSet())
    }

    private fun placeholder(value: String): String? = value
        .takeIf { it.startsWith('{') && it.endsWith('}') && it.length > 2 }
        ?.substring(1, value.length - 1)
}

private data class RoutePattern(
    val pathArguments: Set<String>,
    val queryArguments: Set<String>,
) {
    val allArguments: Set<String> = pathArguments + queryArguments
}

private data class RouteEntry(
    val function: KSFunctionDeclaration,
    val functionName: String,
    val packageName: String,
    val navType: NavType,
    val keyType: String,
    val route: String,
)

internal fun areRoutesAmbiguous(first: String, second: String): Boolean {
    val firstSegments = first.substringBefore('?').split('/')
    val secondSegments = second.substringBefore('?').split('/')
    if (firstSegments.size != secondSegments.size) return false
    return firstSegments.zip(secondSegments).all { (left, right) ->
        left == right || left.isRoutePlaceholder() || right.isRoutePlaceholder()
    }
}

private fun String.isRoutePlaceholder(): Boolean =
    startsWith('{') && endsWith('}') && length > 2
