package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler

class SurfaceLayerHandler(
    private val surfaceBlockType: BlockType = BlockType.GRASS,
    private val underSurfaceBlockType: BlockType = BlockType.DIRT
): BlockLayerHandler() {


    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        surfaceHeightNoise: Int
    ) {
        val block = when {
            worldPosition.y < surfaceHeightNoise - 4 -> BlockType.STONE
            worldPosition.y < surfaceHeightNoise -> BlockType.DIRT
            worldPosition.y == surfaceHeightNoise -> BlockType.GRASS
            else -> BlockType.AIR
        }
        chunkData.setBlockByLocal(block, localPosition)
    }


}