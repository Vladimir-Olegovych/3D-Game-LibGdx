package app.feature.game.event

import com.badlogic.gdx.math.Vector3

sealed class PlayerEvent {
    class OnRemoveBlock(val from: Vector3, val direction: Vector3): PlayerEvent()
}