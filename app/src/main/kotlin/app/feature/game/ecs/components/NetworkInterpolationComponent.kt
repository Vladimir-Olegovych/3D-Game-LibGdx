package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

class NetworkInterpolationComponent : Component() {
    val fromPos = Vector3()
    val toPos = Vector3()
    val fromRot = Quaternion()
    val toRot = Quaternion()
    val renderPos = Vector3()
    val renderRot = Quaternion()

    var elapsed = 0f
    var duration = DEFAULT_DURATION
    var hasTarget = false

    companion object {
        const val DEFAULT_DURATION = 1f / 20f
        const val SNAP_DISTANCE = 16f
    }
}
