package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.eventbus.annotation.EventType
import com.gigcreator.NetEntityType
import com.gigcreator.NetVector3
import com.gigcreator.NetworkEvent
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.ecs.states.ServerPlayerRegistry
import com.gigcreator.event.ServerEvent
import com.gigcreator.network.SendType

class NetworkRelaySystem: BaseSystem() {

    @Wire
    private lateinit var playerRegistry: ServerPlayerRegistry

    private lateinit var clientMapper: ComponentMapper<ClientComponent>

    @BusEvent
    @EventType(NetworkEvent.EntityStateUpdate::class)
    fun onEntityStateUpdate(received: ServerEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityStateUpdate
        if (event.entityType != NetEntityType.PLAYER) return

        val playerId = playerRegistry.getPlayerIdByConnection(received.connection.id) ?: return
        if (playerId != event.entityId) return

        playerRegistry.updateState(playerId, event.pos, event.rot, event.modelId)
        broadcastExcept(received.connection.id, event, SendType.UDP)
    }

    private fun broadcastExcept(senderConnectionId: Int, event: NetworkEvent, sendType: SendType) {
        for ((connectionId, entityId) in playerRegistry.connectionToEntity) {
            if (connectionId == senderConnectionId) continue
            clientMapper[entityId]?.addEvent(event, sendType)
        }
    }

    override fun processSystem() {}
}
