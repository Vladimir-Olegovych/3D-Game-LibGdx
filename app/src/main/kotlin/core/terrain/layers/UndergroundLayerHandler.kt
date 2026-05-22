package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler

class UndergroundLayerHandler(
    private val undergroundBlockType: BlockType = BlockType.STONE
): BlockLayerHandler() {
    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        surfaceHeightNoise: Int
    ) {
        /*
        if (worldPosition.y < surfaceHeightNoise - 3) {
            chunkData.setBlockByLocal(undergroundBlockType, localPosition)
        }

         */
    }


}