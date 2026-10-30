package com.gigcreator

class GamePacket(val events: Array<NetworkEvent> = emptyArray())

sealed class NetworkEvent {
    class HelloFromServer(
        val worldSeed: Int = 0,
        val timeOfDay: Float = 0.7f,
        val cycleDuration: Float = 600f,
    ): NetworkEvent()

    class EntityJoined(
        val entityId: Int = 0,
        val name: String = "",
        val pos: NetVector3 = NetVector3.ZERO,
        val modelId: Int = 0,
    ): NetworkEvent()

    class EntityLeft(
        val entityId: Int = 0,
    ): NetworkEvent()

    class EntityStateUpdate(
        val entityId: Int = 0,
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.ZERO,
        val modelId: Int = 0,
    ): NetworkEvent()
}
