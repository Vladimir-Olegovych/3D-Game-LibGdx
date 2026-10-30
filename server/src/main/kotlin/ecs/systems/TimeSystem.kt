package com.gigcreator.ecs.systems

import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import com.gigcreator.ecs.states.ServerTimeState

internal class TimeSystem : BaseSystem() {

    @Wire
    private lateinit var serverTimeState: ServerTimeState

    override fun processSystem() {
        serverTimeState.update(world.delta)
    }
}
