package com.liangwenb.nav.processor

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class RouteClasspathScannerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scansBinaryRouteFromClassDirectory() {
        val classes = temporaryFolder.newFolder("classes")
        val routeClass = File(classes, "com/example/FeatureRoutesKt.class")
        routeClass.parentFile.mkdirs()
        routeClass.writeBytes(routeClassBytes())

        val result = RouteClasspathScanner.scan(listOf(classes))

        assertEquals(listOf(expectedRoute()), result)
    }

    @Test
    fun scansBinaryRouteFromJar() {
        val jar = temporaryFolder.newFile("feature.jar")
        JarOutputStream(jar.outputStream()).use { output ->
            output.putNextEntry(JarEntry("com/example/FeatureRoutesKt.class"))
            output.write(routeClassBytes())
            output.closeEntry()
        }

        val result = RouteClasspathScanner.scan(listOf(jar))

        assertEquals(listOf(expectedRoute()), result)
    }

    private fun expectedRoute() = BinaryRouteDeclaration(
        ownerJvmName = "com/example/FeatureRoutesKt",
        jvmName = "FeaturePage",
        methodDescriptor = "(Lcom/example/FeatureKey;)V",
        keyType = "com.example.FeatureKey",
        navType = "BottomDialog",
        route = "feature/{id}",
    )

    private fun routeClassBytes(): ByteArray = ClassWriter(0).apply {
        visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
            "com/example/FeatureRoutesKt",
            null,
            "java/lang/Object",
            null,
        )
        visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "FeaturePage",
            "(Lcom/example/FeatureKey;)V",
            null,
            null,
        ).apply {
            visitAnnotation("Lcom/liangwenb/nav/route/Route;", false).apply {
                visit("key", Type.getType("Lcom/example/FeatureKey;"))
                visitEnum("type", "Lcom/liangwenb/nav/route/NavType;", "BottomDialog")
                visit("route", "feature/{id}")
                visitEnd()
            }
            visitEnd()
        }
        visitEnd()
    }.toByteArray()
}
