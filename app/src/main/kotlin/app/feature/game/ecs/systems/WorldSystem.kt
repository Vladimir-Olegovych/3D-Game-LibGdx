package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BlenderModelComponent
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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.screens.mesh.ModelAssetManager
import com.gigapi.screens.texture.DefaultsTextures
import core.artemis.startTest100Box
import core.assets.ModelID
import core.assets.TextureID
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

    override fun setWorld(world: World?) {
        world?.let { WorldConstants.initialize(world) }
        super.setWorld(world)
    }

    override fun initialize() {}

    @BusEvent
    fun onWorldGenerated(event: GameEvent.GameWorldStarted) {
        val playerEntityId = WorldConstants.getPlayerEntityId()

        val playerBlenderModel = modelAssetManager.getRenderModel(ModelID.FIREYARETZIRESP)
        playerBlenderModel.subMeshes.forEach {
            it.mesh.transform(Matrix4().translate(0F, -1F, 0F))
        }
        val playerRawModel = MeshUtils.createBoxBySize(1F, 2F)

        transformMapper.create(playerEntityId).transform = Matrix4().translate(10F, 200F, 10F)
        blenderMapper.create(playerEntityId).apply {
            this@apply.blenderRenderData = playerBlenderModel
        }

        physicsEventBus.sendEvent(
            GameEvent.OnCreateMeshRigidBody(
                entityId = playerEntityId,
                position = Vector3(10F, 200F, 10F),
                rawMeshData = playerRawModel,
                fixedXZ = true
            )
        )

        world.startTest100Box()
    }

    override fun processSystem() {

    }

}