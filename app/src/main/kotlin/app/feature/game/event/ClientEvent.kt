package app.feature.game.event

import com.esotericsoftware.kryonet.Connection
import com.gigcreator.NetworkEvent

sealed class ClientEvent {
    class OnError(val connection: Connection): ClientEvent()
    class OnConnected(val connection: Connection): ClientEvent()
    class OnDisconnected(val connection: Connection): ClientEvent()
    class OnReceived(val connection: Connection, val event: NetworkEvent): ClientEvent()
}