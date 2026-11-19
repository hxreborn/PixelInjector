package eu.hxreborn.pixelinjector

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.prefs.PrefsRepository
import eu.hxreborn.pixelinjector.support.GeckoWatch
import eu.hxreborn.pixelinjector.support.Support
import eu.hxreborn.pixelinjector.ui.util.DateStyle
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

    private val _hideUnavailable = MutableStateFlow(false)
    val hideUnavailable: StateFlow<Boolean> = _hideUnavailable.asStateFlow()

    private val _showPackages = MutableStateFlow(false)
    val showPackages: StateFlow<Boolean> = _showPackages.asStateFlow()

    private val _useSystemFont = MutableStateFlow(false)
    val useSystemFont: StateFlow<Boolean> = _useSystemFont.asStateFlow()

    private val _dateStyle = MutableStateFlow(DateStyle.SYSTEM)
    val dateStyle: StateFlow<DateStyle> = _dateStyle.asStateFlow()

    lateinit var prefs: PrefsRepository
        private set

    private val state by lazy { getSharedPreferences(STATE_PREFS, MODE_PRIVATE) }

    val support: Support by lazy { Support(this) }

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder
                .create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(SHELL_TIMEOUT_SECONDS),
        )
        val local = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)
        Prefs.perBrowser.forEach { it.migrate(local) }
        prefs =
            PrefsRepository(local) {
                runCatching { _service.value?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        _everBound.value = state.getBoolean(EVER_BOUND, false)
        _hideUnavailable.value = state.getBoolean(HIDE_UNAVAILABLE, false)
        _showPackages.value = state.getBoolean(SHOW_PACKAGES, false)
        _useSystemFont.value = state.getBoolean(USE_SYSTEM_FONT, false)
        _dateStyle.value =
            DateStyle.entries.firstOrNull { it.name == state.getString(DATE_STYLE, null) }
                ?: DateStyle.SYSTEM
        XposedServiceHelper.registerListener(this)
        GeckoWatch.schedule(this)
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

    fun setHideUnavailable(hide: Boolean) {
        state.edit { putBoolean(HIDE_UNAVAILABLE, hide) }
        _hideUnavailable.value = hide
    }

    fun setShowPackages(show: Boolean) {
        state.edit { putBoolean(SHOW_PACKAGES, show) }
        _showPackages.value = show
    }

    fun setUseSystemFont(enabled: Boolean) {
        state.edit { putBoolean(USE_SYSTEM_FONT, enabled) }
        _useSystemFont.value = enabled
    }

    fun setDateStyle(style: DateStyle) {
        state.edit { putString(DATE_STYLE, style.name) }
        _dateStyle.value = style
    }

    override fun onServiceDied(service: XposedService) {
        Log.w(ModuleConstants.LOG_TAG, "service died")
        _service.value = null
    }

    companion object {
        private const val STATE_PREFS = "state"
        private const val EVER_BOUND = "everBound"
        private const val HIDE_UNAVAILABLE = "hideUnavailable"
        private const val SHOW_PACKAGES = "showPackages"
        private const val USE_SYSTEM_FONT = "useSystemFont"
        private const val DATE_STYLE = "dateStyle"
        private const val SHELL_TIMEOUT_SECONDS = 10L

        fun from(context: Context): App = context.applicationContext as App
    }
}
