package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.LinearMoveComponent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Vector3
import core.controls.PInputProcessor
import core.defaults.PlayerConstants

class PlayerSystem : BaseSystem() {

    @Wire
    private lateinit var playerInputProcessor: PInputProcessor

    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>

    override fun initialize() {
        linearMoveMapper = world.getMapper(LinearMoveComponent::class.java)
        forceMoveMapper = world.getMapper(ForceMoveComponent::class.java)
    }

    override fun begin() {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()
        val linearMoveComponent = linearMoveMapper[playerEntityId] ?: return
        val isFreeCamera = playerInputProcessor.isFreeCameraMode
        val currentViewMode = playerInputProcessor.currentViewMode

        blenderMapper[playerEntityId]?.ignoreDrawingAll =
            !isFreeCamera && currentViewMode == PlayerConstants.VIEW_FIRST_PERSON

        linearMoveComponent.ignoreYLinear = !playerInputProcessor.isJumped
    }

    override fun processSystem() {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()

        if (playerInputProcessor.isFreeCameraMode) {
            stopPlayer(playerEntityId)
            return
        }

        updateMovement(playerEntityId)
    }

    private fun updateMovement(playerEntityId: Int) {
        val linearMoveComponent = linearMoveMapper[playerEntityId] ?: return
        val forceMoveComponent = forceMoveMapper[playerEntityId] ?: return

        val direction = playerInputProcessor.moveDirectionByCamera

        if (playerInputProcessor.isJumped) {
            direction.y = PlayerConstants.JUMP_FORCE
            forceMoveComponent.setDirection(Vector3.Zero)
        } else {
            direction.y = 0f
            forceMoveComponent.setDirection(
                Vector3(0f, PlayerConstants.JUMP_FORCE_REVERSE, 0f)
            )
        }

        linearMoveComponent.setDirection(direction)
    }

    private fun stopPlayer(playerEntityId: Int) {
        linearMoveMapper[playerEntityId]?.setDirection(Vector3.Zero)
        forceMoveMapper[playerEntityId]?.setDirection(Vector3.Zero)
    }
}