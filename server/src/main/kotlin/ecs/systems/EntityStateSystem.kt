package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.eventbus.annotation.EventType
import com.gigcreator.NetworkEvent
import com.gigcreator.core.congifs.ServerData
import com.gigcreator.ecs.components.*
import com.gigcreator.ecs.states.ServerPlayerRegistry
import com.gigcreator.event.ServerEvent

internal class EntityStateSystem : BaseSystem() {

    @Wire
    private lateinit var playerRegistry: ServerPlayerRegistry
    @Wire
    private lateinit var serverData: ServerData

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var modelMapper: ComponentMapper<ModelComponent>
    private lateinit var observerMapper: ComponentMapper<ObserverComponent>
    private lateinit var ownerMapper: ComponentMapper<OwnerComponent>
    private lateinit var changedMapper: ComponentMapper<StateChangedComponent>

    @BusEvent
    @EventType(NetworkEvent.EntityStateUpdate::class)
    fun onEntityStateUpdate(received: ServerEvent.OnReceived) {
        val sender = playerRegistry.getEntityId(received.connection) ?: return
        val update = received.event as NetworkEvent.EntityStateUpdate
        val target = update.entityId

        if (target == sender) {
            if (!transformMapper.has(sender)) enterWorld(sender)
        } else if (ownerMapper[target]?.owner != sender) {
            return
        }

        val transform = transformMapper[target] ?: return
        transform.pos = update.pos
        transform.rot = update.rot

        val model = modelMapper[target] ?: modelMapper.create(target)
        model.modelId = update.modelId

        if (!changedMapper.has(target)) changedMapper.create(target)
    }

    /** Первое состояние клиента: сущность получает позицию, становится наблюдателем и своим владельцем. */
    private fun enterWorld(entityId: Int) {
        transformMapper.create(entityId)
        observerMapper.create(entityId).apply {
            radiusXZ = serverData.radiusXZ
            radiusY = serverData.radiusY
        }
        ownerMapper.create(entityId).owner = entityId
        if (!modelMapper.has(entityId)) modelMapper.create(entityId)
    }

    override fun processSystem() {}
}