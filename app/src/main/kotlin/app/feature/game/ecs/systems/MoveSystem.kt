package app.feature.game.ecs.systems

import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.LinearMoveComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.gigapi.eventbus.EventBus

@One(LinearMoveComponent::class, ForceMoveComponent::class)
class MoveSystem: IteratingSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>

    override fun process(entityId: Int) {
        linearMoveMapper[entityId]?.let {
            physicsEventBus.sendEvent(
                GameEvent.OnApplyLinearForce(
                    entityId,
                    it.ignoreYLinear,
                    it.direction
                )
            )
        }
        forceMoveMapper[entityId]?.let {
            physicsEventBus.sendEvent(
                GameEvent.OnApplyForce(
                    entityId,
                    it.direction
                )
            )
        }
    }

}