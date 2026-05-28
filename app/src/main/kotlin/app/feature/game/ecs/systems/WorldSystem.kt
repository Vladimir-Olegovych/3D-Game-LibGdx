package app.feature.game.ecs.systems

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.screens.mesh.ModelAssetManager
import core.artemis.startTest100Box
import core.assets.ModelID
import core.assets.TextureID
import core.defaults.WorldConstants

class WorldSystem: BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    override fun setWorld(world: World?) {
        world?.let { WorldConstants.initialize(world) }
        super.setWorld(world)
    }

    override fun initialize() {
        val playerEntityId = WorldConstants.getPlayerEntityId()

        val playerMeshTexture = assetManager.get<Texture>(TextureID.PLAYER.filePath)
        val playerRawMesh = modelAssetManager.get(ModelID.CAPSULE)
        transformMapper.create(playerEntityId)
        meshMapper.create(playerEntityId).apply {
            this@apply.meshData = playerRawMesh.createMeshData()
            this@apply.meshTextureData = playerMeshTexture
        }
        physicsEventBus.sendEvent(GameEvent.OnCreateMeshRigidBody(
            entityId = playerEntityId,
            position = Vector3(10F, 200F, 10F),
            rawMeshData = playerRawMesh,
            fixedXZ = true
        ))

        world.startTest100Box()
    }

    override fun processSystem() {

    }

}