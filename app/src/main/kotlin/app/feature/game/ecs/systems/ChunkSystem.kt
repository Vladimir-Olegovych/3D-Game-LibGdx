package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.ChunkComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
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
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.math.vector.IntVector3
import core.assets.SkinID
import core.chunk.ChunkWorldUpdater
import core.defaults.CameraTypes
import core.defaults.WorldConstants
import core.mesh.MeshUtils

@All(MeshComponent::class)
class ChunkSystem: BaseSystem() {

    @Wire
    private lateinit var chunkWorldUpdater: ChunkWorldUpdater
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire(name = EventBusTypes.MAIN_EVENT_BUS)
    private lateinit var mainEventBus: EventBus
    @Wire(name = EventBusTypes.CHUNK_EVENT_BUS)
    private lateinit var chunkEventBus: EventBus
    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var chunkMapper: ComponentMapper<ChunkComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    private lateinit var chunkMeshTextureData: Texture

    private val playerPosition = Vector3(0f, 0f, 0f)
    private var lastPlayerBlockPosition = IntVector3()
    private var timeSinceLastUpdate = 0f

    override fun initialize() {
        playerPosition.set(camera.position)
        chunkWorldUpdater.start()
        chunkMeshTextureData = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()
        chunkMeshTextureData.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
    }

    override fun processSystem() {
        val playerEntityId = WorldConstants.getLocalPlayerEntityId()
        val playerTransform = transformMapper[playerEntityId]?.transform

        timeSinceLastUpdate += world.delta
        if (timeSinceLastUpdate < 0.3f) return
        timeSinceLastUpdate = 0f

        if (playerTransform == null) {
            playerPosition.set(camera.position)
        } else {
            playerTransform.getTranslation(playerPosition)
        }

        val currentPlayerBlockPosition = IntVector3.roundToInt(playerPosition)
        if (currentPlayerBlockPosition == lastPlayerBlockPosition) return
        lastPlayerBlockPosition = currentPlayerBlockPosition
        chunkEventBus.sendEvent(ChunkEvent.LoadAdditionalChunksRequest(currentPlayerBlockPosition))
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
        chunkMapper.create(entityId)
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
        val boundComponent = boundMapper[entityId]?: boundMapper.create(entityId)
        boundComponent.boundingRadius = radius
    }

    @BusEvent
    fun onMeshDataCreated(event: GameEvent.OnCreateChunkMeshData) {
        val entityId = event.chunkEntityId
        val mesh = event.meshData.mesh ?: return
        val meshComponent = meshMapper[entityId] ?: meshMapper.create(entityId)
        meshComponent.dispose()
        meshComponent.meshData = event.meshData
        meshComponent.meshTextureData = chunkMeshTextureData
        val boundComponent = boundMapper[entityId] ?: boundMapper.create(entityId)
        boundComponent.boundingRadius = MeshUtils.getBoundRadius(mesh)
    }

    @BusEvent
    fun onChunkDataRemoved(event: GameEvent.OnRemoveChunkData) {
        chunkMapper.remove(event.chunkEntityId)
        transformMapper.remove(event.chunkEntityId)
        world.delete(event.chunkEntityId)
    }

    @BusEvent
    fun onMeshDataRemoved(event: GameEvent.OnRemoveChunkMeshData) {
        meshMapper[event.chunkEntityId]?.dispose()
        meshMapper.remove(event.chunkEntityId)
        boundMapper.remove(event.chunkEntityId)
    }

}