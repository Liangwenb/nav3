package com.liangwenb.nav.processor

import com.google.devtools.ksp.processing.Resolver
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val ROUTE_DESCRIPTOR = "Lcom/liangwenb/nav/route/Route;"
private val routeDescriptorBytes = ROUTE_DESCRIPTOR.toByteArray()

internal data class BinaryRouteDeclaration(
    val ownerJvmName: String,
    val jvmName: String,
    val methodDescriptor: String,
    val keyType: String,
    val navType: String,
    val route: String,
) {
    val packageName: String = ownerJvmName
        .substringBeforeLast('/', missingDelimiterValue = "")
        .replace('/', '.')
}

/** Reads the KSP2 compilation classpath without requiring per-module package configuration. */
internal fun Resolver.compilationLibraries(): List<File> {
    val companion = runCatching {
        javaClass.getField("Companion").get(null)
    }.getOrNull() ?: unsupportedKspImplementation()
    val config = runCatching {
        companion.javaClass.getMethod("getKspConfig").invoke(companion)
    }.getOrNull() ?: unsupportedKspImplementation()
    val libraries = runCatching {
        config.javaClass.getMethod("getLibraries").invoke(config)
    }.getOrNull() as? List<*> ?: unsupportedKspImplementation()
    return libraries.filterIsInstance<File>()
}

private fun Resolver.unsupportedKspImplementation(): Nothing = error(
    "Nav3 zero-config module aggregation requires KSP2; unsupported Resolver: ${javaClass.name}",
)

/** Scans compiled dependencies for binary-retained `@Route` declarations. */
internal object RouteClasspathScanner {
    private data class Fingerprint(
        val path: String,
        val length: Long,
        val lastModified: Long,
    )

    private val archiveCache = ConcurrentHashMap<Fingerprint, List<BinaryRouteDeclaration>>()

    fun scan(libraries: Iterable<File>): List<BinaryRouteDeclaration> = libraries
        .flatMap(::scanLibrary)
        .distinctBy { declaration ->
            listOf(
                declaration.ownerJvmName,
                declaration.jvmName,
                declaration.methodDescriptor,
                declaration.keyType,
            ).joinToString("|")
        }
        .sortedWith(
            compareBy(
                BinaryRouteDeclaration::packageName,
                BinaryRouteDeclaration::ownerJvmName,
                BinaryRouteDeclaration::jvmName,
                BinaryRouteDeclaration::methodDescriptor,
            ),
        )

    private fun scanLibrary(file: File): List<BinaryRouteDeclaration> = when {
        file.isDirectory -> file.walkTopDown()
            .filter { candidate -> candidate.isFile && candidate.extension == "class" }
            .sortedBy(File::getPath)
            .flatMap { candidate -> scanClass(candidate.readBytes()).asSequence() }
            .toList()

        file.isFile && file.extension == "jar" -> scanArchiveCached(file)
        file.isFile && file.extension == "aar" -> scanAar(file)
        else -> emptyList()
    }

    private fun scanArchiveCached(file: File): List<BinaryRouteDeclaration> {
        val fingerprint = Fingerprint(file.canonicalPath, file.length(), file.lastModified())
        return archiveCache.computeIfAbsent(fingerprint) { scanJar(file) }
    }

    private fun scanJar(file: File): List<BinaryRouteDeclaration> = ZipFile(file).use { zip ->
        zip.entries().asSequence()
            .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
            .sortedBy { entry -> entry.name }
            .flatMap { entry -> scanClass(zip.getInputStream(entry).readBytes()).asSequence() }
            .toList()
    }

    private fun scanAar(file: File): List<BinaryRouteDeclaration> = ZipFile(file).use { zip ->
        zip.entries().asSequence()
            .filter { entry ->
                !entry.isDirectory && (entry.name == "classes.jar" ||
                    entry.name.startsWith("libs/") && entry.name.endsWith(".jar"))
            }
            .sortedBy { entry -> entry.name }
            .flatMap { entry -> scanNestedJar(zip.getInputStream(entry).readBytes()).asSequence() }
            .toList()
    }

    private fun scanNestedJar(bytes: ByteArray): List<BinaryRouteDeclaration> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            buildList {
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        addAll(scanClass(zip.readBytes()))
                    }
                    entry = zip.nextEntry
                }
            }
        }

    private fun scanClass(bytes: ByteArray): List<BinaryRouteDeclaration> {
        if (!bytes.contains(routeDescriptorBytes)) return emptyList()
        return buildList {
            ClassReader(bytes).accept(
                RouteClassVisitor(::add),
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
        }
    }
}

private class RouteClassVisitor(
    private val onRoute: (BinaryRouteDeclaration) -> Unit,
) : ClassVisitor(Opcodes.ASM9) {
    private lateinit var ownerJvmName: String

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        ownerJvmName = name
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
            if (annotationDescriptor != ROUTE_DESCRIPTOR) return null
            return RouteAnnotationVisitor { keyType, navType, route ->
                onRoute(
                    BinaryRouteDeclaration(
                        ownerJvmName = ownerJvmName,
                        jvmName = name,
                        methodDescriptor = descriptor,
                        keyType = keyType,
                        navType = navType,
                        route = route,
                    ),
                )
            }
        }
    }
}

private class RouteAnnotationVisitor(
    private val onComplete: (keyType: String, navType: String, route: String) -> Unit,
) : AnnotationVisitor(Opcodes.ASM9) {
    private var keyType: String? = null
    private var navType: String = "Screen"
    private var route: String = ""

    override fun visit(name: String, value: Any) {
        when (name) {
            "key" -> keyType = (value as Type).className
            "route" -> route = value as String
        }
    }

    override fun visitEnum(name: String, descriptor: String, value: String) {
        if (name == "type") navType = value
    }

    override fun visitEnd() {
        onComplete(
            checkNotNull(keyType) { "@Route bytecode is missing its key argument" },
            navType,
            route,
        )
    }
}

private fun ByteArray.contains(target: ByteArray): Boolean {
    if (target.isEmpty() || size < target.size) return false
    for (index in 0..size - target.size) {
        var matched = true
        for (targetIndex in target.indices) {
            if (this[index + targetIndex] != target[targetIndex]) {
                matched = false
                break
            }
        }
        if (matched) return true
    }
    return false
}
