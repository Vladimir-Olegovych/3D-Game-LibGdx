package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.ChunkComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.math.vector.IntVector3
import core.assets.SkinID
import core.chunk.ChunkManager
import core.defaults.CameraTypes
import core.math.createMatrixForChunk
import core.mesh.MeshUtils

@All(MeshComponent::class)
class ChunkSystem: BaseSystem() {

    @Wire
    private lateinit var chunkManager: ChunkManager
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = EventBusTypes.MAIN_EVENT_BUS)
    private lateinit var eventBus: EventBus

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var chunkMapper: ComponentMapper<ChunkComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    private lateinit var chunkMeshTextureData: Texture

    override fun initialize() {
        chunkMeshTextureData = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()
    }

    @BusEvent
    fun onChunkDataCreated(event: GameEvent.OnCreateChunkData) {
        val entityId = event.chunkEntityId
        chunkMapper.create(entityId).chunkData = event.chunkData
        transformMapper.create(entityId).transform = createMatrixForChunk(event.chunkData)
    }

    @BusEvent
    fun onMeshDataCreated(event: GameEvent.OnCreateChunkMeshData) {
        val entityId = event.chunkEntityId
        val meshComp = meshMapper.create(entityId)
        val mesh = event.meshData.mesh ?: return
        val radius = MeshUtils.getBoundRadius(mesh)
        meshComp.meshData = event.meshData
        meshComp.meshTextureData = chunkMeshTextureData
        boundMapper.create(entityId).boundingRadius = radius
    }

    @BusEvent
    fun onChunkDataRemoved(event: GameEvent.OnRemoveChunkData) {
        chunkMapper.remove(event.chunkEntityId)
    }

    @BusEvent
    fun onMeshDataRemoved(event: GameEvent.OnRemoveChunkMeshData) {
        meshMapper[event.chunkEntityId]?.dispose()
        meshMapper.remove(event.chunkEntityId)
    }

    private var lastCameraPosition = IntVector3()
    private var timeSinceLastUpdate = 0f

    override fun processSystem() {
        timeSinceLastUpdate += world.delta
        if (timeSinceLastUpdate < 0.3f) return
        timeSinceLastUpdate = 0f
        val currentCameraPosition = IntVector3.roundToInt(camera.position)
        if (currentCameraPosition == lastCameraPosition) return
        lastCameraPosition = currentCameraPosition
        eventBus.sendEvent(GameEvent.LoadAdditionalChunksRequest(world, currentCameraPosition))
    }
}