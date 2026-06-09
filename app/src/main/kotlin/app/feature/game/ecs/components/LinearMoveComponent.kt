package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Vector3

class LinearMoveComponent: Component() {
    val direction: Vector3 = Vector3()

    var ignoreYLinear = false

    fun setDirection(vector3: Vector3) {
        direction.set(vector3)
    }

}