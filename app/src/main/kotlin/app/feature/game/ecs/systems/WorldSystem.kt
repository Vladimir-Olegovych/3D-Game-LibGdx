package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ForceMoveComponent
import app.feature.game.ecs.components.LinearMoveComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.NetworkEntityComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_DEACTIVATION
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.mesh.ModelAssetManager
import core.defaults.WorldConstants
import core.mesh.MeshUtils
import core.mesh.rawMeshParams

class WorldSystem: BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>

    override fun setWorld(world: World?) {
        world?.let { WorldConstants.initializeLocalPlayer(world) }
        super.setWorld(world)
    }

    override fun initialize() {}

    @BusEvent
    fun onWorldGenerated(event: ChunkEvent.GameWorldStarted) {
        val playerEntityId = WorldConstants.getLocalPlayerEntityId()

        /*
        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.STONE)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, -4.8F, 0F))
            it.mesh.scale(0.2f, 0.2f, 0.2f)
        }

         */
        val playerPhysicalModel = MeshUtils.createHitboxModel(1F, 1.8F)

        linearMoveMapper.create(playerEntityId).ignoreYLinear = true
        forceMoveMapper.create(playerEntityId)
        transformMapper.create(playerEntityId)

        /*
        blenderMapper.create(playerEntityId).apply {
            this@apply.blenderRenderData = playerBlenderModel
            //ignoreMeshDrawing.add(0)
        }
         */

        networkEntityMapper.create(playerEntityId).apply {
            isLocal = true
            entityType = com.gigcreator.NetEntityType.PLAYER
        }

        meshMapper.create(playerEntityId).apply {
            meshData = playerPhysicalModel.createMeshData(rawMeshParams)
        }

        physicsEventBus.sendEvent(
            GameEvent.OnCreateMeshRigidBody(
                entityId = playerEntityId,
                position = event.position,
                rawMeshData = playerPhysicalModel,
                restitution = 0f,
                friction = 0f,
                activationState = DISABLE_DEACTIVATION,
                fixedXZ = true
            )
        )


        //world.startTest100Box()
    }

    override fun processSystem() {

    }

}