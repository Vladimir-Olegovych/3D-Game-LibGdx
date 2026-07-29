package core.client

import app.feature.game.event.ClientEvent
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.gigapi.eventbus.EventBus
import com.gigcreator.GamePacket
import com.gigcreator.NetworkEvent

class ClientAcceptor(private val eventBus: EventBus): Listener() {

    override fun connected(connection: Connection) {
        eventBus.sendEvent(
            event = ClientEvent.OnConnected(connection)
        )
    }

    override fun disconnected(connection: Connection) {
        eventBus.sendEvent(
            event = ClientEvent.OnDisconnected(connection)
        )
    }

    override fun received(connection: Connection, obj: Any) {
        val gamePacket = (obj as? GamePacket) ?: return
        for (event in gamePacket.events) {
            eventBus.sendEvent(
                event = ClientEvent.OnReceived(connection, event),
                customType = event::class
            )
        }
    }
}