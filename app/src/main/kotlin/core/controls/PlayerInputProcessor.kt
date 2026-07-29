package core.controls

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import core.bullet.raycast.RayCastTypes
import core.defaults.CameraTypes
import kotlin.math.cos
import kotlin.math.sin

class PlayerInputProcessor: LaunchedEffect, InputProcessor {

    companion object {
        const val PLAYER_SPEED = 90f
        const val JUMP_FORCE = 10f
        const val JUMP_FORCE_REVERSE = -10f
        const val CAMERA_SENSITIVITY = 0.03f
        const val MAX_VERTICAL_ANGLE = 89f
    }

    private lateinit var mainEventBus: EventBus
    private lateinit var physicsEventBus: EventBus
    private lateinit var camera: PerspectiveCamera

    override fun launch(gContext: GContext) {
        camera = gContext.getObject(CameraTypes.GL_3D)
        mainEventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        physicsEventBus = gContext.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
    }

    private var isMouseHold = false
    private var jump = false
    private var lastMouseX = -1
    private var lastMouseY = -1
    private var deltaMouseX = 0f
    private var deltaMouseY = 0f
    private var pitch = 0f
    private var yaw = 0f
    private val moveDirection = Vector3()
    private val moveDirectionByCamera = Vector3()

    fun clear() {
        isMouseHold = false
        jump = false
        lastMouseX = -1
        lastMouseY = -1
        deltaMouseX = 0f
        deltaMouseY = 0f
        //pitch = 0f
        //yaw = 0f
        moveDirection.set(Vector3.Zero)
        moveDirectionByCamera.set(Vector3.Zero)
    }

    fun isJumped() = jump
    fun getPitch() = pitch
    fun getYaw() = yaw

    fun getMoveDirection(): Vector3 = moveDirection

    fun getMoveDirectionByCamera(): Vector3 = moveDirectionByCamera

    fun update(deltaTime: Float) {
        if (isMouseHold) {
            onLeftButtonClick()
        }
        yaw -= deltaMouseX * CAMERA_SENSITIVITY
        pitch = (pitch - deltaMouseY * CAMERA_SENSITIVITY).coerceIn(-MAX_VERTICAL_ANGLE, MAX_VERTICAL_ANGLE)

        deltaMouseX = 0f
        deltaMouseY = 0f

        if (moveDirection.isZero) {
            moveDirectionByCamera.set(Vector3.Zero)
            return
        }

        val yawRad = Math.toRadians(yaw.toDouble())

        val forwardX = sin(yawRad).toFloat()
        val forwardZ = cos(yawRad).toFloat()

        val rightX = cos(yawRad).toFloat()
        val rightZ = -sin(yawRad).toFloat()

        val resultX = forwardX * moveDirection.z + rightX * moveDirection.x
        val resultZ = forwardZ * moveDirection.z + rightZ * moveDirection.x

        moveDirectionByCamera.set(resultX, 0f, resultZ).nor().scl(PLAYER_SPEED)
    }

    private fun onLeftButtonClick() {
        physicsEventBus.sendEvent(
            GameEvent.OnRayCastRequest(
                requestId = RayCastTypes.CHUNK_RAY_CAST,
                from = camera.position.cpy(),
                direction = camera.direction.cpy(),
                maxDistance = 5f
            )
        )
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
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        when(keycode) {
            Keys.W -> updateMoveDirection(z = 0f)
            Keys.A -> updateMoveDirection(x = 0f)
            Keys.S -> updateMoveDirection(z = 0f)
            Keys.D -> updateMoveDirection(x = 0f)
            Keys.SPACE -> { jump = false }
        }
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        updateCameraPosition(screenX, screenY)
        return false
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
        isMouseHold = true
        return false
    }

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        isMouseHold = false
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
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }
}