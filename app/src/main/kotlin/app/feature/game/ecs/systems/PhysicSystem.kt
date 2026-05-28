package app.feature.game.ecs.systems

import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.bullet.PhysicsWorldUpdater


class PhysicSystem: BaseSystem() {

    @Wire
    private lateinit var physicsWorldUpdater: PhysicsWorldUpdater
    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    @BusEvent
    fun onRigidBodyTransformUpdate(event: GameEvent.OnRigidBodyTransformUpdate) {
        val component = transformMapper[event.entityId]?: return
        component.transform = event.transform
    }

    override fun initialize() { physicsWorldUpdater.start() }

    override fun processSystem() {}

    override fun dispose() { physicsWorldUpdater.stop() }
}