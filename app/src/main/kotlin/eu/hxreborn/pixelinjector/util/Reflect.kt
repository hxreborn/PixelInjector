package eu.hxreborn.pixelinjector.util

import java.lang.reflect.Field
import java.lang.reflect.Method

fun Class<*>.optionalField(vararg names: String): Field? =
    names.firstNotNullOfOrNull { name ->
        try {
            getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            null
        }
    }

fun Class<*>.requiredField(vararg names: String): Field =
    optionalField(*names) ?: throw NoSuchFieldException("$simpleName.${names.joinToString("/")}")

fun Class<*>.requiredMethod(
    name: String,
    vararg params: Class<*>?,
): Method =
    try {
        getDeclaredMethod(name, *params).apply { isAccessible = true }
    } catch (_: NoSuchMethodException) {
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
    generateSequence(this) { it.superclass }.firstNotNullOfOrNull { it.optionalField(name) }

fun Method.signature(): String =
    "${declaringClass.simpleName}#$name(${parameterTypes.joinToString(",") { it.simpleName }})"

fun Field.signature(): String = "${declaringClass.simpleName}#$name"

fun Throwable.detail(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName.ifEmpty { javaClass.name }

fun Throwable.member(): String =
    (this as? ClassNotFoundException)
        ?.message
        ?.substringAfter("find class \"", "")
        ?.substringBefore('"')
        ?.substringAfterLast('.')
        ?.ifEmpty { null }
        ?: detail()

fun Throwable.reason(): String =
    when (this) {
        is ClassNotFoundException -> "no-class"
        is NoSuchFieldException -> "no-field"
        is NoSuchMethodException -> "no-method"
        else -> javaClass.simpleName
    }

fun <T> orNullIfMissing(lookup: () -> T): T? =
    try {
        lookup()
    } catch (e: ReflectiveOperationException) {
        null
    }

inline fun <T> resolving(lookup: () -> T): Result<T> =
    try {
        Result.success(lookup())
    } catch (e: Exception) {
        Result.failure(e)
    } catch (e: LinkageError) {
        Result.failure(e)
    }
