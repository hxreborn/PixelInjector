package eu.hxreborn.pixelinjector.prefs

import android.content.SharedPreferences
import androidx.core.content.edit

sealed class PrefSpec<T : Any>(
    val key: String,
    val default: T,
) {
    abstract fun read(prefs: SharedPreferences): T

    abstract fun write(
        editor: SharedPreferences.Editor,
        value: T,
    )

    fun copyIfChanged(
        from: SharedPreferences,
        to: SharedPreferences,
        editor: SharedPreferences.Editor,
    ) {
        val value = read(from)
        if (read(to) != value) write(editor, value)
    }
}

class BoolPref(
    key: String,
    default: Boolean,
) : PrefSpec<Boolean>(key, default) {
    override fun read(prefs: SharedPreferences): Boolean = prefs.getBoolean(key, default)

    override fun write(
        editor: SharedPreferences.Editor,
        value: Boolean,
    ) {
        editor.putBoolean(key, value)
    }
}

class IntPref(
    key: String,
    default: Int,
) : PrefSpec<Int>(key, default) {
    override fun read(prefs: SharedPreferences): Int = prefs.getInt(key, default)

    override fun write(
        editor: SharedPreferences.Editor,
        value: Int,
    ) {
        editor.putInt(key, value)
    }
}

class StringPref(
    key: String,
    default: String,
) : PrefSpec<String>(key, default) {
    override fun read(prefs: SharedPreferences): String = prefs.getString(key, default) ?: default

    override fun write(
        editor: SharedPreferences.Editor,
        value: String,
    ) {
        editor.putString(key, value)
    }
}

class StringSetPref(
    key: String,
    default: Set<String>,
) : PrefSpec<Set<String>>(key, default) {
    override fun read(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(key, default) ?: default

    override fun write(
        editor: SharedPreferences.Editor,
        value: Set<String>,
    ) {
        editor.putStringSet(key, if (value.isEmpty()) null else HashSet(value))
    }
}

class PerPackage<out P : PrefSpec<*>>(
    val key: String,
    packages: Set<String>,
    make: (String) -> P,
) {
    val byPackage: Map<String, P> = packages.associateWith { make("$key@$it") }

    operator fun get(packageName: String): P = byPackage.getValue(packageName)

    fun packageOf(pref: PrefSpec<*>): String? =
        byPackage.entries.firstOrNull { it.value == pref }?.key

    fun migrate(prefs: SharedPreferences) {
        if (!prefs.contains(key)) return
        val legacy = prefs.all[key]
        prefs.edit {
            byPackage.values.filterNot { prefs.contains(it.key) }.forEach { pref ->
                when (legacy) {
                    is Boolean -> putBoolean(pref.key, legacy)
                    is String -> putString(pref.key, legacy)
                    is Set<*> -> putStringSet(pref.key, legacy.filterIsInstance<String>().toSet())
                }
            }
            remove(key)
        }
    }
}
