package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.gigapi.eventbus.annotation.BusEvent
import com.gigcreator.NetworkEvent
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.ecs.components.ModelComponent
import com.gigcreator.ecs.components.TransformComponent
import com.gigcreator.event.ServerChunkEvent
import com.gigcreator.network.SendType

internal class ChunkReplicationSystem : BaseSystem() {

    private lateinit var clientMapper: ComponentMapper<ClientComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var modelMapper: ComponentMapper<ModelComponent>

    @BusEvent
    fun onShow(event: ServerChunkEvent.Show) {
        val client = clientMapper[event.observer] ?: return

        for (entityId in event.entities) {
            val transform = transformMapper[entityId] ?: continue
            val model = modelMapper[entityId]

            client.addEvent(
                NetworkEvent.EntityJoined(
                    entityId = entityId,
                    name = "",//model?.name ?: "",
                    pos = transform.pos?: continue,
                    rot = transform.rot?: continue,
                    modelId = model?.modelId ?: 0,
                ),
                sendType = SendType.TCP
            )
        }
    }

    @BusEvent
    fun onHide(event: ServerChunkEvent.Hide) {
        val client = clientMapper[event.observer] ?: return

        for (entityId in event.entities) {
            client.addEvent(NetworkEvent.EntityLeft(entityId), sendType = SendType.TCP)
        }
    }

    override fun processSystem() {}
}