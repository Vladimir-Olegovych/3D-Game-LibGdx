package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Vector3

class LinearMoveComponent: Component() {
    val direction: Vector3 = Vector3()

    var ignoreYLinear = false
        set(value) {
            if (field == value) return
            field = value
            dirty = true
        }

    var dirty = true

    fun setDirection(vector3: Vector3) {
        if (direction.epsilonEquals(vector3, 0.0001f)) return
        direction.set(vector3)
        dirty = true
    }
}
