package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.gigcreator.core.defaults.ServerConstants

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
        const val DEFAULT_DURATION = ServerConstants.SERVER_DELTA
        const val SNAP_DISTANCE = 16f
    }
}
