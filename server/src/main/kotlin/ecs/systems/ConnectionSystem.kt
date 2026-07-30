package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.annotation.BusEvent
import com.gigcreator.NetEntityType
import com.gigcreator.NetVector3
import com.gigcreator.NetworkEvent
import com.gigcreator.core.congifs.ServerData
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.ecs.states.ServerPlayerRegistry
import com.gigcreator.event.ServerEvent
import com.gigcreator.network.SendType

class ConnectionSystem: BaseSystem() {

    @Wire
    private lateinit var serverData: ServerData
    @Wire
    private lateinit var playerRegistry: ServerPlayerRegistry

    private lateinit var clientMapper: ComponentMapper<ClientComponent>

    @BusEvent
    fun onConnected(event: ServerEvent.OnConnected) {
        val connection = event.connection
        val entityId = world.create()

        val newClient = clientMapper.create(entityId).apply {
            this.connection = connection
        }

        val playerId = playerRegistry.assignPlayer(connection.id, entityId)

        newClient.addEvent(
            NetworkEvent.HelloFromServer(
                worldSeed = serverData.worldSeed,
                playerId = playerId,
            ),
            sendType = SendType.TCP
        )

        for ((otherPlayerId, state) in playerRegistry.playerStates) {
            if (otherPlayerId == playerId) continue
            newClient.addEvent(
                NetworkEvent.EntityStateSnapshot(
                    entityId = otherPlayerId,
                    entityType = NetEntityType.PLAYER,
                    pos = state.pos,
                    rot = state.rot,
                    modelId = state.modelId,
                ),
                sendType = SendType.TCP
            )
        }

        broadcastExcept(
            senderConnectionId = connection.id,
            event = NetworkEvent.EntityJoined(
                entityId = playerId,
                entityType = NetEntityType.PLAYER,
                pos = NetVector3.ZERO,
                modelId = 0,
            ),
            sendType = SendType.TCP,
        )
    }

    @BusEvent
    fun onDisconnected(event: ServerEvent.OnDisconnected) {
        val removed = playerRegistry.removeByConnection(event.connection.id) ?: return
        val (playerId, entityId) = removed

        broadcast(
            NetworkEvent.EntityLeft(
                entityId = playerId,
                entityType = NetEntityType.PLAYER,
            ),
            SendType.TCP,
        )

        world.delete(entityId)
    }

    private fun broadcast(event: NetworkEvent, sendType: SendType) {
        for ((_, entityId) in playerRegistry.connectionToEntity) {
            clientMapper[entityId]?.addEvent(event, sendType)
        }
    }

    private fun broadcastExcept(senderConnectionId: Int, event: NetworkEvent, sendType: SendType) {
        for ((connectionId, entityId) in playerRegistry.connectionToEntity) {
            if (connectionId == senderConnectionId) continue
            clientMapper[entityId]?.addEvent(event, sendType)
        }
    }

    override fun processSystem() {}
}
