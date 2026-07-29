package com.gigcreator.event

import com.esotericsoftware.kryonet.Connection

sealed class ServerEvent {
    class OnConnected(val connection: Connection): ServerEvent()
    class OnDisconnected(val connection: Connection): ServerEvent()
    class OnReceived(val connection: Connection, val obj: Any): ServerEvent()
}