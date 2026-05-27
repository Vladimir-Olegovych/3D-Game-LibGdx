package app.feature.game.ecs.systems

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.assets.SkinID
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.bullet.PhysicsWorldUpdater
import core.mesh.MeshUtils


class PhysicSystem: BaseSystem() {

    @Wire
    private lateinit var physicsWorldUpdater: PhysicsWorldUpdater
    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus

    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    //Delete
    @Wire
    private lateinit var blockDataManager: BlockDataManager
    @Wire private lateinit var assetManager: AssetManager
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    override fun initialize() {
        physicsWorldUpdater.start()

        val meshTexture = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()

        val size = 4F
        val m = size * 2.1F
        val rawBoxMesh = MeshUtils.createBoxMeshData(blockDataManager, BlockType.STONE, size)

        for (x in 0 .. 5) {
            for (y in -10 .. 0) {
                for (z in 0 .. 5) {
                    val entityId = world.create()
                    transformMapper.create(entityId)
                    meshMapper.create(entityId).apply {
                        this@apply.meshData = rawBoxMesh.createMeshData()
                        this@apply.meshTextureData = meshTexture
                    }
                    physicsEventBus.sendEvent(GameEvent.OnCreateMeshRigidBody(
                        entityId = entityId,
                        position = Vector3(x * m + 10, y * m + 700, z * m + 10),
                        rawMeshData = rawBoxMesh
                    ))
                }
            }
        }
    }

    @BusEvent
    fun onRigidBodyTransformUpdate(event: GameEvent.OnRigidBodyTransformUpdate) {
        val component = transformMapper[event.entityId]?: return
        component.transform = event.transform
    }

    override fun processSystem() {}

    override fun dispose() {
        physicsWorldUpdater.stop()
    }
}