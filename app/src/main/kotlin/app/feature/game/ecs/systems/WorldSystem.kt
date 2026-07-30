package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
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
import com.gigcreator.NetEntityType
import core.assets.ModelID
import core.defaults.WorldConstants
import core.mesh.MeshUtils
import core.mesh.defaultPlayerHitBox

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
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>

    override fun setWorld(world: World?) {
        world?.let { WorldConstants.initializeLocalPlayer(world) }
        super.setWorld(world)
    }

    override fun initialize() {}

    @BusEvent
    fun onWorldGenerated(event: ChunkEvent.GameWorldStarted) {
        val playerEntityId = WorldConstants.getLocalPlayerEntityId()
        val playerPhysicalModel = defaultPlayerHitBox

        /*
        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.STONE)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, -4.8F, 0F))
            it.mesh.scale(0.2f, 0.2f, 0.2f)
        }
        blenderMapper.create(playerEntityId).apply {
            this@apply.blenderRenderData = playerBlenderModel
            //ignoreMeshDrawing.add(0)
        }
         */

        /*
       meshMapper.create(playerEntityId).apply {
           meshData = playerPhysicalModel.createMeshData(rawMeshParams)
       }
        */

        linearMoveMapper.create(playerEntityId).ignoreYLinear = true
        forceMoveMapper.create(playerEntityId)
        transformMapper.create(playerEntityId)

        networkEntityMapper.create(playerEntityId).apply {
            isLocal = true
            entityType = NetEntityType.PLAYER
            modelId = ModelID.NULL.ordinal
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