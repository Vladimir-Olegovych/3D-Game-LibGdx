package com.gigcreator.ecs.states

class ServerTimeState {
    var timeOfDay = 0.2f

    var cycleDuration = 600f

    fun update(deltaTime: Float) {
        timeOfDay = (timeOfDay + deltaTime / cycleDuration) % 1f
    }
}
