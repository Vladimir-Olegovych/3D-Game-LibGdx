package com.gigcreator.ecs.components

import com.artemis.Component
import com.esotericsoftware.kryonet.Connection
import com.gigcreator.NetworkEvent
import com.gigcreator.network.SendEvent
import com.gigcreator.network.SendType

class ClientComponent: Component() {
    var connection: Connection? = null

    private val events = ArrayList<SendEvent>()

    fun addEvent(event: NetworkEvent, sendType: SendType) {
        events.add(SendEvent(event, sendType))
    }

    fun getEvents(): List<SendEvent> = events

    fun clearEvents() {
        events.clear()
    }
}
