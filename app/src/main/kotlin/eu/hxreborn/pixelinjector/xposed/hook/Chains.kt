package eu.hxreborn.pixelinjector.xposed.hook

import io.github.libxposed.api.XposedInterface

internal fun XposedInterface.Chain.proceedReplacing(
    index: Int,
    value: Any?,
): Any? = proceed(args.toTypedArray<Any?>().also { it[index] = value })
