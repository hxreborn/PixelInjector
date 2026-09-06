package eu.hxreborn.pixelinjector.prefs

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import eu.hxreborn.pixelinjector.ModuleConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PrefsRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences?,
) {
    val state: Flow<AppPrefs> =
        callbackFlow {
            fun sendState() = trySend(snapshot())
            sendState()
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener {
                    _,
                    _,
                    ->
                    sendState()
                }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        local.edit { pref.write(this, value) }
        val remote = remoteProvider() ?: return
        runCatching { remote.edit { pref.write(this, value) } }.onFailure {
            Log.w(
                ModuleConstants.LOG_TAG,
                "remote prefs push failed reason=${it.message}",
                it,
            )
        }
    }

    fun syncToRemote() {
        val remote = remoteProvider() ?: return
        runCatching {
            remote.edit {
                Prefs.all.forEach {
                    it.copyIfChanged(
                        local,
                        remote,
                        this,
                    )
                }
            }
        }.onFailure {
            Log.w(
                ModuleConstants.LOG_TAG,
                "remote prefs sync failed reason=${it.message}",
                it,
            )
        }
    }

    fun snapshot(): AppPrefs = AppPrefs(Prefs.all.associate { it.key to it.read(local) })
}

class AppPrefs(
    private val values: Map<String, Any> = emptyMap(),
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(spec: PrefSpec<T>): T = values[spec.key] as T? ?: spec.default

    override fun equals(other: Any?): Boolean = other is AppPrefs && other.values == values

    override fun hashCode(): Int = values.hashCode()
}
