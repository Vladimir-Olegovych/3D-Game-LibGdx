package core.controls

import app.feature.game.ecs.components.InventoryComponent
import app.feature.game.ui.InventoryUI.Companion.TOOL_BAR_SIZE
import com.badlogic.gdx.Input.Buttons
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.Vector3
import com.gigapi.input.EditableInputProcessor
import core.defaults.PlayerConstants
import core.defaults.PlayerConstants.CAMERA_SENSITIVITY
import core.defaults.PlayerConstants.MAX_VERTICAL_ANGLE
import core.defaults.PlayerConstants.PLAYER_SPEED
import kotlin.math.cos
import kotlin.math.sin

class PInputProcessor: EditableInputProcessor() {

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
            //9 to Keys.NUM_9,
            //0 to Keys.NUM_0
        )

        val cameraModes = listOf(
            PlayerConstants.VIEW_FIRST_PERSON,
            PlayerConstants.VIEW_THIRD_BACK,
            PlayerConstants.VIEW_THIRD_FRONT
        )
    }

    interface InputListener {
        fun onLeftClick() {}
        fun onRightClick() {}
        fun selectSlot(slot: Int) {}
        fun selectCameraMode(mode: Int) {}
        fun freeCameraMode(status: Boolean) {}
    }

    private val listeners = mutableSetOf<InputListener>()

    var isMouseLeftHold = false
        private set
    var isMouseRightHold = false
        private set
    var selectedSlot = 0
        private set
    var inventoryStartSlot = 0
        private set
    var inventoryEndSlot = 0
        private set
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

    fun applyInventoryStatistic(inventoryComponent: InventoryComponent) {
        inventoryStartSlot = inventoryComponent.inventorySize - TOOL_BAR_SIZE
        inventoryEndSlot = inventoryComponent.inventorySize - 1
        selectedSlot = inventoryEndSlot
        inventoryComponent.setSelectedSlot(selectedSlot)
    }

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

    private fun selectSlotByKeyNum(num: Int) {
        val adjustedNum = if (num == 0) 9 else num - 1
        val slot = adjustedNum + inventoryStartSlot
        selectedSlot = slot
        listeners.forEach { it.selectSlot(slot) }
    }

    private fun onScrollSlot(count: Float) {
        if (count < 0) {
            selectedSlot--
            if (selectedSlot < inventoryStartSlot) {
                selectedSlot = inventoryEndSlot
            }
        } else {
            selectedSlot++
            if (selectedSlot > inventoryEndSlot) {
                selectedSlot = inventoryStartSlot
            }
        }
        listeners.forEach { it.selectSlot(selectedSlot) }
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
        keyNumbersMap.forEach { (num, key) ->
            if (keycode == key) selectSlotByKeyNum(num)
        }
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

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        when (button) {
            Buttons.LEFT -> {
                isMouseLeftHold = true
                listeners.forEach { it.onLeftClick() }
            }
            Buttons.RIGHT -> {
                isMouseRightHold = true
                listeners.forEach { it.onRightClick() }
            }
        }
        return false
    }

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        when (button) {
            Buttons.LEFT -> isMouseLeftHold = false
            Buttons.RIGHT -> isMouseRightHold = false
        }
        return false
    }

    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        when (button) {
            Buttons.LEFT -> isMouseLeftHold = false
            Buttons.RIGHT -> isMouseRightHold = false
        }
        return false
    }


    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        onScrollSlot(amountY)
        return false
    }
}