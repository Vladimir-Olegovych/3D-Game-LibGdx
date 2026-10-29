package app.feature.game.ecs.systems

import app.feature.game.ecs.states.TimeState
import com.artemis.BaseSystem
import com.artemis.annotations.Wire

class TimeSystem : BaseSystem() {

    @Wire
    private lateinit var timeState: TimeState

    override fun processSystem() {
        timeState.update(world.delta)
    }
}
