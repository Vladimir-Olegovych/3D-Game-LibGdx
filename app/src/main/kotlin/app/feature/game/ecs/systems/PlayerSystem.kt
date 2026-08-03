package app.feature.game.ecs.systems

import app.feature.game.ecs.components.AnimatorComponent
import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.LinearMoveComponent
import app.feature.game.ecs.components.LookDirectionComponent
import app.feature.game.ecs.components.TransformComponent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import core.animator.ModelAnimator
import core.controls.PlayerInputProcessor
import core.defaults.CameraTypes
import core.defaults.WorldConstants
import kotlin.math.cos
import kotlin.math.sin

class PlayerSystem: BaseSystem() {

    companion object {
        private const val FREE_CAM_SPEED = 25f
        private const val THIRD_PERSON_DISTANCE = 4f
    }

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>
    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>

    private val cameraOffset = Vector3(0f, 1f, 0f)
    private val freeCamLocalPosition = Vector3(cameraOffset)
    private val playerPosition = Vector3()
    private val lookDirection = Vector3()
    private val freeCamMove = Vector3()
    private val rightDirection = Vector3()
    private val cameraTarget = Vector3()
    private val orbitPivot = Vector3()
    private var wasFreeCam = false

    override fun begin() {
        val playerEntityId = WorldConstants.getLocalPlayerEntityId()
        val linearMoveComponent = linearMoveMapper[playerEntityId]?: return
        val isJumped = playerInputProcessor.isJumped()
        linearMoveComponent.ignoreYLinear = !isJumped
    }

    override fun processSystem() {
        val playerEntityId = WorldConstants.getLocalPlayerEntityId()
        playerInputProcessor.update(world.delta)
        updateLookDirection(playerEntityId)
        cameraUpdate(playerEntityId)
        if (playerInputProcessor.isFreeCam()) {
            stopPlayer(playerEntityId)
            updateAnimation(playerEntityId, moving = false)
        } else {
            forceUpdate(playerEntityId)
            updateAnimation(playerEntityId, moving = !playerInputProcessor.getMoveDirection().isZero)
        }
    }

    private fun updateLookDirection(playerEntityId: Int) {
        val look = lookDirectionMapper[playerEntityId] ?: return
        val pitch = playerInputProcessor.getPitch()
        val yaw = playerInputProcessor.getYaw()

        look.yaw = yaw
        look.pitch = pitch

        val pitchRad = Math.toRadians(pitch.toDouble())
        val yawRad = Math.toRadians(yaw.toDouble())
        look.direction.set(
            (cos(pitchRad) * sin(yawRad)).toFloat(),
            sin(pitchRad).toFloat(),
            (cos(pitchRad) * cos(yawRad)).toFloat()
        ).nor()
    }

    private fun updateAnimation(playerEntityId: Int, moving: Boolean) {
        val animator = animatorMapper[playerEntityId]?.animator ?: return
        animator.playAnimation(if (moving) ModelAnimator.ANIM_MOVE else ModelAnimator.ANIM_IDLE)
    }

    private fun stopPlayer(playerEntityId: Int) {
        linearMoveMapper[playerEntityId]?.setDirection(Vector3.Zero)
        forceMoveMapper[playerEntityId]?.setDirection(Vector3.Zero)
    }

    private fun forceUpdate(playerEntityId: Int) {
        val linearMoveComponent = linearMoveMapper[playerEntityId]?: return
        val forceMoveComponent = forceMoveMapper[playerEntityId]?: return
        val isJumped = playerInputProcessor.isJumped()
        val direction = playerInputProcessor.getMoveDirectionByCamera()

        if (isJumped) {
            direction.y = PlayerInputProcessor.JUMP_FORCE
            forceMoveComponent.setDirection(Vector3.Zero)
        } else {
            direction.y = 0f
            forceMoveComponent.setDirection(Vector3(0f, PlayerInputProcessor.JUMP_FORCE_REVERSE, 0f))
        }

        linearMoveComponent.setDirection(direction)
    }

    private fun cameraUpdate(playerEntityId: Int) {
        val playerTransform = transformMapper[playerEntityId]?.transform ?: return
        playerTransform.getTranslation(playerPosition)

        val look = lookDirectionMapper[playerEntityId]
        if (look != null) {
            lookDirection.set(look.direction)
        } else {
            val pitch = playerInputProcessor.getPitch()
            val yaw = playerInputProcessor.getYaw()
            val pitchRad = Math.toRadians(pitch.toDouble())
            val yawRad = Math.toRadians(yaw.toDouble())
            lookDirection.set(
                (cos(pitchRad) * sin(yawRad)).toFloat(),
                sin(pitchRad).toFloat(),
                (cos(pitchRad) * cos(yawRad)).toFloat()
            ).nor()
        }

        val isFreeCam = playerInputProcessor.isFreeCam()
        val viewMode = playerInputProcessor.getViewMode()
        blenderMapper[playerEntityId]?.ignoreDrawingAll =
            !isFreeCam && viewMode == PlayerInputProcessor.VIEW_FIRST_PERSON

        if (isFreeCam) {
            if (!wasFreeCam) {
                freeCamLocalPosition.set(camera.position).sub(playerPosition)
                wasFreeCam = true
            }
            updateFreeCamLocalPosition(lookDirection)
            camera.position.set(playerPosition).add(freeCamLocalPosition)
            camera.direction.set(lookDirection)
        } else {
            wasFreeCam = false
            applyViewModeCamera(viewMode)
        }

        camera.up.set(Vector3.Y)
        camera.update()
    }

    private fun applyViewModeCamera(viewMode: Int) {
        orbitPivot.set(playerPosition).add(cameraOffset)

        when (viewMode) {
            PlayerInputProcessor.VIEW_THIRD_BACK -> {
                cameraTarget.set(orbitPivot).mulAdd(lookDirection, -THIRD_PERSON_DISTANCE)
                camera.position.lerp(cameraTarget, 0.8f)
                camera.direction.set(lookDirection)
            }
            PlayerInputProcessor.VIEW_THIRD_FRONT -> {
                cameraTarget.set(orbitPivot).mulAdd(lookDirection, THIRD_PERSON_DISTANCE)
                camera.position.lerp(cameraTarget, 0.8f)
                camera.direction.set(lookDirection).scl(-1f)
            }
            else -> {
                cameraTarget.set(orbitPivot)
                camera.position.lerp(cameraTarget, 0.8f)
                camera.direction.set(lookDirection)
            }
        }
    }

    private fun updateFreeCamLocalPosition(forward: Vector3) {
        val move = playerInputProcessor.getMoveDirection()
        val yawRad = Math.toRadians(playerInputProcessor.getYaw().toDouble())
        freeCamMove.set(Vector3.Zero)

        if (!move.isZero) {
            rightDirection.set(cos(yawRad).toFloat(), 0f, (-sin(yawRad)).toFloat())
            freeCamMove.mulAdd(forward, move.z)
            freeCamMove.mulAdd(rightDirection, move.x)
        }

        if (playerInputProcessor.isJumped()) {
            freeCamMove.y += PlayerInputProcessor.PLAYER_SPEED
        }

        if (freeCamMove.isZero) return

        freeCamMove.nor().scl(FREE_CAM_SPEED * world.delta)
        freeCamLocalPosition.add(freeCamMove)
    }
}
