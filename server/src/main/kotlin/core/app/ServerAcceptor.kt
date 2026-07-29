package com.gigcreator.core.app

import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.gigapi.eventbus.EventBus
import com.gigcreator.GamePacket
import com.gigcreator.event.ServerEvent

class ServerAcceptor(
    private val eventBus: EventBus
): Listener() {
    override fun connected(connection: Connection) {
        eventBus.sendEvent(
            event = ServerEvent.OnConnected(connection)
        )
    }

    override fun disconnected(connection: Connection) {
        eventBus.sendEvent(
            event = ServerEvent.OnDisconnected(connection)
        )
    }

    override fun received(connection: Connection, obj: Any) {
        val gamePacket = (obj as? GamePacket) ?: return
        /*
        eventBus.sendEvent(
            event = ServerEvent.OnReceived(connection, obj),
            customType = obj::class
        )
         */
    }
}