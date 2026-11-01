package eu.hxreborn.pixelinjector.xposed.hook

import eu.hxreborn.pixelinjector.util.requiredMethods
import java.lang.reflect.Method

private const val SNAPSHOT_STATE = "androidx.compose.runtime.SnapshotStateKt"
private const val STATE = "androidx.compose.runtime.State"
private const val MUTABLE_STATE = "androidx.compose.runtime.MutableState"
private const val DEFAULT_POLICY_ARG_MASK = 2

internal class RecomposeTrigger private constructor(
    private val getValue: Method,
    private val setValue: Method,
    @Volatile var state: Any,
) {
    fun read() {
        getValue.invoke(state)
    }

    fun bump() {
        setValue.invoke(state, getValue.invoke(state) as Int + 1)
    }

    fun adopt(saved: Any?) {
        if (saved == null || !getValue.declaringClass.isInstance(saved)) return
        state = saved
        bump()
    }

    companion object {
        fun resolve(cl: ClassLoader): RecomposeTrigger {
            val newState =
                cl.loadClass(SNAPSHOT_STATE).requiredMethods("mutableStateOf\$default").single()
            return RecomposeTrigger(
                getValue = cl.loadClass(STATE).getMethod("getValue"),
                setValue = cl.loadClass(MUTABLE_STATE).getMethod("setValue", Any::class.java),
                state = checkNotNull(newState.invoke(null, 0, null, DEFAULT_POLICY_ARG_MASK, null)),
            )
        }
    }
}
