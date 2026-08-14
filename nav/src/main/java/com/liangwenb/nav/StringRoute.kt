package com.liangwenb.nav

import androidx.navigation3.runtime.NavKey
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 将受支持的相对字符串路由与类型安全 [NavKey] 相互转换。
 *
 * 应优先使用 KSP 生成的 `xxxStringRouteResolver()`，避免手工维护注册表。
 */
interface StringRouteResolver {
    fun resolve(route: String): StringRouteResolveResult

    fun format(key: NavKey): String?

    companion object {
        fun of(vararg definitions: StringRouteDefinition): StringRouteResolver =
            DefaultStringRouteResolver(definitions.toList())
    }
}

/** 字符串解析结果；失败时不会产生 Key，也不会修改回退栈。 */
sealed interface StringRouteResolveResult {
    data class Matched(val key: NavKey) : StringRouteResolveResult

    data class NotFound(val route: String) : StringRouteResolveResult

    data class Invalid(val route: String, val reason: String) : StringRouteResolveResult
}

/** 字符串路由进入现有导航管线后的可观察结果。 */
sealed interface StringRouteNavigationResult {
    data object Navigated : StringRouteNavigationResult

    data object Duplicate : StringRouteNavigationResult

    data class NotFound(val route: String) : StringRouteNavigationResult

    data class Invalid(val route: String, val reason: String) : StringRouteNavigationResult

    data class Intercepted(val reason: String?) : StringRouteNavigationResult

    data object NoAttachedBackStack : StringRouteNavigationResult

    data object NoRouteResolver : StringRouteNavigationResult

    data object AmbiguousContext : StringRouteNavigationResult
}

/**
 * 单条字符串路由定义，主要供 KSP 生成代码使用。
 *
 * [createKey] 只负责从已匹配参数构造 Key；模式解析、编码和错误隔离由 resolver 统一处理。
 */
class StringRouteDefinition(
    val pattern: String,
    private val formatKey: (NavKey) -> String? = { null },
    private val createKey: (StringRouteArguments) -> NavKey,
) {
    private val template = StringRouteTemplate.parse(pattern)

    internal fun match(route: ParsedStringRoute): MatchResult = template.match(route)

    internal fun create(arguments: Map<String, String>): NavKey = createKey(StringRouteArguments(arguments))

    internal fun format(key: NavKey): String? = formatKey(key)
}

/** 传给生成 Key 工厂的已解码路由参数。 */
class StringRouteArguments internal constructor(
    private val values: Map<String, String>,
) {
    fun required(name: String): String = values[name]
        ?: throw IllegalArgumentException("缺少路由参数: $name")

    fun optional(name: String): String? = values[name]
}

/** 对生成的字符串路由参数进行 percent encode。 */
fun encodeStringRouteValue(value: Any): String =
    java.net.URLEncoder.encode(
        (value as? Enum<*>)?.name ?: value.toString(),
        StandardCharsets.UTF_8.name(),
    ).replace("+", "%20")

/** 按 canonical 模式格式化 Key 参数；query 中的 null 参数会被省略。 */
fun formatStringRoute(
    pattern: String,
    arguments: Map<String, Any?>,
): String = StringRouteTemplate.parse(pattern).format(arguments)

private class DefaultStringRouteResolver(
    private val definitions: List<StringRouteDefinition>,
) : StringRouteResolver {
    override fun resolve(route: String): StringRouteResolveResult {
        val parsed = ParsedStringRoute.parse(route)
            ?: return StringRouteResolveResult.Invalid(route, "路由必须是相对路径，且不能包含 fragment")

        definitions.forEach { definition ->
            when (val match = definition.match(parsed)) {
                MatchResult.NoMatch -> Unit
                is MatchResult.Invalid -> return StringRouteResolveResult.Invalid(route, match.reason)
                is MatchResult.Matched -> return runCatching { definition.create(match.arguments) }
                    .fold(
                        onSuccess = { StringRouteResolveResult.Matched(it) },
                        onFailure = { StringRouteResolveResult.Invalid(route, "路由参数无法构造 NavKey") },
                    )
            }
        }
        return StringRouteResolveResult.NotFound(route)
    }

    override fun format(key: NavKey): String? = definitions.firstNotNullOfOrNull { it.format(key) }
}

internal data class ParsedStringRoute(
    val pathSegments: List<String>,
    val query: Map<String, String>,
) {
    companion object {
        fun parse(route: String): ParsedStringRoute? {
            if (route.isBlank() || route.startsWith('/') || route.contains("://") || '#' in route) return null
            val parts = route.split('?', limit = 2)
            val pathSegments = parts[0].split('/').map { decodePath(it) ?: return null }
            if (pathSegments.any { it.isEmpty() }) return null
            val query = if (parts.size == 1) emptyMap() else parseQuery(parts[1]) ?: return null
            return ParsedStringRoute(pathSegments, query)
        }

        private fun parseQuery(query: String): Map<String, String>? {
            if (query.isEmpty()) return emptyMap()
            val values = linkedMapOf<String, String>()
            query.split('&').forEach { item ->
                val pair = item.split('=', limit = 2)
                if (pair.size != 2) return null
                val name = decodeQuery(pair[0]) ?: return null
                val value = decodeQuery(pair[1]) ?: return null
                if (name.isEmpty() || values.put(name, value) != null) return null
            }
            return values
        }

        private fun decodePath(value: String): String? = runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull()

        private fun decodeQuery(value: String): String? = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()
    }
}

private data class StringRouteTemplate(
    val pathSegments: List<String>,
    val queryArguments: Map<String, String>,
) {
    fun format(arguments: Map<String, Any?>): String {
        val path = pathSegments.joinToString("/") { segment ->
            val name = segment.placeholderName() ?: return@joinToString segment
            val value = requireNotNull(arguments[name]) { "缺少路由参数: $name" }
            encodeStringRouteValue(value)
        }
        val query = queryArguments.mapNotNull { (queryName, argumentName) ->
            arguments[argumentName]?.let { "$queryName=${encodeStringRouteValue(it)}" }
        }.joinToString("&")
        return if (query.isEmpty()) path else "$path?$query"
    }

    fun match(route: ParsedStringRoute): MatchResult {
        if (pathSegments.size != route.pathSegments.size) return MatchResult.NoMatch
        val arguments = linkedMapOf<String, String>()
        pathSegments.zip(route.pathSegments).forEach { (template, value) ->
            val name = template.placeholderName()
            if (name == null && template != value) return MatchResult.NoMatch
            if (name != null) arguments[name] = value
        }
        if (!route.query.keys.all(queryArguments::containsKey)) {
            return MatchResult.Invalid("路由包含未声明的 query 参数")
        }
        queryArguments.forEach { (queryName, argumentName) ->
            route.query[queryName]?.let { arguments[argumentName] = it }
        }
        return MatchResult.Matched(arguments)
    }

    companion object {
        fun parse(pattern: String): StringRouteTemplate {
            require(pattern.isNotBlank() && !pattern.startsWith('/') && !pattern.contains("://") && '#' !in pattern) {
                "路由模式必须是相对路径"
            }
            val parts = pattern.split('?', limit = 2)
            val pathSegments = parts[0].split('/')
            require(pathSegments.none { it.isEmpty() }) { "路由模式不能包含空路径分段" }
            require(pathSegments.none { ('{' in it || '}' in it) && it.placeholderName() == null }) {
                "路径参数必须使用完整的 {name} 占位符"
            }
            val queryArguments = if (parts.size == 1) emptyMap() else buildMap {
                parts[1].split('&').forEach { item ->
                    val pair = item.split('=', limit = 2)
                    require(pair.size == 2 && pair[0].isNotBlank()) { "query 参数必须是 name={argument}" }
                    val argument = requireNotNull(pair[1].placeholderName()) {
                        "query 参数必须引用占位符"
                    }
                    require(put(pair[0], argument) == null) { "query 参数不能重复: ${pair[0]}" }
                }
            }
            val argumentNames = pathSegments.mapNotNull(String::placeholderName) + queryArguments.values
            require(argumentNames.size == argumentNames.toSet().size) { "同一路由参数不能重复" }
            return StringRouteTemplate(pathSegments, queryArguments)
        }
    }
}

internal sealed interface MatchResult {
    data object NoMatch : MatchResult

    data class Matched(val arguments: Map<String, String>) : MatchResult

    data class Invalid(val reason: String) : MatchResult
}

private fun String.placeholderName(): String? =
    takeIf { startsWith('{') && endsWith('}') && length > 2 }
        ?.substring(1, length - 1)
