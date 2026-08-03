package app.feature.game.event

import com.badlogic.gdx.math.Vector3

sealed class PlayerEvent {
    class OnSelectInventorySlot(val slot: Int)
    class OnRemoveBlock(val from: Vector3, val direction: Vector3): PlayerEvent()
}