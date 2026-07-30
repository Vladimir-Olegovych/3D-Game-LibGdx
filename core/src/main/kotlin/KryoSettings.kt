package com.gigcreator

import com.esotericsoftware.kryo.Kryo

fun Kryo.registerAllEvents() {
    register(GamePacket::class.java)
    register(NetworkEvent::class.java)
    register(Array<NetworkEvent>::class.java)
    register(NetVector3::class.java)
    register(NetQuaternion::class.java)

    register(NetworkEvent.HelloFromServer::class.java)
    register(NetworkEvent.PlayerInput::class.java)
    register(NetworkEvent.PlayerStateUpdate::class.java)
    register(NetworkEvent.BlockPlace::class.java)
    register(NetworkEvent.BlockBreak::class.java)
    register(NetworkEvent.PlayerJoined::class.java)
    register(NetworkEvent.PlayerLeft::class.java)
    register(NetworkEvent.PlayerStateSnapshot::class.java)
    register(NetworkEvent.BlockChanged::class.java)
}
