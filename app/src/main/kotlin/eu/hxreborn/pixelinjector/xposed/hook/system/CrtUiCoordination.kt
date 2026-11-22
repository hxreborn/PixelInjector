package eu.hxreborn.pixelinjector.xposed.hook.system

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import eu.hxreborn.pixelinjector.crtipc.CrtConnection
import eu.hxreborn.pixelinjector.crtipc.CrtProtocol
import eu.hxreborn.pixelinjector.xposed.Logger

internal class CrtUiCoordination(
    private val context: Context,
    private val samsungAodSetting: Boolean,
    private val revealReady: () -> Boolean,
    private val releaseReveal: ((() -> Unit)?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val connection =
        CrtConnection(
            context,
            CrtProtocol.SYSTEM_UI,
            ::onEvent,
            ::observeAlwaysOn,
            ::stopObservingAlwaysOn,
        )

    @Volatile private var alwaysOnSetting: Boolean? = null
    private val alwaysOnLock = Any()
    private var alwaysOnGeneration = 0L
    private val alwaysOnObserver =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                synchronized(alwaysOnLock) {
                    alwaysOnGeneration++
                    alwaysOnSetting = null
                }
                connection.dispatch(::readAlwaysOn)
            }
        }
    private var connected = false
    private var generation = 0L
    private var pending = false
    private var granted = false
    private var started = false
    private var finished = false

    @Volatile private var closed = false
    private val callbackToken = Any()

    fun start() {
        connection.start()
    }

    fun alwaysOnSetting(): Boolean? = alwaysOnSetting

    private fun observeAlwaysOn() {
        try {
            val uri =
                if (samsungAodSetting) {
                    Settings.System.getUriFor(SAMSUNG_AOD_MODE)
                } else {
                    Settings.Secure.getUriFor(DOZE_ALWAYS_ON)
                }
            context.contentResolver.registerContentObserver(
                uri,
                false,
                alwaysOnObserver,
            )
            readAlwaysOn()
        } catch (e: Exception) {
            Logger.warn("crt aod setting observer failed tweak=CrtScreenOff reason=${e.message}")
        } catch (e: LinkageError) {
            Logger.warn("crt aod setting observer failed tweak=CrtScreenOff reason=${e.message}")
        }
    }

    private fun stopObservingAlwaysOn() {
        try {
            context.contentResolver.unregisterContentObserver(alwaysOnObserver)
        } catch (e: Exception) {
            Logger.warn(
                "crt aod setting observer removal failed tweak=CrtScreenOff reason=${e.message}",
            )
        } catch (e: LinkageError) {
            Logger.warn(
                "crt aod setting observer removal failed tweak=CrtScreenOff reason=${e.message}",
            )
        }
    }

    private fun readAlwaysOn() {
        val generation = synchronized(alwaysOnLock) { alwaysOnGeneration }
        try {
            val resolver = context.contentResolver
            val enabled =
                if (samsungAodSetting) {
                    Settings.System.getInt(resolver, SAMSUNG_AOD_MODE, 1) != 0
                } else {
                    Settings.Secure.getInt(resolver, DOZE_ALWAYS_ON, 1) != 0
                }
            synchronized(alwaysOnLock) {
                if (alwaysOnGeneration == generation) alwaysOnSetting = enabled
            }
        } catch (e: Exception) {
            alwaysOnSetting = null
            Logger.warn("crt aod setting read failed tweak=CrtScreenOff reason=${e.message}")
        } catch (e: LinkageError) {
            alwaysOnSetting = null
            Logger.warn("crt aod setting read failed tweak=CrtScreenOff reason=${e.message}")
        }
    }

    fun arm(): Boolean {
        if (closed || !connected) return false
        generation = SystemClock.elapsedRealtimeNanos()
        pending = true
        granted = false
        started = false
        finished = false
        connection.send(CrtProtocol.ARMED, generation)
        Logger.info("crt aod armed tweak=CrtScreenOff generation=$generation")
        val current = generation
        main.postAtTime({
            if (pending && generation == current && !granted) finish(false)
        }, callbackToken, SystemClock.uptimeMillis() + START_TIMEOUT_MS)
        return true
    }

    fun cancel() {
        if (!pending) return
        val current = generation
        pending = false
        granted = false
        started = false
        finished = false
        connection.send(CrtProtocol.FAILED, current)
    }

    fun heldAmountChanged() {
        if (!pending || !finished) return
        main.post {
            if (pending && finished && revealReady()) finish(true)
        }
    }

    fun close() {
        closed = true
        connected = false
        main.removeCallbacksAndMessages(callbackToken)
        cancel()
        connection.close()
    }

    fun isClosed(): Boolean = closed

    fun whenStopped(action: () -> Unit) = connection.whenStopped(action)

    private fun onEvent(
        event: Int,
        incoming: Long,
        value: Long,
    ) {
        main.post {
            if (closed) return@post
            Logger.info("crt aod event tweak=CrtScreenOff event=$event generation=$incoming")
            when (event) {
                CrtProtocol.PEER_CONNECTED -> {
                    if (connected && pending) finish(false)
                    connected = true
                }

                CrtProtocol.PEER_DISCONNECTED -> {
                    connected = false
                    if (pending) finish(false)
                }

                CrtProtocol.START_REQUEST -> {
                    if (pending && generation == incoming && !granted) {
                        granted = true
                        connection.send(CrtProtocol.START_GRANTED, incoming)
                        main.postAtTime({
                            if (pending && generation == incoming && !started) finish(false)
                        }, callbackToken, SystemClock.uptimeMillis() + START_TIMEOUT_MS)
                    }
                }

                CrtProtocol.STARTED -> {
                    if (pending && generation == incoming && granted && !started) {
                        started = true
                        val timeout =
                            (CRT_DURATION_MS * ValueAnimator.getDurationScale()).toLong() +
                                FINISH_GRACE_MS
                        main.postAtTime({
                            if (pending && generation == incoming) finish(false)
                        }, callbackToken, SystemClock.uptimeMillis() + timeout)
                    }
                }

                CrtProtocol.FINISHED -> {
                    if (pending && generation == incoming) {
                        finished = true
                        if (revealReady()) finish(true)
                    }
                }

                CrtProtocol.FAILED -> {
                    if (pending && generation == incoming) finish(false)
                }
            }
        }
    }

    private fun finish(completed: Boolean) {
        val current = generation
        pending = false
        granted = false
        started = false
        finished = false
        val notify = {
            if (!closed) {
                connection.send(
                    if (completed) CrtProtocol.RELEASED else CrtProtocol.FAILED,
                    current,
                )
            }
        }
        releaseReveal(notify)
        Logger.info(
            "crt aod reveal released tweak=CrtScreenOff completed=$completed generation=$current",
        )
    }

    private companion object {
        const val DOZE_ALWAYS_ON = "doze_always_on"
        const val SAMSUNG_AOD_MODE = "aod_mode"
        const val START_TIMEOUT_MS = 300L
        const val CRT_DURATION_MS = 500L
        const val FINISH_GRACE_MS = 2_000L
    }
}
