package eu.hxreborn.pixelinjector.crtipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process

internal object CrtProtocol {
    const val AUTHORITY = "eu.hxreborn.pixelinjector.crt"
    const val REGISTER = "register"
    const val UNREGISTER = "unregister"
    const val ROLE = "role"
    const val CALLBACK = "callback"
    const val RELAY = "relay"
    const val SYSTEM = 1
    const val SYSTEM_UI = 2
    const val EVENT = IBinder.FIRST_CALL_TRANSACTION
    const val DISCONNECT = IBinder.FIRST_CALL_TRANSACTION + 1
    const val PEER_CONNECTED = 1
    const val PEER_DISCONNECTED = 2
    const val ARMED = 4
    const val STARTED = 5
    const val FINISHED = 6
    const val FAILED = 7
    const val RELEASED = 8
    const val START_REQUEST = 9
    const val START_GRANTED = 10
}

class CrtBroker : ContentProvider() {
    private data class Client(
        val uid: Int,
        val callback: IBinder,
        val death: IBinder.DeathRecipient,
    )

    private val clients = mutableMapOf<Int, Client>()
    private val relay =
        object : Binder() {
            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code != CrtProtocol.EVENT && code != CrtProtocol.DISCONNECT) {
                    return super.onTransact(code, data, reply, flags)
                }
                val role = data.readInt()
                val callback = data.readStrongBinder()
                synchronized(clients) {
                    val client = clients[role]
                    if (client?.uid != getCallingUid() || client.callback != callback) {
                        return false
                    }
                    if (code == CrtProtocol.DISCONNECT) {
                        remove(role, client.callback)
                    } else {
                        val event = data.readInt()
                        val generation = data.readLong()
                        val value = data.readLong()
                        clients[peerOf(role)]?.callback?.let { send(it, event, generation, value) }
                    }
                }
                return true
            }
        }

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val role = extras?.getInt(CrtProtocol.ROLE) ?: return null
        val uid = Binder.getCallingUid()
        if (!authorized(role, uid)) return null
        when (method) {
            CrtProtocol.REGISTER -> {
                val callback = extras.getBinder(CrtProtocol.CALLBACK) ?: return null
                val death = IBinder.DeathRecipient { remove(role, callback) }
                synchronized(clients) {
                    callback.linkToDeath(death, 0)
                    if (!callback.isBinderAlive) return null
                    val previous = clients.put(role, Client(uid, callback, death))
                    previous?.callback?.unlinkToDeath(previous.death, 0)
                    val peer = clients[peerOf(role)]?.callback
                    if (peer != null) {
                        send(peer, CrtProtocol.PEER_CONNECTED, 0, 0)
                        send(callback, CrtProtocol.PEER_CONNECTED, 0, 0)
                    }
                }
                return Bundle().apply { putBinder(CrtProtocol.RELAY, relay) }
            }

            CrtProtocol.UNREGISTER -> {
                val callback = extras.getBinder(CrtProtocol.CALLBACK) ?: return null
                remove(role, callback)
                return Bundle.EMPTY
            }

            else -> {
                return null
            }
        }
    }

    private fun authorized(
        role: Int,
        uid: Int,
    ): Boolean =
        when (role) {
            CrtProtocol.SYSTEM -> {
                uid == Process.SYSTEM_UID
            }

            CrtProtocol.SYSTEM_UI -> {
                val pm = context?.packageManager ?: return false
                try {
                    uid == pm.getPackageUid("com.android.systemui", 0)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    false
                }
            }

            else -> {
                false
            }
        }

    private fun remove(
        role: Int,
        callback: IBinder,
    ) {
        synchronized(clients) {
            val existing = clients[role]
            if (existing?.callback != callback) return
            clients.remove(role)
            existing.callback.unlinkToDeath(existing.death, 0)
            clients[peerOf(role)]?.callback?.let {
                send(it, CrtProtocol.PEER_DISCONNECTED, 0, 0)
            }
        }
    }

    private fun peerOf(role: Int): Int =
        if (role == CrtProtocol.SYSTEM) CrtProtocol.SYSTEM_UI else CrtProtocol.SYSTEM

    private fun send(
        callback: IBinder,
        event: Int,
        generation: Long,
        value: Long,
    ) {
        val data = Parcel.obtain()
        val identity = Binder.clearCallingIdentity()
        try {
            data.writeInt(event)
            data.writeLong(generation)
            data.writeLong(value)
            callback.transact(CrtProtocol.EVENT, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: android.os.RemoteException) {
        } finally {
            Binder.restoreCallingIdentity(identity)
            data.recycle()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
