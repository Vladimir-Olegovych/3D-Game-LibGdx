package com.gigcreator

import com.esotericsoftware.kryo.Kryo

fun Kryo.registerAllEvents() {
    val kryo = this
    kryo.register(GamePacket::class.java)
    kryo.register(NetworkEvent::class.java)
    kryo.register(Array<NetworkEvent>::class.java)
    kryo.register(NetworkEvent.HelloFromServer::class.java)
}