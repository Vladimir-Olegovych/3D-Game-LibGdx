package app.feature.game.ecs.systems

import app.feature.game.ecs.components.ChunkComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.gigapi.eventbus.annotation.BusEvent
import core.assets.SkinID
import core.chunk.ChunkManager
import core.math.createMatrixForChunk
import kotlin.text.get

@All(MeshComponent::class)
class ChunkSystem: BaseSystem() {

    @Wire
    private lateinit var chunkManager: ChunkManager
    @Wire
    private lateinit var assetManager: AssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var chunkMapper: ComponentMapper<ChunkComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    private lateinit var chunkMeshTextureData: Texture

    override fun initialize() {
        chunkManager.generateWorld(world)
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
        meshMapper.create(entityId).apply {
            this@apply.meshData = event.meshData
            this@apply.meshTextureData = chunkMeshTextureData
        }
    }

    override fun processSystem() {}
}