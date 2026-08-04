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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_DEACTIVATION
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.mesh.ModelAssetManager
import com.gigcreator.NetEntityType
import core.animator.ModelAnimator
import core.assets.ModelID
import core.defaults.WorldConstants
import core.items.ItemManager
import core.mesh.defaultPlayerHitBox

class WorldSystem: BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager
    @Wire
    private lateinit var itemManager: ItemManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var holdingItemComponent: ComponentMapper<HoldingItemComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
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

        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.M_PLAYER_MODEL)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, 0F, 0F))
            it.mesh.scale(0.35f, 0.35f, 0.35f)
        }
        blenderMapper.create(playerEntityId).apply {
            this@apply.blenderRenderData = playerBlenderModel
            //ignoreMeshDrawing.add(0)
        }

        animatorMapper.create(playerEntityId).apply {
            animator = ModelAnimator(playerBlenderModel)
        }

        /*
       meshMapper.create(playerEntityId).apply {
           meshData = playerPhysicalModel.createMeshData(rawMeshParams)
       }
        */

        linearMoveMapper.create(playerEntityId).ignoreYLinear = true
        forceMoveMapper.create(playerEntityId)
        transformMapper.create(playerEntityId)
        lookDirectionMapper.create(playerEntityId)
        holdingItemComponent.create(playerEntityId)

        networkEntityMapper.create(playerEntityId).apply {
            isLocal = true
            entityType = NetEntityType.PLAYER
            modelId = ModelID.M_PLAYER_MODEL.ordinal
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