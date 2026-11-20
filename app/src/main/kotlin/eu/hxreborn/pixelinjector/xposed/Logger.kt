package eu.hxreborn.pixelinjector.xposed

import android.util.Log
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.isInstallFailure

object Logger {
    const val TAG = ModuleConstants.LOG_TAG
    private val installFailures = ThreadLocal<MutableMap<String, String>>()

    internal fun <T> captureInstallFailures(
        failures: MutableMap<String, String>,
        install: () -> T,
    ): T {
        installFailures.set(failures)
        try {
            return install()
        } finally {
            installFailures.remove()
        }
    }

    fun log(
        level: Int,
        msg: String,
        t: Throwable? = null,
    ) = if (t != null) module.log(level, TAG, msg, t) else module.log(level, TAG, msg)

    fun info(msg: String) = module.log(Log.INFO, TAG, msg)

    fun warn(
        msg: String,
        t: Throwable? = null,
    ) = log(Log.WARN, msg, t)

    fun error(
        msg: String,
        t: Throwable? = null,
    ) {
        if (isInstallFailure(msg)) {
            val tweak = msg.substringAfter("tweak=").substringBefore(' ')
            installFailures.get()?.set(tweak, msg)
        }
        log(Log.ERROR, msg, t)
    }

    fun field(
        name: String,
        value: Any?,
    ): String =
        value
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { " $name=$it" }
            .orEmpty()

    inline fun debug(msg: () -> String) {
        if (BuildConfig.DEBUG) module.log(Log.DEBUG, TAG, msg())
    }
}
