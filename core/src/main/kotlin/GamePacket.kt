package com.gigcreator

class GamePacket(val events: Array<NetworkEvent> = emptyArray())

sealed class NetworkEvent {
    class HelloFromServer(val worldSeed: Int = 0): NetworkEvent()
}