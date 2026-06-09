package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.LinearMoveComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_DEACTIVATION
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.screens.mesh.ModelAssetManager
import core.artemis.startTest100Box
import core.defaults.WorldConstants
import core.mesh.MeshUtils

class WorldSystem: BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>

    override fun setWorld(world: World?) {
        world?.let { WorldConstants.initialize(world) }
        super.setWorld(world)
    }

    override fun initialize() {}

    @BusEvent
    fun onWorldGenerated(event: GameEvent.GameWorldStarted) {
        val playerEntityId = WorldConstants.getPlayerEntityId()

        /*
        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.STONE)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, -4.8F, 0F))
            it.mesh.scale(0.2f, 0.2f, 0.2f)
        }

         */
        val playerPhysicalModel = MeshUtils.createBoxModel(1F, 1.8F)

        linearMoveMapper.create(playerEntityId).ignoreYLinear = true
        forceMoveMapper.create(playerEntityId)
        transformMapper.create(playerEntityId)

        /*
        blenderMapper.create(playerEntityId).apply {
            this@apply.blenderRenderData = playerBlenderModel
            //ignoreMeshDrawing.add(0)
        }
         */

        physicsEventBus.sendEvent(
            GameEvent.OnCreateMeshRigidBody(
                entityId = playerEntityId,
                position = Vector3(10F, 200F, 10F),
                rawMeshData = playerPhysicalModel,
                restitution = 0f,
                friction = 0f,
                activationState = DISABLE_DEACTIVATION,
                fixedXZ = true
            )
        )


        world.startTest100Box()
    }

    override fun processSystem() {

    }

}