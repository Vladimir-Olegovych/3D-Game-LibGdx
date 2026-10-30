package com.gigcreator

import com.esotericsoftware.kryo.Kryo

const val KRYO_CLIENT_BUFFER_SIZE =  1024 * 64
const val KRYO_SERVER_BUFFER_SIZE = 1024 * 64

fun Kryo.registerAllEvents() {
    register(GamePacket::class.java)
    register(NetworkEvent::class.java)
    register(Array<NetworkEvent>::class.java)
    register(NetVector3::class.java)
    register(NetQuaternion::class.java)

    register(NetworkEvent.HelloFromServer::class.java)
    register(NetworkEvent.EntityJoined::class.java)
    register(NetworkEvent.EntityLeft::class.java)
    register(NetworkEvent.EntityStateUpdate::class.java)

    register(NetworkEvent.SpawnRequest::class.java)
    register(NetworkEvent.EntityAssigned::class.java)
}
