package app.feature.game.ecs.systems

import app.feature.game.ecs.components.LookDirectionComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.PhysicsEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import core.bullet.raycast.RayCastTypes
import core.controls.PInputProcessor
import core.defaults.CameraTypes
import core.defaults.PlayerConstants
import kotlin.math.cos
import kotlin.math.sin

class CameraSystem : PInputProcessor.InputListener, BaseSystem() {

    companion object {
        private const val FREE_CAMERA_SPEED = 90f
    }

    @Wire
    private lateinit var playerInputProcessor: PInputProcessor

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>

    private val playerPosition = Vector3()
    private val lookDirection = Vector3()
    private val orbitPivot = Vector3()
    private val cameraTarget = Vector3()

    private val freeCameraOffset = Vector3()
    private val freeCameraMove = Vector3()
    private val rightDirection = Vector3()

    private var cameraMode = PlayerConstants.VIEW_FIRST_PERSON

    override fun onRightClick() {
        physicsEventBus.sendEvent(
            PhysicsEvent.OnRayCastRequest(
                requestId = RayCastTypes.CHUNK_PLACE_RAY_CAST,
                requestOwner = PlayerConstants.getLocalPlayerEntityId(),
                from = camera.position.cpy(),
                direction = camera.direction.cpy(),
                maxDistance = 5f
            )
        )
    }

    override fun selectCameraMode(mode: Int) {
        cameraMode = mode
    }

    override fun freeCameraMode(status: Boolean) {
        if (status) {
            freeCameraOffset.set(camera.position).sub(playerPosition)
        }
    }

    override fun initialize() {
        playerInputProcessor.addListener(this)

        transformMapper = world.getMapper(TransformComponent::class.java)
        lookDirectionMapper = world.getMapper(LookDirectionComponent::class.java)

        cameraMode = playerInputProcessor.currentViewMode
    }

    override fun dispose() {
        playerInputProcessor.removeListener(this)
    }

    override fun processSystem() {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()

        playerInputProcessor.updateCamera(world.delta)
        updateLookDirection(playerEntityId)
        updateCamera(playerEntityId)
    }

    private fun updateLookDirection(playerEntityId: Int) {
        val look = lookDirectionMapper[playerEntityId] ?: return

        val pitch = playerInputProcessor.pitch
        val yaw = playerInputProcessor.yaw

        val pitchRad = Math.toRadians(pitch.toDouble())
        val yawRad = Math.toRadians(yaw.toDouble())

        look.yaw = yaw
        look.pitch = pitch

        look.direction.set(
            (cos(pitchRad) * sin(yawRad)).toFloat(),
            sin(pitchRad).toFloat(),
            (cos(pitchRad) * cos(yawRad)).toFloat()
        ).nor()

        lookDirection.set(look.direction)
    }

    private fun updateCamera(playerEntityId: Int) {
        val transform = transformMapper[playerEntityId]?.transform ?: return
        transform.getTranslation(playerPosition)

        if (playerInputProcessor.isFreeCameraMode) {
            updateFreeCamera()
            return
        }

        orbitPivot.set(playerPosition).add(PlayerConstants.cameraOffset)

        when (cameraMode) {
            PlayerConstants.VIEW_THIRD_BACK -> {
                cameraTarget.set(orbitPivot)
                    .mulAdd(lookDirection, -PlayerConstants.THIRD_PERSON_DISTANCE)

                camera.position.lerp(cameraTarget, PlayerConstants.CAMERA_LERP)
                camera.direction.set(lookDirection)
            }

            PlayerConstants.VIEW_THIRD_FRONT -> {
                cameraTarget.set(orbitPivot)
                    .mulAdd(lookDirection, PlayerConstants.THIRD_PERSON_DISTANCE)

                camera.position.lerp(cameraTarget, PlayerConstants.CAMERA_LERP)
                camera.direction.set(lookDirection).scl(-1f)
            }

            else -> {
                camera.position.lerp(orbitPivot, PlayerConstants.CAMERA_LERP)
                camera.direction.set(lookDirection)
            }
        }

        camera.up.set(Vector3.Y)
        camera.update()
    }

    private fun updateFreeCamera() {
        val move = playerInputProcessor.moveDirection
        val yawRad = Math.toRadians(playerInputProcessor.yaw.toDouble())

        freeCameraMove.set(Vector3.Zero)

        if (!move.isZero) {
            rightDirection.set(
                cos(yawRad).toFloat(),
                0f,
                (-sin(yawRad)).toFloat()
            )

            freeCameraMove.mulAdd(lookDirection, move.z)
            freeCameraMove.mulAdd(rightDirection, move.x)
        }

        if (playerInputProcessor.isJumped) {
            freeCameraMove.y += 1f
        }

        if (!freeCameraMove.isZero) {
            freeCameraMove.nor().scl(FREE_CAMERA_SPEED * world.delta)
            freeCameraOffset.add(freeCameraMove)
        }

        camera.position.set(playerPosition).add(freeCameraOffset)
        camera.direction.set(lookDirection)
        camera.up.set(Vector3.Y)
        camera.update()
    }
}