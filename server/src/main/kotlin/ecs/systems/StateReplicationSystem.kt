package com.gigcreator.ecs.systems

import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.gigcreator.NetworkEvent
import com.gigcreator.core.chunk.ServerChunkManager
import com.gigcreator.core.ecs.canSee
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.ecs.components.ModelComponent
import com.gigcreator.ecs.components.OwnerComponent
import com.gigcreator.ecs.components.StateChangedComponent
import com.gigcreator.ecs.components.TransformComponent
import com.gigcreator.network.SendType

/**
 * Рассылает изменившиеся состояния (UDP) всем, кто видит чанк сущности, кроме её владельца.
 * Если чанк видят оба игрока — состояние уходит обоим (кроме того, чьё оно).
 *
 * Порядок систем: ... ServerChunkSystem -> StateReplicationSystem -> SendSystem.
 */
@All(TransformComponent::class, StateChangedComponent::class)
internal class StateReplicationSystem : IteratingSystem() {

    @Wire
    private lateinit var chunkManager: ServerChunkManager

    private lateinit var ownerMapper: ComponentMapper<OwnerComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var modelMapper: ComponentMapper<ModelComponent>
    private lateinit var clientMapper: ComponentMapper<ClientComponent>
    private lateinit var changedMapper: ComponentMapper<StateChangedComponent>

    override fun process(entityId: Int) {
        changedMapper.remove(entityId)

        val transform = transformMapper[entityId] ?: return
        val update = NetworkEvent.EntityStateUpdate(
            entityId = entityId,
            pos = transform.pos?: return,
            rot = transform.rot?: return,
            modelId = modelMapper[entityId]?.modelId ?: 0,
        )

        forEachRecipient(entityId) { observer ->
            clientMapper[observer]?.addEvent(update, sendType = SendType.UDP)
        }
    }


    fun forEachRecipient(entity: Int, action: (observer: Int) -> Unit) {
        val observers = chunkManager.getObservers(entity) ?: return
        for (i in 0 until observers.size()) {
            val observer = observers[i]
            if (ownerMapper.canSee(observer, entity)) action(observer)
        }
    }

}