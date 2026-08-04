package com.gigcreator.ecs.states

class TimeState {
    var timeOfDay = 0.7f
        private set

    var cycleDuration = 600f

    fun update(deltaTime: Float) {
        timeOfDay = (timeOfDay + deltaTime / cycleDuration) % 1f
    }
}
