package com.gigcreator

class GamePacket(val events: Array<NetworkEvent> = emptyArray())

sealed class NetworkEvent {
    class HelloFromServer(val worldSeed: Int = 0): NetworkEvent()

    class PlayerInput(
        val playerId: Int = 0,
        val moveDir: NetVector3 = NetVector3.ZERO,
        val yaw: Float = 0f,
        val pitch: Float = 0f,
        val jump: Boolean = false
    ): NetworkEvent()

    class PlayerStateUpdate(
        val playerId: Int = 0,
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.ZERO,
        val tick: Long = 0
    ): NetworkEvent()

    class BlockPlace(
        val pos: NetVector3 = NetVector3.ZERO,
        val blockId: Int = 0
    ): NetworkEvent()

    class BlockBreak(
        val pos: NetVector3 = NetVector3.ZERO
    ): NetworkEvent()



    class PlayerJoined(
        val playerId: Int = 0,
        val name: String = "",
        val pos: NetVector3 = NetVector3.ZERO
    ): NetworkEvent()

    class PlayerLeft(
        val playerId: Int = 0
    ): NetworkEvent()

    class PlayerStateSnapshot(
        val playerId: Int = 0,
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.ZERO
    ): NetworkEvent()

    class BlockChanged(
        val pos: NetVector3 = NetVector3.ZERO,
        val blockId: Int = 0
    ): NetworkEvent()
}
