package com.gigcreator.event

import com.esotericsoftware.kryonet.Connection
import com.gigcreator.NetworkEvent

sealed class ServerEvent {
    class OnConnected(val connection: Connection): ServerEvent()
    class OnDisconnected(val connection: Connection): ServerEvent()
    class OnReceived(val connection: Connection, val event: NetworkEvent): ServerEvent()
}