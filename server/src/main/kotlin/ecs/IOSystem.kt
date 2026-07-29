package com.gigcreator.ecs

import com.artemis.BaseSystem
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.eventbus.annotation.EventType
import com.gigcreator.NetworkEvent
import com.gigcreator.event.ServerEvent

class IOSystem: BaseSystem() {

    override fun processSystem() {

    }

    @BusEvent
    @EventType(NetworkEvent.HelloFromServer::class)
    fun networkEventReceivedAcceptPlayer(received: ServerEvent.OnReceived) {
        val event = received.event as NetworkEvent.HelloFromServer

    }
}