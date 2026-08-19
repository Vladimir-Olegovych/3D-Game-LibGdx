package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

class PhysicsInterpolationComponent : Component() {
    val toPos = Vector3()
    val toRot = Quaternion()
    val renderPos = Vector3()
    val renderRot = Quaternion()
    val pendingPos = Vector3()
    val pendingRot = Quaternion()

    var hasTarget = false
    var hasPending = false

    companion object {
        const val SMOOTHING = 10f
        const val SNAP_DISTANCE = 32f
    }
}
