package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.annotation.BusEvent
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
        playerRegistry.connectionToEntity[connection.id] = entityId
        newClient.addEvent(
            NetworkEvent.HelloFromServer(
                worldSeed = serverData.worldSeed,
            ),
            sendType = SendType.TCP
        )
    }

    @BusEvent
    fun onDisconnected(event: ServerEvent.OnDisconnected) {
        val entityId = playerRegistry.connectionToEntity.remove(event.connection.id) ?: return
        world.delete(entityId)
    }

    override fun processSystem() {}

}
