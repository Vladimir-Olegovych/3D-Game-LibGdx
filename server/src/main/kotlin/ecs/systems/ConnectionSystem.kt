package com.gigcreator.ecs.systems

import com.artemis.Aspect
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.annotation.BusEvent
import com.gigcreator.NetworkEvent
import com.gigcreator.core.congifs.ServerData
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.ecs.components.OwnerComponent
import com.gigcreator.ecs.states.ServerPlayerRegistry
import com.gigcreator.ecs.states.ServerTimeState
import com.gigcreator.event.ServerEvent
import com.gigcreator.network.SendType

internal class ConnectionSystem: BaseSystem() {

    @Wire
    private lateinit var serverData: ServerData
    @Wire
    private lateinit var playerRegistry: ServerPlayerRegistry
    @Wire
    private lateinit var serverTimeState: ServerTimeState

    private lateinit var clientMapper: ComponentMapper<ClientComponent>
    private lateinit var ownerMapper: ComponentMapper<OwnerComponent>

    @BusEvent
    fun onConnected(event: ServerEvent.OnConnected) {
        val connection = event.connection
        val entityId = world.create()

        val newClient = clientMapper.create(entityId).apply {
            this.connection = connection
        }

        playerRegistry.addPlayer(connection, entityId)

        // Transform/Observer/Owner добавит EntityStateSystem по первому состоянию клиента.
        newClient.addEvent(
            NetworkEvent.HelloFromServer(
                playerId = entityId,
                worldSeed = serverData.worldSeed,
                timeOfDay = serverTimeState.timeOfDay,
                cycleDuration = serverTimeState.cycleDuration,
            ),
            sendType = SendType.TCP
        )
    }

    @BusEvent
    fun onDisconnected(event: ServerEvent.OnDisconnected) {
        val connection = event.connection
        val entityId = playerRegistry.getEntityId(connection) ?: return


        val bag = world.aspectSubscriptionManager
            .get(Aspect.all(OwnerComponent::class.java))
            .entities
        val snapshot = IntArray(bag.size()) { i -> bag[i] }
        for (e in snapshot) {
            if (ownerMapper[e]?.owner == entityId) ownerMapper.remove(e)
        }

        playerRegistry.removePlayer(connection)
        world.delete(entityId)
    }

    override fun processSystem() {}
}