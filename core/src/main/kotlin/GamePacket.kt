package com.gigcreator

class GamePacket(val events: Array<NetworkEvent> = emptyArray())

sealed class NetworkEvent {
    class AcceptPlayer(val id: Int = 0): NetworkEvent()
}