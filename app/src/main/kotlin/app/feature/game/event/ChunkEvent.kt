package app.feature.game.event

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import core.chunk.world.WorldChunksPreparation
import core.chunk.world.WorldGenerationData

sealed class ChunkEvent {
    class LoadAdditionalChunksRequest(val playerPosition: IntVector3): ChunkEvent()
    class ChunksPreparation(val worldGenerationData: WorldGenerationData): ChunkEvent()
    class ChunksGenerating(val entityIds: Map<IntVector3, Int>, val worldChunksPreparation: WorldChunksPreparation): ChunkEvent()
    class ChunksMeshing(val worldChunksPreparation: WorldChunksPreparation): ChunkEvent()
    class FinalizeCreation(val worldChunksPreparation: WorldChunksPreparation): ChunkEvent()


    class OnSetBlock(val owner: Int, val isPlaceContext: Boolean, val chunkPosition: IntVector3, val blockType: BlockType, val blockPosition: IntVector3): ChunkEvent()
    class OnSetBlockFeedBack(
        val owner: Int,
        val isPlaceContext: Boolean,
        val chunkPosition: IntVector3,
        val blockPosition: IntVector3,
        val removedBlockType: BlockType,
        val setBlockType: BlockType,
        val isSuccess: Boolean
    )

    class OnRayCastRequest(
        val requestId: Long,
        val requestOwner: Int,
        val from: Vector3,
        val direction: Vector3,
        val maxDistance: Float = 10f
    ) : PhysicsEvent()

    class OnRayCastResult(
        val requestId: Long,
        val requestOwner: Int,
        val chunkEntityId: Int,
        val blockToSet: BlockType,
        val blockToRemove: BlockType,
        val chunkPosition: IntVector3,
        val blockPosition: IntVector3
    ): PhysicsEvent()
}