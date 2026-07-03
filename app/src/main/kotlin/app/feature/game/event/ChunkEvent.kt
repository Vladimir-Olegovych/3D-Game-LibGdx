package app.feature.game.event

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.chunk.world.WorldGenerationData

sealed class ChunkEvent {
    class LoadAdditionalChunksRequest(val playerPosition: IntVector3): GameEvent()
    class OnSetBlock(val chunkData: ChunkData, val blockType: BlockType, val position: IntVector3)
    class ChunkEntitiesRequest(val generationData: WorldGenerationData): GameEvent()
    class ChunkEntitiesResponse(val generationData: WorldGenerationData, val entities: Map<IntVector3, Int>): GameEvent()
    class OnGenerateResponse(val generationData: WorldGenerationData)
    class OnDrawResponse(val generationData: WorldGenerationData)
    class OnAcceptPendingResponse(val generationData: WorldGenerationData)
    class OnFinalizeResponse(val generationData: WorldGenerationData)
    class GameWorldStarted(val position: Vector3): GameEvent()
}