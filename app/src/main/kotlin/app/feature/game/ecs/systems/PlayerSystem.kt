package app.feature.game.ecs.systems

import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.LinearMoveComponent
import app.feature.game.ecs.components.TransformComponent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import core.controls.PlayerInputProcessor
import core.defaults.CameraTypes
import core.defaults.WorldConstants
import kotlin.math.cos
import kotlin.math.sin

class PlayerSystem: BaseSystem() {

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>

    private val cameraOffset = Vector3(0f, 0.6f, 0f)

    override fun begin() {
        val playerEntityId = WorldConstants.getPlayerEntityId()
        val linearMoveComponent = linearMoveMapper[playerEntityId]?: return
        val isJumped = playerInputProcessor.isJumped()
        linearMoveComponent.ignoreYLinear = !isJumped
    }

    override fun processSystem() {
        val playerEntityId = WorldConstants.getPlayerEntityId()
        playerInputProcessor.update(world.delta)
        cameraUpdate(playerEntityId)
        forceUpdate(playerEntityId)
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
        val playerPosition = Vector3()
        playerTransform.getTranslation(playerPosition)
        playerPosition.add(cameraOffset)

        val pitch = playerInputProcessor.getPitch()
        val yaw = playerInputProcessor.getYaw()

        val dirX = cos(Math.toRadians(pitch.toDouble())) * sin(Math.toRadians(yaw.toDouble()))
        val dirY = sin(Math.toRadians(pitch.toDouble()))
        val dirZ = cos(Math.toRadians(pitch.toDouble())) * cos(Math.toRadians(yaw.toDouble()))

        val direction = Vector3(dirX.toFloat(), dirY.toFloat(), dirZ.toFloat()).nor()

        camera.position.lerp(playerPosition, 0.2f)
        camera.direction.set(direction)
        camera.up.set(Vector3.Y)
        camera.update()
    }
}