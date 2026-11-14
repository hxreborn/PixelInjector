package eu.hxreborn.pixelinjector.xposed

import android.content.SharedPreferences
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.PerPackage
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import io.github.libxposed.api.XposedModule

internal open class Value<T : Any, R> private constructor(
    default: T,
    private val prefFor: (String) -> PrefSpec<T>,
    private val onLoad: (R) -> Unit,
    private val map: (T) -> R,
) {
    constructor(pref: PrefSpec<T>, onLoad: (R) -> Unit = {}, map: (T) -> R) :
        this(pref.default, { pref }, onLoad, map)

    constructor(prefs: PerPackage<PrefSpec<T>>, onLoad: (R) -> Unit = {}, map: (T) -> R) :
        this(
            prefs.byPackage.values
                .first()
                .default,
            prefs::get,
            onLoad,
            map,
        )

    @Volatile var value: R = map(default)
        private set

    fun key(process: String): String = prefFor(process).key

    fun load(
        prefs: SharedPreferences,
        process: String,
    ) {
        value = map(prefFor(process).read(prefs))
        onLoad(value)
    }
}

internal class Switch : Value<Boolean, Boolean> {
    constructor(pref: BoolPref, onLoad: (Boolean) -> Unit = {}) : super(pref, onLoad, { it })

    constructor(
        prefs: PerPackage<BoolPref>,
        onLoad: (Boolean) -> Unit = {
        },
    ) : super(prefs, onLoad, { it })

    val enabled: Boolean get() = value
}

internal class Tweak(
    val key: String,
    val targets: Set<Target>,
    val prefs: List<Value<*, *>> = emptyList(),
    val saveState: () -> Any? = { null },
    val restoreState: (Any?) -> Unit = {},
    val onReloadRejected: () -> Unit = {},
    val install: XposedModule.(ClassLoader) -> Boolean,
)
