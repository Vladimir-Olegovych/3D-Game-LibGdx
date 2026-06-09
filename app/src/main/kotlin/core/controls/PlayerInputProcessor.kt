package core.controls

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector3
import core.bullet.PhysicsWorld
import kotlin.math.cos
import kotlin.math.sin

class PlayerInputProcessor: InputProcessor {

    companion object {
        const val PLAYER_SPEED = 5f
        const val JUMP_FORCE = 10f
        const val CAMERA_SENSITIVITY = 0.03f
        const val MAX_VERTICAL_ANGLE = 85f
    }

    private var jump = false
    private var lastMouseX = -1
    private var lastMouseY = -1
    private var deltaMouseX = 0f
    private var deltaMouseY = 0f
    private var pitch = 0f
    private var yaw = 0f
    private val moveDirection = Vector3()

    fun isJumped() = jump
    fun getPitch() = pitch
    fun getYaw() = yaw

    fun getMoveDirection(): Vector3 = moveDirection

    fun getMoveDirectionByCamera(): Vector3 {
        if (moveDirection.isZero) return Vector3.Zero

        val yawRad = Math.toRadians(yaw.toDouble())

        val forwardX = sin(yawRad).toFloat()
        val forwardZ = cos(yawRad).toFloat()

        val rightX = cos(yawRad).toFloat()
        val rightZ = -sin(yawRad).toFloat()

        val resultX = forwardX * moveDirection.z + rightX * moveDirection.x
        val resultZ = forwardZ * moveDirection.z + rightZ * moveDirection.x

        return Vector3(resultX, 0f, resultZ).nor().scl(PLAYER_SPEED)
    }

    fun update(deltaTime: Float) {
        yaw -= deltaMouseX * CAMERA_SENSITIVITY
        pitch = (pitch - deltaMouseY * CAMERA_SENSITIVITY).coerceIn(-MAX_VERTICAL_ANGLE, MAX_VERTICAL_ANGLE)

        deltaMouseX = 0f
        deltaMouseY = 0f
    }

    private fun updateMoveDirection(x: Float = moveDirection.x, z: Float = moveDirection.z) {
        moveDirection.x = x
        moveDirection.z = z
    }

    private fun updateCameraPosition(screenX: Int, screenY: Int) {
        if (lastMouseX == -1 && lastMouseY == -1) {
            lastMouseX = screenX
            lastMouseY = screenY
            return
        }

        deltaMouseX = (screenX - lastMouseX).toFloat()
        deltaMouseY = (screenY - lastMouseY).toFloat()

        lastMouseX = screenX
        lastMouseY = screenY

        return
    }

    override fun keyDown(keycode: Int): Boolean {
        when(keycode) {
            Keys.W -> updateMoveDirection(z = PLAYER_SPEED)
            Keys.A -> updateMoveDirection(x = PLAYER_SPEED)
            Keys.S -> updateMoveDirection(z = -PLAYER_SPEED)
            Keys.D -> updateMoveDirection(x = -PLAYER_SPEED)
            Keys.SPACE -> { jump = true }
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        when(keycode) {
            Keys.W -> updateMoveDirection(z = 0f)
            Keys.A -> updateMoveDirection(x = 0f)
            Keys.S -> updateMoveDirection(z = 0f)
            Keys.D -> updateMoveDirection(x = 0f)
            Keys.SPACE -> { jump = false }
        }
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        updateCameraPosition(screenX, screenY)
        return true
    }


    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        updateCameraPosition(screenX, screenY)
        return true
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }
}