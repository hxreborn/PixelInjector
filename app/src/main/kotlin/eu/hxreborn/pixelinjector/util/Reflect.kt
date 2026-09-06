package eu.hxreborn.pixelinjector.util

import java.lang.reflect.Field
import java.lang.reflect.Method

fun Class<*>.optionalField(vararg names: String): Field? =
    names.firstNotNullOfOrNull { name ->
        runCatching { getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
    }

fun Class<*>.requiredField(vararg names: String): Field =
    optionalField(*names) ?: throw NoSuchFieldException("$simpleName.${names.joinToString("/")}")

fun Class<*>.requiredMethod(
    name: String,
    vararg params: Class<*>?,
): Method =
    runCatching { getDeclaredMethod(name, *params).apply { isAccessible = true } }.getOrElse {
        throw NoSuchMethodException(
            "$simpleName.$name(${params.joinToString(",") { it?.simpleName ?: "?" }})",
        )
    }

fun Class<*>.requiredMethods(name: String): List<Method> =
    declaredMethods
        .filter { it.name == name }
        .onEach { it.isAccessible = true }
        .ifEmpty { throw NoSuchMethodException("$simpleName.$name") }

fun Class<*>.optionalMethod(
    name: String,
    argCount: Int? = null,
): Method? =
    declaredMethods
        .filter { it.name == name && (argCount == null || it.parameterCount == argCount) }
        .minByOrNull { it.parameterCount }
        ?.apply { isAccessible = true }

fun Class<*>.findFieldUpward(name: String): Field? =
    generateSequence(this) { it.superclass }.firstNotNullOfOrNull { cls ->
        runCatching { cls.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
    }

fun Method.signature(): String =
    "${declaringClass.simpleName}#$name(${parameterTypes.joinToString(",") { it.simpleName }})"

fun Field.signature(): String = "${declaringClass.simpleName}#$name"

fun Throwable.reason(): String =
    when (this) {
        is ClassNotFoundException -> "no-class"
        is NoSuchFieldException -> "no-field"
        is NoSuchMethodException -> "no-method"
        else -> javaClass.simpleName
    }
