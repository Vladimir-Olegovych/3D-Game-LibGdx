package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameChunkEvent
import app.feature.game.event.PhysicsEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_DEACTIVATION
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.mesh.ModelAssetManager
import com.gigcreator.NetEntityType
import core.animator.ModelAnimator
import core.assets.ModelID
import core.blocks.types.BlockType
import core.controls.PInputProcessor
import core.defaults.PlayerConstants
import core.items.ItemManager
import core.mesh.defaultPlayerHitBox

class WorldSystem: BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var itemManager: ItemManager
    @Wire
    private lateinit var pInputProcessor: PInputProcessor
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var holdingItemMapper: ComponentMapper<HoldingItemComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var linearMoveMapper: ComponentMapper<LinearMoveComponent>
    private lateinit var forceMoveMapper: ComponentMapper<ForceMoveComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>
    private lateinit var inventoryMapper: ComponentMapper<InventoryComponent>
    private lateinit var colorMapper: ComponentMapper<ColorComponent>

    private lateinit var inventoryListener: PInputProcessor.InputListener

    override fun setWorld(world: World) {
        PlayerConstants.initialize(world)
        super.setWorld(world)
    }

    override fun initialize() {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()
        val inventoryComponent = inventoryMapper.create(playerEntityId)
        val holdingItemComponent = holdingItemMapper.create(playerEntityId)

        pInputProcessor.applyInventoryStatistic(inventoryComponent)
        itemManager.getItem(BlockType.OAK_WOODEN_PLANKS)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.OAK_WOOD)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.SAND)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.STONE)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.LEAVES)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.WHEAT_PLANT_1)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.GRASS_PLANT_1)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.GRASS_PLANT_2)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.DIRT)?.let { inventoryComponent.addItem(it, 64) }
        itemManager.getItem(BlockType.CACTUS)?.let { inventoryComponent.addItem(it, 64) }

        inventoryListener = object : PInputProcessor.InputListener {
            override fun selectSlot(slot: Int) {
                inventoryComponent.setSelectedSlot(slot)
                val selectedInventoryItem = inventoryComponent.getSelectedInventoryItem()
                val item = selectedInventoryItem?.item
                holdingItemComponent.setHoldingItem(item)
            }
        }

        pInputProcessor.addListener(inventoryListener)
    }

    override fun processSystem() {

    }

    override fun dispose() {
        pInputProcessor.removeListener(inventoryListener)
    }

    @BusEvent
    fun onWorldGenerated(event: GameChunkEvent.GameWorldStarted) {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()
        val playerPhysicalModel = defaultPlayerHitBox

        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.M_PLAYER_MODEL)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, -PlayerConstants.cameraOffset.y, 0F))
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

        networkEntityMapper.create(playerEntityId).apply {
            isLocal = true
            entityType = NetEntityType.PLAYER
            modelId = ModelID.M_PLAYER_MODEL.ordinal
        }

        physicsEventBus.sendEvent(
            PhysicsEvent.OnCreateMeshRigidBody(
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

}