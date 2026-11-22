package eu.hxreborn.pixelinjector.xposed.hook.system

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import eu.hxreborn.pixelinjector.crtipc.CrtConnection
import eu.hxreborn.pixelinjector.crtipc.CrtProtocol
import eu.hxreborn.pixelinjector.xposed.Logger

internal class CrtSystemCoordination(
    context: Context,
    private val displayHandler: Handler,
    private val update: () -> Unit,
) {
    private val connection = CrtConnection(context, CrtProtocol.SYSTEM, ::onEvent)
    private var connected = false
    private var armed = 0L
    private var armedTime = 0L

    @Volatile private var requested = 0L
    private var granted = 0L

    @Volatile private var active = 0L
    private var released = false
    private var failurePending = false

    @Volatile private var closed = false
    private val callbackToken = Any()

    fun start() = connection.start()

    fun armedGeneration(): Long =
        if (!closed && connected && SystemClock.uptimeMillis() - armedTime < ARM_MAX_AGE_MS) {
            armed
        } else {
            0L
        }

    fun requestStart(generation: Long) {
        if (requested == generation) return
        requested = generation
        connection.send(CrtProtocol.START_REQUEST, generation)
        displayHandler.postAtTime({
            if (!closed && requested == generation && granted != generation) {
                failed()
                update()
            }
        }, callbackToken, SystemClock.uptimeMillis() + START_REQUEST_TIMEOUT_MS)
    }

    fun isGranted(generation: Long): Boolean =
        generation != 0L && granted == generation && armedGeneration() == generation

    fun isTransitionPending(): Boolean = requested != 0L || active != 0L

    fun isDisplayThread(): Boolean = displayHandler.looper.isCurrentThread

    fun started(generation: Long) {
        active = generation
        armed = 0
        requested = 0
        granted = 0
        released = false
        failurePending = false
        connection.send(CrtProtocol.STARTED, generation)
        Logger.info("crt aod started tweak=CrtScreenOff generation=$generation")
    }

    fun failed() {
        val generation = if (active != 0L) active else armed
        if (generation != 0L) connection.send(CrtProtocol.FAILED, generation)
        active = 0
        armed = 0
        requested = 0
        granted = 0
        released = true
        failurePending = false
        if (generation !=
            0L
        ) {
            Logger.info("crt aod failed tweak=CrtScreenOff generation=$generation")
        }
    }

    fun finished() {
        val generation = active
        if (generation == 0L || failurePending) return
        connection.send(CrtProtocol.FINISHED, generation)
        Logger.info("crt aod finished tweak=CrtScreenOff generation=$generation")
        waitForRelease(generation)
    }

    fun failedAfterBlack() {
        val generation = active
        if (generation == 0L) return failed()
        failurePending = true
        connection.send(CrtProtocol.FAILED, generation)
        waitForRelease(generation)
    }

    private fun waitForRelease(generation: Long) {
        displayHandler.postAtTime({
            if (!closed && active == generation && !released) {
                failed()
                update()
            }
        }, callbackToken, SystemClock.uptimeMillis() + RELEASE_TIMEOUT_MS)
    }

    fun isReleased(): Boolean = released

    fun reset() {
        active = 0
        armed = 0
        requested = 0
        granted = 0
        released = false
        failurePending = false
    }

    fun close() {
        closed = true
        try {
            connection.close()
        } finally {
            displayHandler.removeCallbacksAndMessages(callbackToken)
        }
    }

    fun isClosed(): Boolean = closed

    fun whenStopped(action: () -> Unit) = connection.whenStopped(action)

    private fun onEvent(
        event: Int,
        generation: Long,
        value: Long,
    ) {
        displayHandler.postAtTime({
            if (closed) return@postAtTime
            Logger.info("crt aod event tweak=CrtScreenOff event=$event generation=$generation")
            when (event) {
                CrtProtocol.PEER_CONNECTED -> {
                    if (connected) {
                        failed()
                        update()
                    }
                    connected = true
                }

                CrtProtocol.PEER_DISCONNECTED -> {
                    connected = false
                    failed()
                    update()
                }

                CrtProtocol.ARMED -> {
                    if (connected) {
                        armed = generation
                        armedTime = SystemClock.uptimeMillis()
                        update()
                    }
                }

                CrtProtocol.START_GRANTED -> {
                    if (generation == requested) {
                        granted = generation
                        update()
                    }
                }

                CrtProtocol.RELEASED -> {
                    if (generation == active) {
                        released = true
                        update()
                    }
                }

                CrtProtocol.FAILED -> {
                    if (generation == active || generation == armed) {
                        failed()
                        update()
                    }
                }
            }
        }, callbackToken, SystemClock.uptimeMillis())
    }

    private companion object {
        const val ARM_MAX_AGE_MS = 300L
        const val START_REQUEST_TIMEOUT_MS = 200L
        const val RELEASE_TIMEOUT_MS = 500L
    }
}
