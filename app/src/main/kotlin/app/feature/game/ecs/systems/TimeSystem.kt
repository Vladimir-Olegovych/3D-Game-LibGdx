package app.feature.game.ecs.systems

import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import core.time.TimeState

class TimeSystem : BaseSystem() {

    @Wire
    private lateinit var timeState: TimeState

    override fun processSystem() {
        timeState.update(world.delta)
    }
}
