package core.controls

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.Vector3
import com.gigapi.input.EditableInputProcessor
import core.defaults.PlayerConstants
import core.defaults.PlayerConstants.CAMERA_SENSITIVITY
import core.defaults.PlayerConstants.MAX_VERTICAL_ANGLE
import core.defaults.PlayerConstants.PLAYER_SPEED
import kotlin.math.cos
import kotlin.math.sin

class PInputProcessor : EditableInputProcessor() {

    companion object {
        val keyNumbersMap = mapOf(
            1 to Keys.NUM_1,
            2 to Keys.NUM_2,
            3 to Keys.NUM_3,
            4 to Keys.NUM_4,
            5 to Keys.NUM_5,
            6 to Keys.NUM_6,
            7 to Keys.NUM_7,
            8 to Keys.NUM_8,
            9 to Keys.NUM_9,
            0 to Keys.NUM_0
        )

        val cameraModes = listOf(
            PlayerConstants.VIEW_FIRST_PERSON,
            PlayerConstants.VIEW_THIRD_BACK,
            PlayerConstants.VIEW_THIRD_FRONT
        )
    }

    interface InputListener {
        fun selectCameraMode(mode: Int) {}
        fun freeCameraMode(status: Boolean) {}
    }

    val isAndroid: Boolean = false
    private val listeners = mutableSetOf<InputListener>()

    var isFreeCameraMode = false
        private set
    var currentViewMode = PlayerConstants.VIEW_FIRST_PERSON
        private set
    var isJumped = false
        private set
    var lastMouseX = -1
        private set
    var lastMouseY = -1
        private set
    var deltaMouseX = 0f
        private set
    var deltaMouseY = 0f
        private set
    var pitch = 0f
        private set
    var yaw = 0f
        private set

    val moveDirection = Vector3()
    val moveDirectionByCamera = Vector3()

    fun clear() {
        isJumped = false
        lastMouseX = -1
        lastMouseY = -1
        deltaMouseX = 0f
        deltaMouseY = 0f
        moveDirection.set(Vector3.Zero)
        moveDirectionByCamera.set(Vector3.Zero)
    }

    fun addListener(listener: InputListener) {
        listeners += listener
    }

    fun removeListener(listener: InputListener) {
        listeners -= listener
    }

    fun updateCamera(deltaTime: Float) {
        yaw -= deltaMouseX * CAMERA_SENSITIVITY
        pitch = (pitch - deltaMouseY * CAMERA_SENSITIVITY)
            .coerceIn(-MAX_VERTICAL_ANGLE, MAX_VERTICAL_ANGLE)

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

        moveDirectionByCamera.set(resultX, 0f, resultZ)
            .nor()
            .scl(PLAYER_SPEED)
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
    }

    private fun updateFreeCamera() {
        isFreeCameraMode = !isFreeCameraMode
        listeners.forEach { it.freeCameraMode(isFreeCameraMode) }
    }

    private fun updateCameraMode() {
        val currentIndex = cameraModes.indexOf(currentViewMode)
        val nextIndex = (currentIndex + 1) % cameraModes.size

        currentViewMode = cameraModes[nextIndex]

        listeners.forEach { it.selectCameraMode(currentViewMode) }
    }

    override fun keyDown(keycode: Int): Boolean {
        when(keycode) {
            Keys.W -> updateMoveDirection(z = PLAYER_SPEED)
            Keys.A -> updateMoveDirection(x = PLAYER_SPEED)
            Keys.S -> updateMoveDirection(z = -PLAYER_SPEED)
            Keys.D -> updateMoveDirection(x = -PLAYER_SPEED)
            Keys.SPACE -> { isJumped = true }
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        when(keycode) {
            Keys.W -> updateMoveDirection(z = 0f)
            Keys.A -> updateMoveDirection(x = 0f)
            Keys.S -> updateMoveDirection(z = 0f)
            Keys.D -> updateMoveDirection(x = 0f)
            Keys.F -> updateFreeCamera()
            Keys.F5 -> updateCameraMode()
            Keys.SPACE -> { isJumped = false }
        }
        //PlayerInputProcessor.keyNumbersMap.forEach { (num, key) ->
        //    if (keycode == key) selectSlotByKeyNum(num)
        //}
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        updateCameraPosition(screenX, screenY)
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        updateCameraPosition(screenX, screenY)
        return false
    }

    fun getSelectedSlot(): Int {
        return 0
    }

    fun isMouseLeft(): Boolean {
        return false
    }

    //Android Private
    fun addLook(deltaX: Float, deltaY: Float) {
        deltaMouseX += deltaX
        deltaMouseY += deltaY
    }

    fun setMoveInput(x: Float, z: Float) {
        moveDirection.x = -x
        moveDirection.z = z
    }

    fun setJump(jumped: Boolean) {
        isJumped = jumped
    }
}