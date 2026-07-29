package core.client

import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.gigapi.eventbus.EventBus
import com.gigcreator.GamePacket
import com.gigcreator.NetworkEvent

class ClientAcceptor(private val eventBus: EventBus): Listener() {
    override fun connected(connection: Connection) {
        println("connected")
        connection.sendTCP(
            GamePacket(
                arrayOf(NetworkEvent.AcceptPlayer(0)))
        )
    }

    override fun disconnected(connection: Connection) {
        println("disconnected")
    }

    override fun received(connection: Connection, obj: Any) {
        println("received ${obj::class.java.name}")
    }
}