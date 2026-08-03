package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Vector3

class LookDirectionComponent: Component() {
    var yaw = 0f
    var pitch = 0f
    val direction = Vector3(0f, 0f, 1f)
}
