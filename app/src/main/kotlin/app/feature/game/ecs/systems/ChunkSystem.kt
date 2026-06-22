package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import app.feature.game.event.ChunkEvent
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
import core.chunk.ChunkWorldUpdater
import core.defaults.CameraTypes
import core.mesh.MeshUtils

@All(MeshComponent::class)
class ChunkSystem: BaseSystem() {

    @Wire
    private lateinit var chunkWorldUpdater: ChunkWorldUpdater
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = EventBusTypes.MAIN_EVENT_BUS)
    private lateinit var mainEventBus: EventBus
    @Wire(name = EventBusTypes.CHUNK_EVENT_BUS)
    private lateinit var chunkEventBus: EventBus

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var chunkMapper: ComponentMapper<ChunkComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var aoMapper: ComponentMapper<AOComponent>

    private lateinit var chunkMeshTextureData: Texture

    override fun initialize() {
        chunkWorldUpdater.start()
        chunkMeshTextureData = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()
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
        chunkEventBus.sendEvent(ChunkEvent.LoadAdditionalChunksRequest(currentCameraPosition))
    }

    override fun dispose() {
        chunkWorldUpdater.stop(false)
    }


    @BusEvent
    fun onChunkEntitiesRequest(event: ChunkEvent.ChunkEntitiesRequest) {
        val generationData = event.generationData
        val entities = HashMap<IntVector3, Int>()

        for (pos in generationData.chunkDataPositionsToCreate) {
            val entityId = world.create()
            entities[pos] = entityId
        }

        chunkEventBus.sendEvent(ChunkEvent.ChunkEntitiesResponse(
            generationData = generationData,
            entities = entities
        ))
    }
    @BusEvent
    fun onChunkDataCreated(event: GameEvent.OnCreateChunkTransform) {
        val entityId = event.chunkEntityId
        transformMapper.create(entityId).transform = event.transform
    }

    @BusEvent
    fun onUpdateChunkMeshData(event: GameEvent.OnUpdateChunkMeshData) {
        val entityId = event.chunkEntityId
        val meshComponent = meshMapper[entityId]?: meshMapper.create(entityId)
        meshComponent.dispose()
        meshComponent.meshData = event.meshData
        meshComponent.meshTextureData = chunkMeshTextureData
        val radius = MeshUtils.getBoundRadius(event.meshData.mesh)
        aoMapper[entityId]?: aoMapper.create(entityId)
        val boundComponent = boundMapper[entityId]?: boundMapper.create(entityId)
        boundComponent.boundingRadius = radius
    }

    @BusEvent
    fun onMeshDataCreated(event: GameEvent.OnCreateChunkMeshData) {
        val entityId = event.chunkEntityId
        if (meshMapper[entityId] != null) return
        val meshComponent = meshMapper.create(entityId)
        val mesh = event.meshData.mesh ?: return
        val radius = MeshUtils.getBoundRadius(mesh)
        meshComponent.meshData = event.meshData
        meshComponent.meshTextureData = chunkMeshTextureData
        aoMapper.create(entityId)
        boundMapper.create(entityId).boundingRadius = radius
    }

    @BusEvent
    fun onChunkDataRemoved(event: GameEvent.OnRemoveChunkData) {
        //chunkMapper.remove(event.chunkEntityId)
        transformMapper.remove(event.chunkEntityId)
        world.delete(event.chunkEntityId)
    }

    @BusEvent
    fun onMeshDataRemoved(event: GameEvent.OnRemoveChunkMeshData) {
        meshMapper[event.chunkEntityId]?.dispose()
        meshMapper.remove(event.chunkEntityId)
        boundMapper.remove(event.chunkEntityId)
        aoMapper.remove(event.chunkEntityId)
    }

}