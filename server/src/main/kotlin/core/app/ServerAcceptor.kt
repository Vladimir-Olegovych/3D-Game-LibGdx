package com.gigcreator.core.app

import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.gigapi.eventbus.EventBus
import com.gigapi.storage.json.AppConfig
import com.gigcreator.GamePacket
import com.gigcreator.NetworkEvent
import com.gigcreator.core.congifs.ServerData
import com.gigcreator.event.ServerEvent

class ServerAcceptor(
    private val eventBus: EventBus,
    private val serverData: ServerData
): Listener() {
    override fun connected(connection: Connection) {
        eventBus.sendEvent(
            event = ServerEvent.OnConnected(connection)
        )
        connection.sendTCP(GamePacket(arrayOf(
            NetworkEvent.HelloFromServer(
                worldSeed = serverData.worldSeed
            )
        )))
    }

    override fun disconnected(connection: Connection) {
        eventBus.sendEvent(
            event = ServerEvent.OnDisconnected(connection)
        )
    }

    override fun received(connection: Connection, obj: Any) {
        val gamePacket = (obj as? GamePacket) ?: return
        for (event in gamePacket.events) {
            eventBus.sendEvent(
                event = ServerEvent.OnReceived(connection, event),
                customType = event::class
            )
        }
    }
}