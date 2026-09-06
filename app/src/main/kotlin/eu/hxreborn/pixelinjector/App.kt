package eu.hxreborn.pixelinjector

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.prefs.PrefsRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class App :
    Application(),
    XposedServiceHelper.OnServiceListener {
    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    private val _everBound = MutableStateFlow(false)
    val everBound: StateFlow<Boolean> = _everBound.asStateFlow()

    lateinit var prefs: PrefsRepository
        private set

    private val state by lazy { getSharedPreferences(STATE_PREFS, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder
                .create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(SHELL_TIMEOUT_SECONDS),
        )
        prefs =
            PrefsRepository(getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)) {
                runCatching { _service.value?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        _everBound.value = state.getBoolean(EVER_BOUND, false)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        Log.i(
            ModuleConstants.LOG_TAG,
            "service bound framework=${service.frameworkName} version=${service.frameworkVersion} api=${service.apiVersion}",
        )
        _service.value = service
        prefs.syncToRemote()
        if (!_everBound.value) {
            state.edit { putBoolean(EVER_BOUND, true) }
            _everBound.value = true
        }
    }

    override fun onServiceDied(service: XposedService) {
        Log.w(ModuleConstants.LOG_TAG, "service died")
        _service.value = null
    }

    companion object {
        private const val STATE_PREFS = "state"
        private const val EVER_BOUND = "everBound"
        private const val SHELL_TIMEOUT_SECONDS = 10L

        fun from(context: Context): App = context.applicationContext as App
    }
}
