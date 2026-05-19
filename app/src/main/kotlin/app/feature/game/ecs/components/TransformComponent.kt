package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Matrix4

class TransformComponent: Component() {
    var transform: Matrix4? = null
}