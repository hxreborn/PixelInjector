package eu.hxreborn.pixelinjector.crtipc

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class CrtConnection(
    private val context: Context,
    private val role: Int,
    private val onEvent: (Int, Long, Long) -> Unit,
    private val onStart: () -> Unit = {},
    private val onStop: () -> Unit = {},
) {
    private val uri =
        Uri
            .Builder()
            .scheme("content")
            .authority(CrtProtocol.AUTHORITY)
            .build()
    private val queue = LinkedBlockingQueue<() -> Unit>()

    @Volatile private var brokerUid = -1

    @Volatile private var closed = false
    private var relay: IBinder? = null
    private var death: IBinder.DeathRecipient? = null
    private var stopWaiterStarted = false
    private val callback =
        object : Binder() {
            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code != CrtProtocol.EVENT) return super.onTransact(code, data, reply, flags)
                if (closed || getCallingUid() != brokerUid) return false
                val event = data.readInt()
                val generation = data.readLong()
                val value = data.readLong()
                queue.offer { if (!closed) onEvent(event, generation, value) }
                return true
            }
        }
    private val worker =
        thread(start = false, name = "PixelInjector-CrtConnection") {
            try {
                onStart()
                while (!closed) {
                    if (relay == null) connect()
                    val action =
                        if (relay ==
                            null
                        ) {
                            queue.poll(RETRY_MS, TimeUnit.MILLISECONDS)
                        } else {
                            queue.take()
                        }
                    try {
                        if (closed) break
                        action?.invoke()
                    } catch (e: Exception) {
                        Log.w("PixelInjector", "crt connection event failed", e)
                    } catch (e: LinkageError) {
                        Log.w("PixelInjector", "crt connection event failed", e)
                    }
                }
            } catch (_: InterruptedException) {
            } finally {
                try {
                    onStop()
                } finally {
                    unregister()
                }
            }
        }

    fun start() = worker.start()

    fun dispatch(action: () -> Unit) {
        if (!closed) queue.offer(action)
    }

    fun send(
        event: Int,
        generation: Long,
        value: Long = 0,
    ) {
        if (closed) return
        queue.offer {
            val service = relay ?: return@offer
            val data = Parcel.obtain()
            try {
                data.writeInt(role)
                data.writeStrongBinder(callback)
                data.writeInt(event)
                data.writeLong(generation)
                data.writeLong(value)
                service.transact(CrtProtocol.EVENT, data, null, IBinder.FLAG_ONEWAY)
            } catch (e: RemoteException) {
                Log.w("PixelInjector", "crt connection send failed", e)
                disconnect(service)
            } finally {
                data.recycle()
            }
        }
    }

    fun close() {
        closed = true
        queue.clear()
        worker.interrupt()
        worker.join(CLOSE_TIMEOUT_MS)
        if (worker.isAlive) {
            val cause = IllegalStateException("CRT connection did not stop")
            cause.stackTrace = worker.stackTrace
            throw cause
        }
    }

    fun whenStopped(action: () -> Unit) {
        val stopped =
            synchronized(this) {
                if (!worker.isAlive) {
                    true
                } else {
                    if (stopWaiterStarted) return
                    stopWaiterStarted = true
                    false
                }
            }
        if (stopped) return action()
        thread(name = "PixelInjector-CrtConnectionStop") {
            worker.join()
            action()
        }
    }

    private fun connect() {
        try {
            brokerUid = context.packageManager.getPackageUid("eu.hxreborn.pixelinjector", 0)
            val extras =
                Bundle().apply {
                    putInt(CrtProtocol.ROLE, role)
                    putBinder(CrtProtocol.CALLBACK, callback)
                }
            val result = context.contentResolver.call(uri, CrtProtocol.REGISTER, null, extras)
            val service = result?.getBinder(CrtProtocol.RELAY) ?: return
            val recipient = IBinder.DeathRecipient { queue.offer { disconnect(service) } }
            service.linkToDeath(recipient, 0)
            relay = service
            death = recipient
        } catch (e: Exception) {
            Log.w("PixelInjector", "crt connection failed", e)
        }
    }

    private fun disconnect(service: IBinder) {
        if (relay !== service) return
        death?.let { service.unlinkToDeath(it, 0) }
        relay = null
        death = null
        onEvent(CrtProtocol.PEER_DISCONNECTED, 0, 0)
    }

    private fun unregister() {
        val service = relay ?: return
        val data = Parcel.obtain()
        try {
            data.writeInt(role)
            data.writeStrongBinder(callback)
            service.transact(CrtProtocol.DISCONNECT, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            Log.w("PixelInjector", "crt connection unregister failed", e)
        } finally {
            data.recycle()
            death?.let { service.unlinkToDeath(it, 0) }
            relay = null
            death = null
        }
    }

    private companion object {
        const val RETRY_MS = 1_000L
        const val CLOSE_TIMEOUT_MS = 1_000L
    }
}
