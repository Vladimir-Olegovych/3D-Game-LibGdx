package core.defaults

import com.artemis.World
import com.badlogic.gdx.math.Vector3

object PlayerConstants {

    const val REACH_DISTANCE = 6f
    const val PLAYER_SPEED = 10f
    const val PLAYER_RUN_SPEED = 15f
    const val JUMP_FORCE = 10f
    const val JUMP_FORCE_REVERSE = -10f
    const val CAMERA_SENSITIVITY = 0.03f
    const val MAX_VERTICAL_ANGLE = 89f

    const val VIEW_FIRST_PERSON = 0
    const val VIEW_THIRD_BACK = 1
    const val VIEW_THIRD_FRONT = 2

    const val THIRD_PERSON_DISTANCE = 4f

    val cameraOffset = Vector3(0f, 0.6f, 0f)

    private var localPlayerEntityId = -1

    fun initialize(world: World) {
        val entityId = world.create()
        localPlayerEntityId = entityId
    }

    fun getLocalPlayerEntityId(): Int = localPlayerEntityId

}
