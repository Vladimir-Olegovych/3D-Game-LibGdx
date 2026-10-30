package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.gigapi.eventbus.annotation.BusEvent
import com.gigcreator.NetworkEvent
import com.gigcreator.ecs.components.TransformComponent

internal class EntityStateSystem: BaseSystem() {

    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    @BusEvent
    fun onEntityJoined(event: NetworkEvent.EntityJoined) {

    }

    @BusEvent
    fun onEntityJoined(event: NetworkEvent.EntityStateUpdate) {

    }

    @BusEvent
    fun onEntityJoined(event: NetworkEvent.EntityLeft) {

    }

    override fun processSystem() {

    }
}