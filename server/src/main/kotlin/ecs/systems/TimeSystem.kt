package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import com.gigcreator.ecs.states.TimeState

class TimeSystem : BaseSystem() {

    @Wire
    private lateinit var timeState: TimeState

    override fun processSystem() {
        timeState.update(world.delta)
    }
}
